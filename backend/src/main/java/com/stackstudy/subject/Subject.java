package com.stackstudy.subject;

import com.stackstudy.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Matéria. O dono é resolvido pelo semestre ({@code subject → semester → user}). Os campos de regra
 * de nota (Grade Predictor) são opcionais: {@code null} = usa a preferência do usuário.
 */
@Entity
@Table(name = "subjects")
public class Subject extends BaseEntity {

  @Column(name = "semester_id", nullable = false)
  private UUID semesterId;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(length = 30)
  private String code;

  @Column(length = 120)
  private String professor;

  @Column(nullable = false)
  private int credits;

  @Column(name = "workload_hours")
  private Integer workloadHours;

  @Column(nullable = false, length = 7)
  private String color;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 12)
  private SubjectStatus status = SubjectStatus.IN_PROGRESS;

  /** Total de aulas para as faltas; 0 = usa a carga horária. */
  @Column(name = "total_classes", nullable = false)
  private int totalClasses;

  @Column(name = "grade_method", length = 10)
  private String gradeMethod;

  @Column(name = "grade_scale_max", precision = 6, scale = 2)
  private BigDecimal gradeScaleMax;

  @Column(name = "passing_grade", precision = 6, scale = 2)
  private BigDecimal passingGrade;

  @Column(name = "max_absence_pct", precision = 5, scale = 2)
  private BigDecimal maxAbsencePct;

  @Column(name = "final_exam_enabled")
  private Boolean finalExamEnabled;

  @Column(name = "final_exam_min_average", precision = 6, scale = 2)
  private BigDecimal finalExamMinAverage;

  @Column(name = "final_exam_average_weight", precision = 5, scale = 2)
  private BigDecimal finalExamAverageWeight;

  @Column(name = "final_exam_passing_grade", precision = 6, scale = 2)
  private BigDecimal finalExamPassingGrade;

  @Column(name = "final_exam_score", precision = 6, scale = 2)
  private BigDecimal finalExamScore;

  protected Subject() {}

  public Subject(UUID semesterId, String name, String color) {
    this.semesterId = semesterId;
    this.name = name;
    this.color = color;
  }

  public void update(
      UUID semesterId,
      String name,
      String code,
      String professor,
      int credits,
      Integer workloadHours,
      String color,
      SubjectStatus status) {
    this.semesterId = semesterId;
    this.name = name;
    this.code = code;
    this.professor = professor;
    this.credits = credits;
    this.workloadHours = workloadHours;
    this.color = color;
    this.status = status;
  }

  /** Regra de nota própria da matéria. Cada campo {@code null} herda a preferência do usuário. */
  public void updateGrading(GradingOverrides o) {
    this.gradeMethod = o.method();
    this.gradeScaleMax = o.scaleMax();
    this.passingGrade = o.passingGrade();
    this.maxAbsencePct = o.maxAbsencePct();
    this.totalClasses = o.totalClasses() == null ? 0 : o.totalClasses();
    this.finalExamEnabled = o.finalExamEnabled();
    this.finalExamMinAverage = o.finalExamMinAverage();
    this.finalExamAverageWeight = o.finalExamAverageWeight();
    this.finalExamPassingGrade = o.finalExamPassingGrade();
    this.finalExamScore = o.finalExamScore();
  }

  public GradingOverrides getGrading() {
    return new GradingOverrides(
        gradeMethod,
        gradeScaleMax,
        passingGrade,
        maxAbsencePct,
        totalClasses == 0 ? null : totalClasses,
        finalExamEnabled,
        finalExamMinAverage,
        finalExamAverageWeight,
        finalExamPassingGrade,
        finalExamScore);
  }

  public int getTotalClasses() {
    return totalClasses;
  }

  public UUID getSemesterId() {
    return semesterId;
  }

  public String getName() {
    return name;
  }

  public String getCode() {
    return code;
  }

  public String getProfessor() {
    return professor;
  }

  public int getCredits() {
    return credits;
  }

  public Integer getWorkloadHours() {
    return workloadHours;
  }

  public String getColor() {
    return color;
  }

  public SubjectStatus getStatus() {
    return status;
  }
}
