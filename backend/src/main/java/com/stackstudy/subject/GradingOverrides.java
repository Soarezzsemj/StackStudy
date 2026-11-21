package com.stackstudy.subject;

import java.math.BigDecimal;

/**
 * Regra de nota gravada na matéria. Todo campo {@code null} herda a preferência do usuário (ver
 * {@code grade.GradeSettings}); {@code totalClasses} null = usa a carga horária.
 *
 * @param method {@code WEIGHTED}, {@code ARITHMETIC} ou {@code SUM} (ver {@code grade.GradingRule})
 * @param finalExamScore nota tirada no exame final, quando já aconteceu
 * @param roundBeforeCompare soma de pontos: arredonda o total antes de comparar com a mínima
 * @param roundingScale soma de pontos: casas desse arredondamento (0 a 2)
 * @param pointsCap soma de pontos: teto do total (null = sem teto; não herda)
 */
public record GradingOverrides(
    String method,
    BigDecimal scaleMax,
    BigDecimal passingGrade,
    BigDecimal maxAbsencePct,
    Integer totalClasses,
    Boolean finalExamEnabled,
    BigDecimal finalExamMinAverage,
    BigDecimal finalExamAverageWeight,
    BigDecimal finalExamPassingGrade,
    BigDecimal finalExamScore,
    Boolean roundBeforeCompare,
    Integer roundingScale,
    BigDecimal pointsCap) {

  /** Só a regra de cálculo; o resto herda. */
  public static GradingOverrides ofMethod(String method) {
    return new GradingOverrides(
        method, null, null, null, null, null, null, null, null, null, null, null, null);
  }
}
