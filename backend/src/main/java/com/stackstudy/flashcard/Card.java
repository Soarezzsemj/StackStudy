package com.stackstudy.flashcard;

import com.stackstudy.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Carta. {@code dueOn} é a data local da próxima revisão (null = nova, nunca revisada). Estado SM-2
 * em {@code easeFactor}, {@code intervalDays}, {@code repetitions} e {@code lapses}.
 */
@Entity
@Table(name = "cards")
public class Card extends BaseEntity {

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "deck_id", nullable = false)
  private UUID deckId;

  @Column(nullable = false, columnDefinition = "text")
  private String front;

  @Column(nullable = false, columnDefinition = "text")
  private String back;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(nullable = false, columnDefinition = "text[]")
  private String[] tags = new String[0];

  @Column(name = "ease_factor", nullable = false, precision = 4, scale = 2)
  private BigDecimal easeFactor = Sm2.INITIAL_EASE;

  @Column(name = "interval_days", nullable = false)
  private int intervalDays;

  @Column(nullable = false)
  private int repetitions;

  @Column(nullable = false)
  private int lapses;

  @Column(name = "due_on")
  private LocalDate dueOn;

  @Column(name = "last_reviewed_at")
  private Instant lastReviewedAt;

  protected Card() {}

  public Card(UUID userId, UUID deckId) {
    this.userId = userId;
    this.deckId = deckId;
  }

  public void update(UUID deckId, String front, String back, List<String> tags) {
    this.deckId = deckId;
    this.front = front;
    this.back = back;
    this.tags = tags.toArray(String[]::new);
  }

  public Sm2.State state() {
    return new Sm2.State(easeFactor, intervalDays, repetitions, lapses);
  }

  /** Aplica o resultado de uma revisão. */
  public void schedule(Sm2.State next, LocalDate dueOn, Instant reviewedAt) {
    apply(next, dueOn, reviewedAt);
  }

  /** Volta ao estado anterior (desfazer). */
  public void restore(Sm2.State previous, LocalDate dueOn, Instant lastReviewedAt) {
    apply(previous, dueOn, lastReviewedAt);
  }

  private void apply(Sm2.State s, LocalDate dueOn, Instant lastReviewedAt) {
    this.easeFactor = s.ease();
    this.intervalDays = s.intervalDays();
    this.repetitions = s.repetitions();
    this.lapses = s.lapses();
    this.dueOn = dueOn;
    this.lastReviewedAt = lastReviewedAt;
  }

  public boolean isNew() {
    return lastReviewedAt == null;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getDeckId() {
    return deckId;
  }

  public String getFront() {
    return front;
  }

  public String getBack() {
    return back;
  }

  public List<String> getTags() {
    return List.of(tags);
  }

  public BigDecimal getEaseFactor() {
    return easeFactor;
  }

  public int getIntervalDays() {
    return intervalDays;
  }

  public int getRepetitions() {
    return repetitions;
  }

  public int getLapses() {
    return lapses;
  }

  public LocalDate getDueOn() {
    return dueOn;
  }

  public Instant getLastReviewedAt() {
    return lastReviewedAt;
  }
}
