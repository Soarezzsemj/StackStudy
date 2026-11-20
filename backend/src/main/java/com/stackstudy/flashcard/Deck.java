package com.stackstudy.flashcard;

import com.stackstudy.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** Baralho. Matéria opcional; limites diários próprios opcionais (null = preferências). */
@Entity
@Table(name = "decks")
public class Deck extends BaseEntity {

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "subject_id")
  private UUID subjectId;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(name = "new_per_day")
  private Integer newPerDay;

  @Column(name = "reviews_per_day")
  private Integer reviewsPerDay;

  protected Deck() {}

  public Deck(UUID userId) {
    this.userId = userId;
  }

  public void update(String name, UUID subjectId, Integer newPerDay, Integer reviewsPerDay) {
    this.name = name;
    this.subjectId = subjectId;
    this.newPerDay = newPerDay;
    this.reviewsPerDay = reviewsPerDay;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getSubjectId() {
    return subjectId;
  }

  public String getName() {
    return name;
  }

  public Integer getNewPerDay() {
    return newPerDay;
  }

  public Integer getReviewsPerDay() {
    return reviewsPerDay;
  }
}
