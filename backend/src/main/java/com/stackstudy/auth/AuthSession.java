package com.stackstudy.auth;

import com.stackstudy.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Uma sessão = um login num dispositivo. Os refresh tokens giram dentro dela; o access token leva o
 * id dela. Encerrar (revogar) a sessão derruba o dispositivo.
 */
@Entity
@Table(name = "auth_sessions")
public class AuthSession extends BaseEntity {

  /** Tamanho máximo guardado do User-Agent. */
  public static final int USER_AGENT_MAX = 255;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "user_agent", length = USER_AGENT_MAX)
  private String userAgent;

  @Column(name = "last_used_at", nullable = false)
  private Instant lastUsedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  protected AuthSession() {}

  public AuthSession(UUID userId, String userAgent, Instant now, Instant expiresAt) {
    this.userId = userId;
    this.userAgent = trim(userAgent);
    this.lastUsedAt = now;
    this.expiresAt = expiresAt;
  }

  /** Novo refresh token emitido: marca o uso e estende a validade. */
  public void touch(Instant now, Instant expiresAt) {
    this.lastUsedAt = now;
    this.expiresAt = expiresAt;
  }

  public void revoke(Instant now) {
    if (revokedAt == null) {
      revokedAt = now;
    }
  }

  public boolean isActive(Instant now) {
    return revokedAt == null && expiresAt.isAfter(now);
  }

  private static String trim(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String v = value.trim();
    return v.length() > USER_AGENT_MAX ? v.substring(0, USER_AGENT_MAX) : v;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public Instant getLastUsedAt() {
    return lastUsedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }
}
