package com.stackstudy.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
  private static final UUID SID = UUID.fromString("00000000-0000-0000-0000-00000000005e");

  private static SecurityProperties props(String secret) {
    return new SecurityProperties(
        new SecurityProperties.Jwt(
            secret, "stackstudy", Duration.ofMinutes(15), Duration.ofDays(14)),
        new SecurityProperties.RefreshCookie("ss_refresh", "/api/auth", false, "Strict"),
        new SecurityProperties.Cors(List.of()));
  }

  private static JwtService service(Instant now, String secret) {
    return new JwtService(props(secret), Clock.fixed(now, ZoneOffset.UTC));
  }

  private static final String SECRET = "unit-test-secret-0123456789abcdef0123456789";

  @Test
  void issuedTokenRoundTrips() {
    UUID id = UUID.randomUUID();
    String token = service(NOW, SECRET).issueAccessToken(id, "ada@example.com", SID);

    assertThat(service(NOW.plusSeconds(60), SECRET).parse(token))
        .contains(new AuthenticatedUser(id, "ada@example.com", SID));
  }

  @Test
  void expiredTokenIsRejected() {
    String token = service(NOW, SECRET).issueAccessToken(UUID.randomUUID(), "a@b.c", SID);

    assertThat(service(NOW.plus(Duration.ofMinutes(16)), SECRET).parse(token)).isEmpty();
  }

  @Test
  void tokenSignedWithAnotherKeyIsRejected() {
    String token = service(NOW, SECRET).issueAccessToken(UUID.randomUUID(), "a@b.c", SID);

    assertThat(service(NOW, "another-secret-0123456789abcdef0123456789").parse(token)).isEmpty();
    assertThat(service(NOW, SECRET).parse("garbage")).isEmpty();
  }
}
