package com.stackstudy.grade;

import com.stackstudy.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** Avaliação de uma matéria (prova, trabalho...). Dono resolvido pela matéria. */
@Entity
@Table(name = "assessments")
public class Assessment extends BaseEntity {

  @Column(name = "subject_id", nullable = false, updatable = false)
  private UUID subjectId;

  @Column(nullable = false, length = 80)
  private String name;

  @Column(nullable = false, precision = 6, scale = 2)
  private BigDecimal weight;

  /** Nota na escala da matéria; null = ainda não lançada. */
  @Column(precision = 6, scale = 2)
  private BigDecimal score;

  @Column(nullable = false)
  private int position;

  /** Quanto a avaliação vale na soma de pontos (null se nunca foi informado). */
  @Column(name = "max_points", precision = 6, scale = 2)
  private BigDecimal maxPoints;

  /** Ponto extra (soma de pontos): soma ao total, fora da pontuação prevista. */
  @Column(nullable = false)
  private boolean bonus;

  protected Assessment() {}

  public Assessment(UUID subjectId) {
    this.subjectId = subjectId;
  }

  public void update(String name, BigDecimal weight, BigDecimal score, int position) {
    this.name = name;
    this.weight = weight;
    this.score = score;
    this.position = position;
  }

  public void updatePoints(BigDecimal maxPoints, boolean bonus) {
    this.maxPoints = maxPoints;
    this.bonus = bonus;
  }

  public BigDecimal getMaxPoints() {
    return maxPoints;
  }

  public boolean isBonus() {
    return bonus;
  }

  public UUID getSubjectId() {
    return subjectId;
  }

  public String getName() {
    return name;
  }

  public BigDecimal getWeight() {
    return weight;
  }

  public BigDecimal getScore() {
    return score;
  }

  public int getPosition() {
    return position;
  }
}
