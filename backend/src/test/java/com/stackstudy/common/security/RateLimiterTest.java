package com.stackstudy.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

  /** Relógio que o teste avança. */
  static final class MutableClock extends Clock {
    Instant now = Instant.parse("2026-11-21T12:00:00Z");

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private final MutableClock clock = new MutableClock();
  private final RateLimiter limiter = new RateLimiter(clock);
  private static final Duration MINUTE = Duration.ofMinutes(1);

  @Test
  void allowsUpToTheLimitThenBlocksUntilTheWindowEnds() {
    for (int i = 0; i < 3; i++) {
      assertThat(limiter.acquire("k", 3, MINUTE).allowed()).isTrue();
    }
    RateLimiter.Decision blocked = limiter.acquire("k", 3, MINUTE);
    assertThat(blocked.allowed()).isFalse();
    assertThat(blocked.retryAfterSeconds()).isEqualTo(60);

    clock.now = clock.now.plusSeconds(45);
    assertThat(limiter.acquire("k", 3, MINUTE).retryAfterSeconds()).isEqualTo(15);
    clock.now = clock.now.plusSeconds(15);
    assertThat(limiter.acquire("k", 3, MINUTE).allowed()).isTrue();
  }

  @Test
  void keysAreIndependent() {
    limiter.acquire("a", 1, MINUTE);
    assertThat(limiter.acquire("a", 1, MINUTE).allowed()).isFalse();
    assertThat(limiter.acquire("b", 1, MINUTE).allowed()).isTrue();
  }

  @Test
  void checkDoesNotCountAndResetForgets() {
    assertThat(limiter.check("f", 2).allowed()).isTrue();
    limiter.acquire("f", 2, MINUTE);
    assertThat(limiter.check("f", 2).allowed()).isTrue();
    limiter.acquire("f", 2, MINUTE);
    assertThat(limiter.check("f", 2).allowed()).isFalse();
    limiter.reset("f");
    assertThat(limiter.check("f", 2).allowed()).isTrue();
  }

  @Test
  void memoryIsBoundedAndExpiredKeysArePurged() {
    for (int i = 0; i < RateLimiter.MAX_KEYS; i++) {
      limiter.acquire("ip-" + i, 5, MINUTE);
    }
    assertThat(limiter.size()).isEqualTo(RateLimiter.MAX_KEYS);
    // Cheio e nada vencido: chave nova é recusada (não cresce sem fim).
    assertThat(limiter.acquire("new", 5, MINUTE).allowed()).isFalse();
    // Depois da janela, a limpeza libera espaço.
    clock.now = clock.now.plus(MINUTE);
    assertThat(limiter.acquire("new", 5, MINUTE).allowed()).isTrue();
    assertThat(limiter.size()).isEqualTo(1);
  }

  @Test
  void prodGuardRefusesInsecureConfig() {
    RateLimitProperties.Rule r = new RateLimitProperties.Rule(10, MINUTE);
    RateLimitProperties on = new RateLimitProperties(true, r, r, r, r, r);
    RateLimitProperties off = new RateLimitProperties(false, r, r, r, r, r);
    SecurityProperties secure = props(true, "Lax", List.of());
    SecurityProperties insecure = props(false, "Lax", List.of());

    ProdSecurityGuard.check(secure, on);
    assertThatThrownBy(() -> ProdSecurityGuard.check(insecure, on))
        .hasMessageContaining("REFRESH_COOKIE_SECURE");
    assertThatThrownBy(() -> ProdSecurityGuard.check(secure, off))
        .hasMessageContaining("RATE_LIMIT_ENABLED");
  }

  @Test
  void corsWildcardAndInconsistentCookieAreInvalid() {
    assertThat(props(true, "Lax", List.of("https://app.example")).cors().isExplicit()).isTrue();
    assertThat(props(true, "Lax", List.of("*")).cors().isExplicit()).isFalse();
    assertThat(props(true, "None", List.of()).isCookieConsistent()).isTrue();
    assertThat(props(false, "None", List.of()).isCookieConsistent()).isFalse();
    assertThat(props(false, "Strict", List.of()).isCookieConsistent()).isTrue();
    assertThat(props(true, "Whatever", List.of()).isCookieConsistent()).isFalse();
  }

  private static SecurityProperties props(boolean secure, String sameSite, List<String> origins) {
    return new SecurityProperties(
        new SecurityProperties.Jwt(
            "x".repeat(40), "stackstudy", Duration.ofMinutes(15), Duration.ofDays(14)),
        new SecurityProperties.RefreshCookie("ss_refresh", "/api/auth", secure, sameSite),
        new SecurityProperties.Cors(origins));
  }
}
