package com.stackstudy.grade;

import com.stackstudy.common.persistence.BaseEntity;
import com.stackstudy.grade.GradeCalculator.GpaScale;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Preferências de nota do usuário: padrão das matérias sem regra própria e configuração do CR. Sem
 * linha no banco, valem os padrões abaixo (iguais aos DEFAULT da V4 e da V6).
 */
@Entity
@Table(name = "grade_settings")
public class GradeSettings extends BaseEntity {

  /** Base do peso no CR. */
  public enum GpaWeight {
    CREDITS,
    WORKLOAD
  }

  @Column(name = "user_id", nullable = false, updatable = false, unique = true)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "grade_method", nullable = false, length = 10)
  private GradingRule method = GradingRule.WEIGHTED;

  @Column(name = "grade_scale_max", nullable = false, precision = 6, scale = 2)
  private BigDecimal scaleMax = new BigDecimal("10.00");

  @Column(name = "passing_grade", nullable = false, precision = 6, scale = 2)
  private BigDecimal passingGrade = new BigDecimal("6.00");

  @Column(name = "max_absence_pct", nullable = false, precision = 5, scale = 2)
  private BigDecimal maxAbsencePct = new BigDecimal("25.00");

  @Column(name = "final_exam_enabled", nullable = false)
  private boolean finalExamEnabled;

  @Column(name = "final_exam_min_average", nullable = false, precision = 6, scale = 2)
  private BigDecimal finalExamMinAverage = new BigDecimal("4.00");

  @Column(name = "final_exam_average_weight", nullable = false, precision = 5, scale = 2)
  private BigDecimal finalExamAverageWeight = new BigDecimal("50.00");

  @Column(name = "final_exam_passing_grade", nullable = false, precision = 6, scale = 2)
  private BigDecimal finalExamPassingGrade = new BigDecimal("5.00");

  @Enumerated(EnumType.STRING)
  @Column(name = "gpa_scale", nullable = false, length = 4)
  private GpaScale gpaScale = GpaScale.TEN;

  @Enumerated(EnumType.STRING)
  @Column(name = "gpa_weight", nullable = false, length = 10)
  private GpaWeight gpaWeight = GpaWeight.CREDITS;

  /** Soma de pontos: arredondar o total antes de comparar com a mínima (padrão: não). */
  @Column(name = "round_before_compare", nullable = false)
  private boolean roundBeforeCompare;

  /** Casas desse arredondamento (0 a 2). */
  @Column(name = "rounding_scale", nullable = false)
  private int roundingScale = 1;

  protected GradeSettings() {}

  public GradeSettings(UUID userId) {
    this.userId = userId;
  }

  public void update(
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
    this.method = method;
    this.scaleMax = scaleMax;
    this.passingGrade = passingGrade;
    this.maxAbsencePct = maxAbsencePct;
    this.finalExamEnabled = finalExamEnabled;
    this.finalExamMinAverage = finalExamMinAverage;
    this.finalExamAverageWeight = finalExamAverageWeight;
    this.finalExamPassingGrade = finalExamPassingGrade;
    this.gpaScale = gpaScale;
    this.gpaWeight = gpaWeight;
    this.roundBeforeCompare = roundBeforeCompare;
    this.roundingScale = roundingScale;
  }

  public UUID getUserId() {
    return userId;
  }

  public GradingRule getMethod() {
    return method;
  }

  public BigDecimal getScaleMax() {
    return scaleMax;
  }

  public BigDecimal getPassingGrade() {
    return passingGrade;
  }

  public BigDecimal getMaxAbsencePct() {
    return maxAbsencePct;
  }

  public boolean isFinalExamEnabled() {
    return finalExamEnabled;
  }

  public BigDecimal getFinalExamMinAverage() {
    return finalExamMinAverage;
  }

  public BigDecimal getFinalExamAverageWeight() {
    return finalExamAverageWeight;
  }

  public BigDecimal getFinalExamPassingGrade() {
    return finalExamPassingGrade;
  }

  public GpaScale getGpaScale() {
    return gpaScale;
  }

  public GpaWeight getGpaWeight() {
    return gpaWeight;
  }

  public boolean isRoundBeforeCompare() {
    return roundBeforeCompare;
  }

  public int getRoundingScale() {
    return roundingScale;
  }
}
