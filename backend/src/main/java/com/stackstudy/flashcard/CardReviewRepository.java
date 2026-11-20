package com.stackstudy.flashcard;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Revisões. Índice (user_id, reviewed_at) cobre a contagem do dia e as estatísticas. */
public interface CardReviewRepository extends JpaRepository<CardReview, UUID> {

  Optional<CardReview> findByIdAndUserId(UUID id, UUID userId);

  /** Há resposta mais nova para a carta? (só a última pode ser desfeita) */
  boolean existsByCardIdAndReviewedAtGreaterThan(UUID cardId, Instant reviewedAt);

  /** [deck_id, novas, revisões] respondidas desde {@code from}, pelo deck atual da carta. */
  @Query(
      value =
          """
          SELECT c.deck_id,
                 COUNT(*) FILTER (WHERE r.was_new),
                 COUNT(*) FILTER (WHERE NOT r.was_new)
          FROM card_reviews r
          JOIN cards c ON c.id = r.card_id
          WHERE r.user_id = :userId AND r.reviewed_at >= :from
          GROUP BY c.deck_id
          """,
      nativeQuery = true)
  List<Object[]> countsSince(@Param("userId") UUID userId, @Param("from") Instant from);

  /** [total, acertos (qualidade ≥ 3)] desde {@code from}. */
  @Query(
      value =
          """
          SELECT COUNT(*), COUNT(*) FILTER (WHERE r.quality >= 3)
          FROM card_reviews r
          JOIN cards c ON c.id = r.card_id
          WHERE r.user_id = :userId AND r.reviewed_at >= :from
            AND (CAST(:deckId AS uuid) IS NULL OR c.deck_id = CAST(:deckId AS uuid))
          """,
      nativeQuery = true)
  List<Object[]> accuracySince(
      @Param("userId") UUID userId, @Param("from") Instant from, @Param("deckId") String deckId);

  /** [dia local, revisões] desde {@code from}. */
  @Query(
      value =
          """
          SELECT CAST(r.reviewed_at AT TIME ZONE CAST(:tz AS text) AS date) AS day, COUNT(*)
          FROM card_reviews r
          JOIN cards c ON c.id = r.card_id
          WHERE r.user_id = :userId AND r.reviewed_at >= :from
            AND (CAST(:deckId AS uuid) IS NULL OR c.deck_id = CAST(:deckId AS uuid))
          GROUP BY day
          ORDER BY day
          """,
      nativeQuery = true)
  List<Object[]> perDaySince(
      @Param("userId") UUID userId,
      @Param("tz") String tz,
      @Param("from") Instant from,
      @Param("deckId") String deckId);
}
