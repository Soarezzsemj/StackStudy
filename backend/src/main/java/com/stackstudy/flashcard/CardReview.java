package com.stackstudy.flashcard;

import com.stackstudy.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma resposta numa revisão. Guarda o estado da carta <b>antes</b> da resposta, para desfazer, e
 * {@code wasNew} para contar o limite diário de novas.
 */
@Entity
@Table(name = "card_reviews")
public class CardReview extends BaseEntity {

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "card_id", nullable = false, updatable = false)
  private UUID cardId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 5)
  private Sm2.Rating rating;

  @Column(nullable = false)
  private short quality;

  @Column(name = "reviewed_at", nullable = false)
  private Instant reviewedAt;

  @Column(name = "was_new", nullable = false)
  private boolean wasNew;

  @Column(name = "prev_interval_days", nullable = false)
  private int prevIntervalDays;

  @Column(name = "new_interval_days", nullable = false)
  private int newIntervalDays;

  @Column(name = "prev_ease_factor", nullable = false, precision = 4, scale = 2)
  private BigDecimal prevEaseFactor;

  @Column(name = "prev_repetitions", nullable = false)
  private int prevRepetitions;

  @Column(name = "prev_lapses", nullable = false)
  private int prevLapses;

  @Column(name = "prev_due_on")
  private LocalDate prevDueOn;

  @Column(name = "prev_last_reviewed_at")
  private Instant prevLastReviewedAt;

  protected CardReview() {}

  /** Registra a resposta a partir da carta ainda no estado anterior. */
  public static CardReview before(
      Card card, Sm2.Rating rating, int newIntervalDays, Instant reviewedAt) {
    CardReview r = new CardReview();
    r.userId = card.getUserId();
    r.cardId = card.getId();
    r.rating = rating;
    r.quality = (short) rating.quality();
    r.reviewedAt = reviewedAt;
    r.wasNew = card.isNew();
    r.prevIntervalDays = card.getIntervalDays();
    r.newIntervalDays = newIntervalDays;
    r.prevEaseFactor = card.getEaseFactor();
    r.prevRepetitions = card.getRepetitions();
    r.prevLapses = card.getLapses();
    r.prevDueOn = card.getDueOn();
    r.prevLastReviewedAt = card.getLastReviewedAt();
    return r;
  }

  public Sm2.State previousState() {
    return new Sm2.State(prevEaseFactor, prevIntervalDays, prevRepetitions, prevLapses);
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getCardId() {
    return cardId;
  }

  public Sm2.Rating getRating() {
    return rating;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public boolean isWasNew() {
    return wasNew;
  }

  public LocalDate getPrevDueOn() {
    return prevDueOn;
  }

  public Instant getPrevLastReviewedAt() {
    return prevLastReviewedAt;
  }
}
