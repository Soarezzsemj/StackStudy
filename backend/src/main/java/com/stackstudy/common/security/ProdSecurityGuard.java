package com.stackstudy.common.security;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Em produção, configuração insegura impede o boot (melhor cair no deploy do que rodar aberto):
 * cookie do refresh sem {@code Secure} ou rate limit desligado.
 */
@Component
@Profile("prod")
public class ProdSecurityGuard {

  private final SecurityProperties security;
  private final RateLimitProperties rateLimits;

  public ProdSecurityGuard(SecurityProperties security, RateLimitProperties rateLimits) {
    this.security = security;
    this.rateLimits = rateLimits;
  }

  @PostConstruct
  void check() {
    check(security, rateLimits);
  }

  static void check(SecurityProperties security, RateLimitProperties rateLimits) {
    if (!security.refreshCookie().secure()) {
      throw new IllegalStateException(
          "REFRESH_COOKIE_SECURE must be true in production (the cookie would travel over HTTP)");
    }
    if (!rateLimits.enabled()) {
      throw new IllegalStateException("RATE_LIMIT_ENABLED must be true in production");
    }
  }
}
