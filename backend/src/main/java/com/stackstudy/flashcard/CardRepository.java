package com.stackstudy.flashcard;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Toda consulta filtra por {@code user_id}. Nas nativas, {@code deckId} vem como texto (null =
 * todos os decks): {@code CAST(:deckId AS uuid)} aceita null sem erro de tipo no PostgreSQL.
 */
public interface CardRepository extends JpaRepository<Card, UUID> {

  Optional<Card> findByIdAndUserId(UUID id, UUID userId);

  /** O deck já deve ter sido validado como do usuário. */
  List<Card> findAllByDeckIdOrderByCreatedAtAscIdAsc(UUID deckId);

  /** Vencidas (já revisadas, data ≤ hoje), mais antigas primeiro. Índice (user_id, due_on). */
  @Query(
      value =
          """
          SELECT * FROM cards c
          WHERE c.user_id = :userId AND c.due_on IS NOT NULL AND c.due_on <= :today
            AND (CAST(:deckId AS uuid) IS NULL OR c.deck_id = CAST(:deckId AS uuid))
          ORDER BY c.due_on, c.created_at, c.id
          LIMIT :limit
          """,
      nativeQuery = true)
  List<Card> findDue(
      @Param("userId") UUID userId,
      @Param("today") LocalDate today,
      @Param("deckId") String deckId,
      @Param("limit") int limit);

  /**
   * Novas (nunca revisadas), na ordem em que foram criadas. Índice parcial (user_id, created_at).
   */
  @Query(
      value =
          """
          SELECT * FROM cards c
          WHERE c.user_id = :userId AND c.due_on IS NULL
            AND (CAST(:deckId AS uuid) IS NULL OR c.deck_id = CAST(:deckId AS uuid))
          ORDER BY c.created_at, c.id
          LIMIT :limit
          """,
      nativeQuery = true)
  List<Card> findNew(
      @Param("userId") UUID userId, @Param("deckId") String deckId, @Param("limit") int limit);

  /** [deck_id, total, novas, vencidas até hoje] por deck, numa consulta. */
  @Query(
      value =
          """
          SELECT c.deck_id,
                 COUNT(*),
                 COUNT(*) FILTER (WHERE c.due_on IS NULL),
                 COUNT(*) FILTER (WHERE c.due_on <= :today)
          FROM cards c
          WHERE c.user_id = :userId
          GROUP BY c.deck_id
          """,
      nativeQuery = true)
  List<Object[]> countsByDeck(@Param("userId") UUID userId, @Param("today") LocalDate today);

  /** [novas, aprendendo, maduras] (madura = intervalo ≥ 21 dias). */
  @Query(
      value =
          """
          SELECT COUNT(*) FILTER (WHERE c.due_on IS NULL),
                 COUNT(*) FILTER (WHERE c.due_on IS NOT NULL AND c.interval_days < 21),
                 COUNT(*) FILTER (WHERE c.due_on IS NOT NULL AND c.interval_days >= 21)
          FROM cards c
          WHERE c.user_id = :userId
            AND (CAST(:deckId AS uuid) IS NULL OR c.deck_id = CAST(:deckId AS uuid))
          """,
      nativeQuery = true)
  List<Object[]> stageCounts(@Param("userId") UUID userId, @Param("deckId") String deckId);

  /** [dia, cartas] vencendo de hoje (atrasadas contam hoje) até {@code until} exclusivo. */
  @Query(
      value =
          """
          SELECT GREATEST(c.due_on, :today) AS day, COUNT(*)
          FROM cards c
          WHERE c.user_id = :userId AND c.due_on IS NOT NULL AND c.due_on < :until
            AND (CAST(:deckId AS uuid) IS NULL OR c.deck_id = CAST(:deckId AS uuid))
          GROUP BY day
          ORDER BY day
          """,
      nativeQuery = true)
  List<Object[]> forecast(
      @Param("userId") UUID userId,
      @Param("today") LocalDate today,
      @Param("until") LocalDate until,
      @Param("deckId") String deckId);

  /**
   * Busca por texto em frente, verso e tags (sem diferenciar maiúsculas). {@code pattern} já vem
   * com os curingas escapados e cercado de {@code %}.
   */
  @Query(
      value =
          """
          SELECT * FROM cards c
          WHERE c.user_id = :userId
            AND (CAST(:deckId AS uuid) IS NULL OR c.deck_id = CAST(:deckId AS uuid))
            AND (c.front ILIKE :pattern ESCAPE '\\' OR c.back ILIKE :pattern ESCAPE '\\'
                 OR array_to_string(c.tags, ' ') ILIKE :pattern ESCAPE '\\')
          ORDER BY c.created_at DESC, c.id
          """,
      countQuery =
          """
          SELECT COUNT(*) FROM cards c
          WHERE c.user_id = :userId
            AND (CAST(:deckId AS uuid) IS NULL OR c.deck_id = CAST(:deckId AS uuid))
            AND (c.front ILIKE :pattern ESCAPE '\\' OR c.back ILIKE :pattern ESCAPE '\\'
                 OR array_to_string(c.tags, ' ') ILIKE :pattern ESCAPE '\\')
          """,
      nativeQuery = true)
  Page<Card> search(
      @Param("userId") UUID userId,
      @Param("deckId") String deckId,
      @Param("pattern") String pattern,
      Pageable pageable);
}
