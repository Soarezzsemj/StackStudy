package com.stackstudy.subject;

import java.math.BigDecimal;

/**
 * Regra de nota gravada na matéria. Todo campo {@code null} herda a preferência do usuário (ver
 * {@code grade.GradeSettings}); {@code totalClasses} null = usa a carga horária.
 *
 * @param method {@code WEIGHTED} ou {@code ARITHMETIC}
 * @param finalExamScore nota tirada no exame final, quando já aconteceu
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
    BigDecimal finalExamScore) {}
