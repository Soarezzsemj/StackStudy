package com.stackstudy.grade;

import com.stackstudy.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** Falta num dia: {@code count} aulas perdidas. No máximo um registro por matéria e dia. */
@Entity
@Table(name = "absences")
public class Absence extends BaseEntity {

  @Column(name = "subject_id", nullable = false, updatable = false)
  private UUID subjectId;

  @Column(nullable = false)
  private LocalDate date;

  @Column(nullable = false)
  private int count;

  protected Absence() {}

  public Absence(UUID subjectId, LocalDate date, int count) {
    this.subjectId = subjectId;
    this.date = date;
    this.count = count;
  }

  public void update(LocalDate date, int count) {
    this.date = date;
    this.count = count;
  }

  public UUID getSubjectId() {
    return subjectId;
  }

  public LocalDate getDate() {
    return date;
  }

  public int getCount() {
    return count;
  }
}
