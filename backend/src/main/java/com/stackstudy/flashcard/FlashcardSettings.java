package com.stackstudy.flashcard;

import com.stackstudy.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** Limites diários globais. Sem linha no banco, valem 20 novas e 100 revisões por dia. */
@Entity
@Table(name = "flashcard_settings")
public class FlashcardSettings extends BaseEntity {

  public static final int DEFAULT_NEW_PER_DAY = 20;
  public static final int DEFAULT_REVIEWS_PER_DAY = 100;

  @Column(name = "user_id", nullable = false, updatable = false, unique = true)
  private UUID userId;

  @Column(name = "new_per_day", nullable = false)
  private int newPerDay = DEFAULT_NEW_PER_DAY;

  @Column(name = "reviews_per_day", nullable = false)
  private int reviewsPerDay = DEFAULT_REVIEWS_PER_DAY;

  protected FlashcardSettings() {}

  public FlashcardSettings(UUID userId) {
    this.userId = userId;
  }

  public void update(int newPerDay, int reviewsPerDay) {
    this.newPerDay = newPerDay;
    this.reviewsPerDay = reviewsPerDay;
  }

  public UUID getUserId() {
    return userId;
  }

  public int getNewPerDay() {
    return newPerDay;
  }

  public int getReviewsPerDay() {
    return reviewsPerDay;
  }
}
