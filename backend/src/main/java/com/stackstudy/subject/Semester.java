package com.stackstudy.subject;

import com.stackstudy.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "semesters")
public class Semester extends BaseEntity {

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(nullable = false, length = 80)
  private String name;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Column(nullable = false)
  private boolean archived;

  /** Semestre atual do usuário (no máximo um; índice único parcial no banco). */
  @Column(name = "is_current", nullable = false)
  private boolean current;

  protected Semester() {}

  public Semester(UUID userId, String name, LocalDate startDate, LocalDate endDate) {
    this.userId = userId;
    this.name = name;
    this.startDate = startDate;
    this.endDate = endDate;
  }

  public void update(String name, LocalDate startDate, LocalDate endDate) {
    this.name = name;
    this.startDate = startDate;
    this.endDate = endDate;
  }

  /** Arquivar tira o semestre de "atual" (o banco proíbe os dois juntos). */
  public void setArchived(boolean archived) {
    this.archived = archived;
    if (archived) {
      this.current = false;
    }
  }

  public void setCurrent(boolean current) {
    this.current = current;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getName() {
    return name;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public boolean isArchived() {
    return archived;
  }

  public boolean isCurrent() {
    return current;
  }
}
