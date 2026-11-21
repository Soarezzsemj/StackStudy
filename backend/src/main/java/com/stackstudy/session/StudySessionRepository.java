package com.stackstudy.session;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Toda busca filtra por {@code userId}. As agregações são nativas: o dia é calculado no Postgres
 * com {@code AT TIME ZONE} no fuso do usuário (os dados ficam em UTC). Índice usado: {@code
 * (user_id, started_at)}.
 */
public interface StudySessionRepository extends JpaRepository<StudySession, UUID> {

  Optional<StudySession> findByIdAndUserId(UUID id, UUID userId);

  Optional<StudySession> findByUserIdAndEndedAtIsNull(UUID userId);

  /** Histórico (só encerradas), mais recentes primeiro. */
  @Query(
      """
      select s from StudySession s
      where s.userId = :userId and s.endedAt is not null
        and s.startedAt >= :from and s.startedAt < :to
      """)
  Page<StudySession> findFinished(
      @Param("userId") UUID userId,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);

  /** [dia local, segundos] das sessões encerradas que começaram em [from, to). */
  @Query(
      value =
          """
          SELECT CAST(s.started_at AT TIME ZONE CAST(:tz AS text) AS date) AS day,
                 SUM(s.effective_seconds) AS seconds
          FROM study_sessions s
          WHERE s.user_id = :userId AND s.ended_at IS NOT NULL
            AND s.started_at >= :from AND s.started_at < :to
          GROUP BY day
          HAVING SUM(s.effective_seconds) > 0
          ORDER BY day
          """,
      nativeQuery = true)
  List<Object[]> sumByDay(
      @Param("userId") UUID userId,
      @Param("tz") String tz,
      @Param("from") Instant from,
      @Param("to") Instant to);

  /** [subject_id, segundos] no período. */
  @Query(
      value =
          """
          SELECT s.subject_id, SUM(s.effective_seconds) AS seconds
          FROM study_sessions s
          WHERE s.user_id = :userId AND s.ended_at IS NOT NULL AND s.subject_id IS NOT NULL
            AND s.started_at >= :from AND s.started_at < :to
          GROUP BY s.subject_id
          HAVING SUM(s.effective_seconds) > 0
          ORDER BY seconds DESC
          """,
      nativeQuery = true)
  List<Object[]> sumBySubject(
      @Param("userId") UUID userId, @Param("from") Instant from, @Param("to") Instant to);

  /** Todos os dias locais com estudo (para as sequências). */
  @Query(
      value =
          """
          SELECT CAST(s.started_at AT TIME ZONE CAST(:tz AS text) AS date) AS day
          FROM study_sessions s
          WHERE s.user_id = :userId AND s.ended_at IS NOT NULL
          GROUP BY day
          HAVING SUM(s.effective_seconds) > 0
          ORDER BY day
          """,
      nativeQuery = true)
  List<Object> studyDays(@Param("userId") UUID userId, @Param("tz") String tz);
}
