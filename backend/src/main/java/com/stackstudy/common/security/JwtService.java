package com.stackstudy.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Cria e valida os access tokens (JWT assinado com HMAC). */
@Service
public class JwtService {

  private static final String EMAIL_CLAIM = "email";

  private final SecretKey key;
  private final SecurityProperties.Jwt props;
  private final Clock clock;

  public JwtService(SecurityProperties properties, Clock clock) {
    this.props = properties.jwt();
    this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    this.clock = clock;
  }

  public String issueAccessToken(UUID userId, String email) {
    Instant now = clock.instant();
    return Jwts.builder()
        .issuer(props.issuer())
        .subject(userId.toString())
        .claim(EMAIL_CLAIM, email)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(props.accessTokenTtl())))
        .signWith(key)
        .compact();
  }

  public long accessTokenTtlSeconds() {
    return props.accessTokenTtl().toSeconds();
  }

  public Optional<AuthenticatedUser> parse(String token) {
    try {
      Claims claims =
          Jwts.parser()
              .verifyWith(key)
              .requireIssuer(props.issuer())
              .clock(() -> Date.from(clock.instant()))
              .build()
              .parseSignedClaims(token)
              .getPayload();
      return Optional.of(
          new AuthenticatedUser(
              UUID.fromString(claims.getSubject()), claims.get(EMAIL_CLAIM, String.class)));
    } catch (JwtException | IllegalArgumentException ex) {
      return Optional.empty();
    }
  }
}
