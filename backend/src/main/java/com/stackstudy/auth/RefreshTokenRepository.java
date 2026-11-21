package com.stackstudy.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  @Modifying
  @Query(
      "update RefreshToken t set t.revoked = true, t.updatedAt = CURRENT_TIMESTAMP "
          + "where t.userId = :userId and t.revoked = false")
  int revokeAllForUser(@Param("userId") UUID userId);

  @Modifying
  @Query(
      "update RefreshToken t set t.revoked = true, t.updatedAt = CURRENT_TIMESTAMP "
          + "where t.sessionId = :sessionId and t.revoked = false")
  int revokeAllForSession(@Param("sessionId") UUID sessionId);

  /** Revoga os tokens do usuário fora da sessão {@code keep} (inclui os antigos, sem sessão). */
  @Modifying
  @Query(
      "update RefreshToken t set t.revoked = true, t.updatedAt = CURRENT_TIMESTAMP "
          + "where t.userId = :userId and t.revoked = false "
          + "and (t.sessionId is null or t.sessionId <> :keep)")
  int revokeAllForUserExcept(@Param("userId") UUID userId, @Param("keep") UUID keep);
}
