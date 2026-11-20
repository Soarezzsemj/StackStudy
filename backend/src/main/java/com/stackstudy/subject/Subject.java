package com.stackstudy.subject;

import com.stackstudy.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Matéria. O dono é resolvido pelo semestre ({@code subject → semester → user}). Colunas da Fase 4
 * (regra de nota, frequência) ainda não são mapeadas: ficam com o valor padrão do banco.
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
