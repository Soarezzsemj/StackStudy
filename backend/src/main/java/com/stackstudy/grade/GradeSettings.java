package com.stackstudy.grade;

import com.stackstudy.common.persistence.BaseEntity;
import com.stackstudy.grade.GradeCalculator.GpaScale;
import com.stackstudy.grade.GradeCalculator.Method;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Preferências de nota do usuário: padrão das matérias sem regra própria e configuração do CR. Sem
 * linha no banco, valem os padrões abaixo (iguais aos DEFAULT da V4).
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
  private Method method = Method.WEIGHTED;

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

  protected GradeSettings() {}

  public GradeSettings(UUID userId) {
    this.userId = userId;
  }

  public void update(
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
  }

  public UUID getUserId() {
    return userId;
  }

  public Method getMethod() {
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
}
