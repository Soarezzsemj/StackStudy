package com.stackstudy.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

  Optional<AuthSession> findByIdAndUserId(UUID id, UUID userId);

  /** Sessões que ainda valem, da mais recente para a mais antiga (índice por user_id). */
  @Query(
      "select s from AuthSession s where s.userId = :userId and s.revokedAt is null"
          + " and s.expiresAt > :now order by s.lastUsedAt desc")
  List<AuthSession> findActive(@Param("userId") UUID userId, @Param("now") Instant now);

  @Query(
      "select count(s) > 0 from AuthSession s where s.id = :id and s.userId = :userId"
          + " and s.revokedAt is null")
  boolean isNotRevoked(@Param("id") UUID id, @Param("userId") UUID userId);

  /** Encerra todas as sessões do usuário. */
  @Modifying
  @Query(
      "update AuthSession s set s.revokedAt = :now, s.updatedAt = :now where s.userId = :userId"
          + " and s.revokedAt is null")
  int revokeAll(@Param("userId") UUID userId, @Param("now") Instant now);

  /** Encerra todas as sessões do usuário, menos a {@code keep}. */
  @Modifying
  @Query(
      "update AuthSession s set s.revokedAt = :now, s.updatedAt = :now where s.userId = :userId"
          + " and s.revokedAt is null and s.id <> :keep")
  int revokeAllExcept(
      @Param("userId") UUID userId, @Param("keep") UUID keep, @Param("now") Instant now);

  /** Limpeza no login: sessões encerradas ou vencidas há mais de 30 dias (sem job de fundo). */
  @Modifying
  @Query(
      "delete from AuthSession s where s.userId = :userId and"
          + " (s.revokedAt < :before or s.expiresAt < :before)")
  int deleteStale(@Param("userId") UUID userId, @Param("before") Instant before);
}
