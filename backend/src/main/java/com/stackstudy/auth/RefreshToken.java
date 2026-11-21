package com.stackstudy.auth;

import com.stackstudy.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Refresh token salvo só como hash SHA-256. O valor real fica no cookie. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(nullable = false)
  private boolean revoked;

  /** Sessão (dispositivo) do token. Null só em tokens emitidos antes da V7. */
  @Column(name = "session_id")
  private UUID sessionId;

  protected RefreshToken() {}

  public RefreshToken(UUID userId, UUID sessionId, String tokenHash, Instant expiresAt) {
    this.userId = userId;
    this.sessionId = sessionId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  public boolean isExpired(Instant now) {
    return !expiresAt.isAfter(now);
  }

  public void revoke() {
    this.revoked = true;
  }

  public UUID getSessionId() {
    return sessionId;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public boolean isRevoked() {
    return revoked;
  }
}
