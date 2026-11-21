package com.stackstudy.grade.dto;

import com.stackstudy.grade.Absence;
import com.stackstudy.grade.Assessment;
import com.stackstudy.grade.GradeCalculator;
import com.stackstudy.grade.GradeCalculator.AttendanceStatus;
import com.stackstudy.grade.GradeCalculator.GpaScale;
import com.stackstudy.grade.GradeCalculator.Standing;
import com.stackstudy.grade.GradeSettings;
import com.stackstudy.grade.GradeSettings.GpaWeight;
import com.stackstudy.grade.GradingRule;
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
      GradingRule method,
      BigDecimal scaleMax,
      BigDecimal passingGrade,
      BigDecimal maxAbsencePct,
      boolean finalExamEnabled,
      BigDecimal finalExamMinAverage,
      BigDecimal finalExamAverageWeight,
      BigDecimal finalExamPassingGrade,
      GpaScale gpaScale,
      GpaWeight gpaWeight,
      boolean roundBeforeCompare,
      int roundingScale) {

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
          s.getGpaWeight(),
          s.isRoundBeforeCompare(),
          s.getRoundingScale());
    }
  }

  /**
   * Regra que vale para a matéria (própria ou herdada das preferências).
   *
   * @param attendanceBase total de aulas usado nas faltas (null = sem carga horária)
   * @param pointsCap teto do total na soma de pontos (null = sem teto)
   */
  public record EffectiveRule(
      GradingRule method,
      BigDecimal scaleMax,
      BigDecimal passingGrade,
      BigDecimal maxAbsencePct,
      Integer attendanceBase,
      boolean finalExamEnabled,
      BigDecimal finalExamMinAverage,
      BigDecimal finalExamAverageWeight,
      BigDecimal finalExamPassingGrade,
      boolean roundBeforeCompare,
      int roundingScale,
      BigDecimal pointsCap) {}

  /** Valores gravados na matéria (null = herda). Usado pelo formulário de regra. */
  public record RuleOverrides(
      GradingRule method,
      BigDecimal scaleMax,
      BigDecimal passingGrade,
      BigDecimal maxAbsencePct,
      Integer totalClasses,
      Boolean finalExamEnabled,
      BigDecimal finalExamMinAverage,
      BigDecimal finalExamAverageWeight,
      BigDecimal finalExamPassingGrade,
      Boolean roundBeforeCompare,
      Integer roundingScale,
      BigDecimal pointsCap) {

    public static RuleOverrides from(GradingOverrides o) {
      return new RuleOverrides(
          o.method() == null ? null : GradingRule.valueOf(o.method()),
          o.scaleMax(),
          o.passingGrade(),
          o.maxAbsencePct(),
          o.totalClasses(),
          o.finalExamEnabled(),
          o.finalExamMinAverage(),
          o.finalExamAverageWeight(),
          o.finalExamPassingGrade(),
          o.roundBeforeCompare(),
          o.roundingScale(),
          o.pointsCap());
    }
  }

  /**
   * @param maxPoints quanto vale (soma de pontos); null se nunca foi informado
   * @param bonus ponto extra (soma de pontos)
   */
  public record AssessmentView(
      UUID id,
      String name,
      BigDecimal weight,
      BigDecimal score,
      BigDecimal maxPoints,
      boolean bonus) {

    public static AssessmentView from(Assessment a) {
      return new AssessmentView(
          a.getId(), a.getName(), a.getWeight(), a.getScore(), a.getMaxPoints(), a.isBonus());
    }
  }

  /** Detalhe da soma de pontos (ver {@link GradeCalculator.SumDetail}). */
  public record SumView(
      BigDecimal earned,
      BigDecimal total,
      BigDecimal comparedTotal,
      BigDecimal pointsToPass,
      BigDecimal pointsAvailable,
      BigDecimal maxReachable,
      BigDecimal plannedPoints,
      BigDecimal bonusPoints,
      boolean scaleMismatch,
      int missingMaxPoints,
      List<BigDecimal> requiredPerItem) {

    static SumView from(GradeCalculator.SumDetail d) {
      return d == null
          ? null
          : new SumView(
              d.earned(),
              d.total(),
              d.comparedTotal(),
              d.pointsToPass(),
              d.pointsAvailable(),
              d.maxReachable(),
              d.plannedPoints(),
              d.bonusPoints(),
              d.scaleMismatch(),
              d.missingMaxPoints(),
              d.requiredPerItem());
    }
  }

  /** Resultado das notas (ver {@link GradeCalculator.Result}). {@code sum} só na soma. */
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
      boolean complete,
      SumView sum) {

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
          r.complete(),
          SumView.from(r.sum()));
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
