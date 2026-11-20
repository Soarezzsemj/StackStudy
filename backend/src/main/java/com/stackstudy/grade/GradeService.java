package com.stackstudy.grade;

import com.stackstudy.common.error.ApiError;
import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.error.ErrorCode;
import com.stackstudy.common.security.CurrentUser;
import com.stackstudy.grade.GradeCalculator.FinalExamRule;
import com.stackstudy.grade.GradeCalculator.GpaItem;
import com.stackstudy.grade.GradeCalculator.Method;
import com.stackstudy.grade.GradeSettings.GpaWeight;
import com.stackstudy.grade.dto.GradeRequests;
import com.stackstudy.grade.dto.GradeResponses.AssessmentView;
import com.stackstudy.grade.dto.GradeResponses.AttendanceView;
import com.stackstudy.grade.dto.GradeResponses.EffectiveRule;
import com.stackstudy.grade.dto.GradeResponses.Gpa;
import com.stackstudy.grade.dto.GradeResponses.GradeResult;
import com.stackstudy.grade.dto.GradeResponses.Overview;
import com.stackstudy.grade.dto.GradeResponses.RuleOverrides;
import com.stackstudy.grade.dto.GradeResponses.SemesterGpa;
import com.stackstudy.grade.dto.GradeResponses.Settings;
import com.stackstudy.grade.dto.GradeResponses.SubjectGrades;
import com.stackstudy.subject.GradingOverrides;
import com.stackstudy.subject.Semester;
import com.stackstudy.subject.SemesterRepository;
import com.stackstudy.subject.Subject;
import com.stackstudy.subject.SubjectRepository;
import com.stackstudy.subject.SubjectStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notas, regra por matéria, visão geral e CR. A conta em si fica no {@link GradeCalculator}; aqui
 * só se carrega o que é do usuário logado (consultas agrupadas, sem N+1) e se aplica a regra
 * efetiva (própria da matéria ou herdada das preferências).
 */
@Service
public class GradeService {

  private final SubjectRepository subjects;
  private final SemesterRepository semesters;
  private final AssessmentRepository assessments;
  private final AbsenceRepository absences;
  private final GradeSettingsService settingsService;
  private final CurrentUser currentUser;

  public GradeService(
      SubjectRepository subjects,
      SemesterRepository semesters,
      AssessmentRepository assessments,
      AbsenceRepository absences,
      GradeSettingsService settingsService,
      CurrentUser currentUser) {
    this.subjects = subjects;
    this.semesters = semesters;
    this.assessments = assessments;
    this.absences = absences;
    this.settingsService = settingsService;
    this.currentUser = currentUser;
  }

  /**
   * Tela inteira: matérias do semestre (o pedido ou o atual) e o CR por semestre e acumulado. São 5
   * consultas, qualquer que seja o número de matérias.
   */
  @Transactional(readOnly = true)
  public Overview overview(UUID semesterId) {
    UUID userId = currentUser.id();
    GradeSettings settings = settingsService.forUser(userId);
    List<Semester> owned = semesters.findAllByUserIdOrderByStartDateDesc(userId);
    UUID selected;
    if (semesterId != null) {
      selected =
          owned.stream()
              .filter(s -> s.getId().equals(semesterId))
              .findFirst()
              .orElseThrow(() -> ApiException.notFound("Semester"))
              .getId();
    } else {
      selected =
          owned.stream().filter(Semester::isCurrent).map(Semester::getId).findFirst().orElse(null);
    }

    Map<UUID, List<Assessment>> bySubject =
        assessments.findAllOwned(userId).stream()
            .collect(Collectors.groupingBy(Assessment::getSubjectId));
    Map<UUID, Long> absenceTotals =
        absences.totalsOwned(userId).stream()
            .collect(
                Collectors.toMap(
                    AbsenceRepository.SubjectAbsenceTotal::getSubjectId,
                    AbsenceRepository.SubjectAbsenceTotal::getTotal));

    List<SubjectGrades> all =
        subjects.findAllOwned(userId).stream()
            .map(
                s ->
                    build(
                        s,
                        settings,
                        bySubject.getOrDefault(s.getId(), List.of()),
                        absenceTotals.getOrDefault(s.getId(), 0L)))
            .toList();
    List<SubjectGrades> shown =
        selected == null
            ? List.of()
            : all.stream().filter(g -> selected.equals(g.semesterId())).toList();
    return new Overview(Settings.from(settings), selected, shown, gpa(settings, owned, all));
  }

  @Transactional(readOnly = true)
  public SubjectGrades get(UUID subjectId) {
    return load(findOwned(subjectId));
  }

  /** Grava a regra própria da matéria (campos null herdam das preferências). */
  @Transactional
  public SubjectGrades updateRule(UUID subjectId, GradeRequests.SubjectRule r) {
    Subject subject = findOwned(subjectId);
    GradingOverrides overrides =
        new GradingOverrides(
            r.method() == null ? null : r.method().name(),
            r.scaleMax(),
            r.passingGrade(),
            r.maxAbsencePct(),
            r.totalClasses(),
            r.finalExamEnabled(),
            r.finalExamMinAverage(),
            r.finalExamAverageWeight(),
            r.finalExamPassingGrade(),
            r.finalExamScore());
    EffectiveRule rule = resolve(subject, overrides, settingsService.forUser(currentUser.id()));
    validateRule(rule, r.finalExamScore(), assessments.maxScore(subjectId));
    subject.updateGrading(overrides);
    return load(subject);
  }

  /**
   * Substitui a lista de avaliações: itens com {@code id} são atualizados, sem {@code id} são
   * criados e os que não vieram são apagados. A ordem da lista vira a ordem na tela.
   */
  @Transactional
  public SubjectGrades replaceAssessments(UUID subjectId, GradeRequests.Assessments request) {
    Subject subject = findOwned(subjectId);
    EffectiveRule rule =
        resolve(subject, subject.getGrading(), settingsService.forUser(currentUser.id()));
    Map<UUID, Assessment> existing =
        assessments.findAllBySubjectIdOrderByPositionAscCreatedAtAsc(subjectId).stream()
            .collect(Collectors.toMap(Assessment::getId, Function.identity()));

    List<GradeRequests.AssessmentItem> items = request.items();
    List<ApiError.FieldProblem> problems = new ArrayList<>();
    Set<UUID> kept = new HashSet<>();
    for (int i = 0; i < items.size(); i++) {
      GradeRequests.AssessmentItem item = items.get(i);
      if (item.id() != null) {
        // Id de outra matéria (ou de outro usuário) parece não existir.
        if (!existing.containsKey(item.id())) {
          throw ApiException.notFound("Assessment");
        }
        if (!kept.add(item.id())) {
          problems.add(new ApiError.FieldProblem("items[" + i + "].id", "must be unique"));
        }
      }
      if (item.score() != null && item.score().compareTo(rule.scaleMax()) > 0) {
        problems.add(
            new ApiError.FieldProblem(
                "items[" + i + "].score", "must not exceed the scale (" + rule.scaleMax() + ")"));
      }
    }
    if (!problems.isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "Request validation failed", problems);
    }

    List<Assessment> removed =
        existing.values().stream().filter(a -> !kept.contains(a.getId())).toList();
    assessments.deleteAll(removed);
    List<Assessment> saved = new ArrayList<>();
    for (int i = 0; i < items.size(); i++) {
      GradeRequests.AssessmentItem item = items.get(i);
      Assessment entity =
          item.id() != null ? existing.get(item.id()) : new Assessment(subject.getId());
      entity.update(item.name().trim(), item.weight(), item.score(), i);
      saved.add(entity);
    }
    assessments.saveAll(saved);
    return build(
        subject, settingsService.forUser(currentUser.id()), saved, absenceTotal(subjectId));
  }

  /** Busca uma matéria do usuário logado. Se for de outro usuário, dá 404. */
  Subject findOwned(UUID subjectId) {
    return subjects
        .findOwned(subjectId, currentUser.id())
        .orElseThrow(() -> ApiException.notFound("Subject"));
  }

  private SubjectGrades load(Subject subject) {
    return build(
        subject,
        settingsService.forUser(currentUser.id()),
        assessments.findAllBySubjectIdOrderByPositionAscCreatedAtAsc(subject.getId()),
        absenceTotal(subject.getId()));
  }

  private long absenceTotal(UUID subjectId) {
    return absences.findAllBySubjectIdOrderByDateDesc(subjectId).stream()
        .mapToLong(Absence::getCount)
        .sum();
  }

  // -------------------------------------------------------------------------
  // regra efetiva e cálculo
  // -------------------------------------------------------------------------

  /** Campo da matéria quando houver; senão, a preferência do usuário. */
  static EffectiveRule resolve(Subject subject, GradingOverrides o, GradeSettings d) {
    Integer base;
    if (o.totalClasses() != null && o.totalClasses() > 0) {
      base = o.totalClasses();
    } else if (subject.getWorkloadHours() != null && subject.getWorkloadHours() > 0) {
      base = subject.getWorkloadHours();
    } else {
      base = null;
    }
    return new EffectiveRule(
        o.method() != null ? Method.valueOf(o.method()) : d.getMethod(),
        or(o.scaleMax(), d.getScaleMax()),
        or(o.passingGrade(), d.getPassingGrade()),
        or(o.maxAbsencePct(), d.getMaxAbsencePct()),
        base,
        o.finalExamEnabled() != null ? o.finalExamEnabled() : d.isFinalExamEnabled(),
        or(o.finalExamMinAverage(), d.getFinalExamMinAverage()),
        or(o.finalExamAverageWeight(), d.getFinalExamAverageWeight()),
        or(o.finalExamPassingGrade(), d.getFinalExamPassingGrade()));
  }

  static GradeCalculator.Rule toCalculatorRule(EffectiveRule r) {
    FinalExamRule exam =
        r.finalExamEnabled()
            ? new FinalExamRule(
                true,
                r.finalExamMinAverage(),
                r.finalExamAverageWeight(),
                r.finalExamPassingGrade())
            : FinalExamRule.disabled();
    return new GradeCalculator.Rule(r.method(), r.scaleMax(), r.passingGrade(), exam);
  }

  private static SubjectGrades build(
      Subject subject, GradeSettings settings, List<Assessment> list, long absenceCount) {
    GradingOverrides overrides = subject.getGrading();
    EffectiveRule rule = resolve(subject, overrides, settings);
    List<GradeCalculator.Assessment> input =
        list.stream()
            .map(a -> new GradeCalculator.Assessment(a.getWeight(), a.getScore()))
            .toList();
    GradeCalculator.Result result =
        GradeCalculator.evaluate(toCalculatorRule(rule), input, overrides.finalExamScore());
    GradeCalculator.Attendance attendance =
        GradeCalculator.attendance(
            rule.attendanceBase(), Math.toIntExact(absenceCount), rule.maxAbsencePct());
    return new SubjectGrades(
        subject.getId(),
        subject.getSemesterId(),
        subject.getName(),
        subject.getCode(),
        subject.getColor(),
        subject.getCredits(),
        subject.getWorkloadHours(),
        subject.getStatus(),
        rule,
        RuleOverrides.from(overrides),
        overrides.finalExamScore(),
        list.stream().map(AssessmentView::from).toList(),
        GradeResult.from(result),
        AttendanceView.from(attendance));
  }

  /** As mesmas regras entre campos das preferências, agora sobre a regra efetiva da matéria. */
  private static void validateRule(EffectiveRule r, BigDecimal examScore, BigDecimal maxScore) {
    List<ApiError.FieldProblem> problems = new ArrayList<>();
    BigDecimal scale = r.scaleMax();
    if (r.passingGrade().compareTo(scale) > 0) {
      problems.add(new ApiError.FieldProblem("passingGrade", "must not exceed scaleMax"));
    }
    if (r.finalExamEnabled()) {
      if (r.finalExamPassingGrade().compareTo(scale) > 0) {
        problems.add(
            new ApiError.FieldProblem("finalExamPassingGrade", "must not exceed scaleMax"));
      }
      if (r.finalExamMinAverage().compareTo(r.passingGrade()) > 0) {
        problems.add(
            new ApiError.FieldProblem("finalExamMinAverage", "must not exceed passingGrade"));
      }
    }
    if (examScore != null && examScore.compareTo(scale) > 0) {
      problems.add(new ApiError.FieldProblem("finalExamScore", "must not exceed scaleMax"));
    }
    if (maxScore != null && maxScore.compareTo(scale) > 0) {
      problems.add(
          new ApiError.FieldProblem(
              "scaleMax", "must be at least the highest score already entered (" + maxScore + ")"));
    }
    if (!problems.isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "Request validation failed", problems);
    }
  }

  // -------------------------------------------------------------------------
  // CR
  // -------------------------------------------------------------------------

  /**
   * CR por semestre (do mais antigo ao mais novo) e acumulado. Entram matérias não trancadas com
   * nota (final ou parcial) e peso &gt; 0. O acumulado pondera todas as matérias juntas (não é a
   * média dos semestres).
   */
  private static Gpa gpa(GradeSettings settings, List<Semester> owned, List<SubjectGrades> all) {
    Map<UUID, List<SubjectGrades>> bySemester =
        all.stream().collect(Collectors.groupingBy(SubjectGrades::semesterId));
    List<GpaItem> cumulative = new ArrayList<>();
    int cumulativeProvisional = 0;
    List<SemesterGpa> perSemester = new ArrayList<>();
    List<Semester> ordered =
        owned.stream()
            .sorted(Comparator.comparing(Semester::getStartDate).thenComparing(Semester::getName))
            .toList();
    for (Semester semester : ordered) {
      List<GpaItem> items = new ArrayList<>();
      int provisional = 0;
      int noWeight = 0;
      long totalWeight = 0;
      for (SubjectGrades g : bySemester.getOrDefault(semester.getId(), List.of())) {
        if (g.status() == SubjectStatus.DROPPED || g.result().finalGrade() == null) {
          continue;
        }
        int weight = weightOf(settings.getGpaWeight(), g);
        if (weight <= 0) {
          noWeight++;
          continue;
        }
        items.add(new GpaItem(g.result().finalGrade(), g.rule().scaleMax(), weight));
        totalWeight += weight;
        if (!g.result().complete()) {
          provisional++;
        }
      }
      cumulative.addAll(items);
      cumulativeProvisional += provisional;
      perSemester.add(
          new SemesterGpa(
              semester.getId(),
              semester.getName(),
              semester.getStartDate(),
              semester.isCurrent(),
              GradeCalculator.gpa(items, settings.getGpaScale()),
              items.size(),
              totalWeight,
              provisional,
              noWeight));
    }
    return new Gpa(
        settings.getGpaScale(),
        settings.getGpaWeight(),
        GradeCalculator.gpa(cumulative, settings.getGpaScale()),
        cumulative.size(),
        cumulativeProvisional,
        perSemester);
  }

  private static int weightOf(GpaWeight basis, SubjectGrades g) {
    if (basis == GpaWeight.WORKLOAD) {
      return g.workloadHours() == null ? 0 : g.workloadHours();
    }
    return g.credits();
  }

  private static BigDecimal or(BigDecimal value, BigDecimal fallback) {
    return value != null ? value : fallback;
  }
}
