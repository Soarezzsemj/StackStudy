package com.stackstudy.grade.dto;

import com.stackstudy.grade.GradeCalculator.GpaScale;
import com.stackstudy.grade.GradeSettings.GpaWeight;
import com.stackstudy.grade.GradingRule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Corpos de requisição do Grade Predictor. Números com no máximo 2 casas decimais. Regras entre
 * campos (mínima ≤ escala, nota ≤ escala, nota ≤ quanto vale...) são checadas no service.
 */
public final class GradeRequests {

  private GradeRequests() {}

  /**
   * Preferências do usuário: padrão das matérias e configuração do CR.
   *
   * @param roundBeforeCompare soma de pontos: arredonda o total antes de comparar (null = não)
   * @param roundingScale casas desse arredondamento, 0 a 2 (null = 1)
   */
  public record Settings(
      @NotNull GradingRule method,
      @NotNull @DecimalMin("1") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
          BigDecimal scaleMax,
      @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
          BigDecimal passingGrade,
      @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
          BigDecimal maxAbsencePct,
      @NotNull Boolean finalExamEnabled,
      @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
          BigDecimal finalExamMinAverage,
      @NotNull
          @DecimalMin("0")
          @DecimalMax(value = "100", inclusive = false)
          @Digits(integer = 2, fraction = 2)
          BigDecimal finalExamAverageWeight,
      @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
          BigDecimal finalExamPassingGrade,
      @NotNull GpaScale gpaScale,
      @NotNull GpaWeight gpaWeight,
      Boolean roundBeforeCompare,
      @Min(0) @Max(2) Integer roundingScale) {}

  /**
   * Regra própria da matéria. Todo campo null herda a preferência do usuário.
   *
   * @param totalClasses total de aulas para as faltas (null = usa a carga horária)
   * @param finalExamScore nota tirada no exame final, quando já aconteceu
   * @param pointsCap soma de pontos: teto do total da matéria (null = sem teto)
   */
  public record SubjectRule(
      GradingRule method,
      @DecimalMin("1") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal scaleMax,
      @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
          BigDecimal passingGrade,
      @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
          BigDecimal maxAbsencePct,
      @Min(1) @Max(5000) Integer totalClasses,
      Boolean finalExamEnabled,
      @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
          BigDecimal finalExamMinAverage,
      @DecimalMin("0")
          @DecimalMax(value = "100", inclusive = false)
          @Digits(integer = 2, fraction = 2)
          BigDecimal finalExamAverageWeight,
      @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
          BigDecimal finalExamPassingGrade,
      @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
          BigDecimal finalExamScore,
      Boolean roundBeforeCompare,
      @Min(0) @Max(2) Integer roundingScale,
      @DecimalMin("0.01") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
          BigDecimal pointsCap) {}

  /**
   * Uma avaliação da lista. Com {@code id}, atualiza a existente; sem, cria.
   *
   * @param weight relativo (não precisa somar 100). Obrigatório na média ponderada; nas outras
   *     regras, null mantém o peso que já estava (ou 1).
   * @param score null = ainda não lançada
   * @param maxPoints quanto a avaliação vale; obrigatório na soma de pontos (nota ≤ ele)
   * @param bonus ponto extra na soma de pontos (null = não)
   */
  public record AssessmentItem(
      UUID id,
      @NotBlank @Size(max = 80) String name,
      @DecimalMin("0.01") @DecimalMax("1000") @Digits(integer = 4, fraction = 2) BigDecimal weight,
      @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal score,
      @DecimalMin("0.01") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
          BigDecimal maxPoints,
      Boolean bonus) {}

  /** Substitui a lista de avaliações da matéria (as que não vierem são apagadas). */
  public record Assessments(@NotNull @Size(max = 30) List<@Valid @NotNull AssessmentItem> items) {}

  /** Falta num dia: quantas aulas foram perdidas. */
  public record AbsenceInput(@NotNull LocalDate date, @NotNull @Min(1) @Max(24) Integer count) {}
}
