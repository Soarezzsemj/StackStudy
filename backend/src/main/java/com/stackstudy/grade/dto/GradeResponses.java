package com.stackstudy.grade.dto;

import com.stackstudy.grade.Absence;
import com.stackstudy.grade.Assessment;
import com.stackstudy.grade.GradeCalculator;
import com.stackstudy.grade.GradeCalculator.AttendanceStatus;
import com.stackstudy.grade.GradeCalculator.GpaScale;
import com.stackstudy.grade.GradeCalculator.Method;
import com.stackstudy.grade.GradeCalculator.Standing;
import com.stackstudy.grade.GradeSettings;
import com.stackstudy.grade.GradeSettings.GpaWeight;
import com.stackstudy.subject.GradingOverrides;
import com.stackstudy.subject.SubjectStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Respostas do Grade Predictor (records pequenos agrupados). */
public final class GradeResponses {

  private GradeResponses() {}

  public record Settings(
      Method method,
      BigDecimal scaleMax,
      BigDecimal passingGrade,
      BigDecimal maxAbsencePct,
      boolean finalExamEnabled,
      BigDecimal finalExamMinAverage,
      BigDecimal finalExamAverageWeight,
      BigDecimal finalExamPassingGrade,
      GpaScale gpaScale,
      GpaWeight gpaWeight) {

    public static Settings from(GradeSettings s) {
      return new Settings(
          s.getMethod(),
          s.getScaleMax(),
          s.getPassingGrade(),
          s.getMaxAbsencePct(),
          s.isFinalExamEnabled(),
          s.getFinalExamMinAverage(),
          s.getFinalExamAverageWeight(),
          s.getFinalExamPassingGrade(),
          s.getGpaScale(),
          s.getGpaWeight());
    }
  }

  /**
   * Regra que vale para a matéria (própria ou herdada das preferências).
   *
   * @param attendanceBase total de aulas usado nas faltas (null = sem carga horária)
   */
  public record EffectiveRule(
      Method method,
      BigDecimal scaleMax,
      BigDecimal passingGrade,
      BigDecimal maxAbsencePct,
      Integer attendanceBase,
      boolean finalExamEnabled,
      BigDecimal finalExamMinAverage,
      BigDecimal finalExamAverageWeight,
      BigDecimal finalExamPassingGrade) {}

  /** Valores gravados na matéria (null = herda). Usado pelo formulário de regra. */
  public record RuleOverrides(
      Method method,
      BigDecimal scaleMax,
      BigDecimal passingGrade,
      BigDecimal maxAbsencePct,
      Integer totalClasses,
      Boolean finalExamEnabled,
      BigDecimal finalExamMinAverage,
      BigDecimal finalExamAverageWeight,
      BigDecimal finalExamPassingGrade) {

    public static RuleOverrides from(GradingOverrides o) {
      return new RuleOverrides(
          o.method() == null ? null : Method.valueOf(o.method()),
          o.scaleMax(),
          o.passingGrade(),
          o.maxAbsencePct(),
          o.totalClasses(),
          o.finalExamEnabled(),
          o.finalExamMinAverage(),
          o.finalExamAverageWeight(),
          o.finalExamPassingGrade());
    }
  }

  public record AssessmentView(UUID id, String name, BigDecimal weight, BigDecimal score) {

    public static AssessmentView from(Assessment a) {
      return new AssessmentView(a.getId(), a.getName(), a.getWeight(), a.getScore());
    }
  }

  /** Resultado das notas (ver {@link GradeCalculator.Result}). */
  public record GradeResult(
      Standing standing,
      int totalCount,
      int gradedCount,
      BigDecimal totalWeight,
      BigDecimal gradedWeightPct,
      BigDecimal currentAverage,
      BigDecimal minimumAverage,
      BigDecimal maximumAverage,
      BigDecimal requiredScore,
      BigDecimal examRequired,
      BigDecimal finalGrade,
      boolean complete) {

    public static GradeResult from(GradeCalculator.Result r) {
      return new GradeResult(
          r.standing(),
          r.totalCount(),
          r.gradedCount(),
          r.totalWeight(),
          r.gradedWeightPct(),
          r.currentAverage(),
          r.minimumAverage(),
          r.maximumAverage(),
          r.requiredScore(),
          r.examRequired(),
          r.finalGrade(),
          r.complete());
    }
  }

  public record AttendanceView(
      Integer base,
      int used,
      Integer allowed,
      Integer remaining,
      BigDecimal attendancePct,
      AttendanceStatus status) {

    public static AttendanceView from(GradeCalculator.Attendance a) {
      return new AttendanceView(
          a.base(), a.used(), a.allowed(), a.remaining(), a.attendancePct(), a.status());
    }
  }

  /** Tudo o que a tela precisa de uma matéria, inclusive as avaliações para o simulador. */
  public record SubjectGrades(
      UUID subjectId,
      UUID semesterId,
      String name,
      String code,
      String color,
      int credits,
      Integer workloadHours,
      SubjectStatus status,
      EffectiveRule rule,
      RuleOverrides overrides,
      BigDecimal finalExamScore,
      List<AssessmentView> assessments,
      GradeResult result,
      AttendanceView attendance) {}

  /**
   * CR de um semestre.
   *
   * @param value null se nenhuma matéria entrou
   * @param subjects matérias que entraram no cálculo
   * @param provisional quantas delas ainda estão com média parcial
   * @param excludedNoWeight matérias com nota mas sem créditos/horas (não entram)
   */
  public record SemesterGpa(
      UUID semesterId,
      String name,
      LocalDate startDate,
      boolean current,
      BigDecimal value,
      int subjects,
      long totalWeight,
      int provisional,
      int excludedNoWeight) {}

  public record Gpa(
      GpaScale scale,
      GpaWeight weightBasis,
      BigDecimal cumulative,
      int cumulativeSubjects,
      int cumulativeProvisional,
      List<SemesterGpa> semesters) {}

  /**
   * Tela inteira numa chamada.
   *
   * @param semesterId semestre mostrado (o pedido ou o atual; null se não houver)
   */
  public record Overview(
      Settings settings, UUID semesterId, List<SubjectGrades> subjects, Gpa gpa) {}

  public record AbsenceView(UUID id, UUID subjectId, LocalDate date, int count) {

    public static AbsenceView from(Absence a) {
      return new AbsenceView(a.getId(), a.getSubjectId(), a.getDate(), a.getCount());
    }
  }
}
