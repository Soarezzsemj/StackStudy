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
  private static final String SESSION_CLAIM = "sid";

  private final SecretKey key;
  private final SecurityProperties.Jwt props;
  private final Clock clock;

  public JwtService(SecurityProperties properties, Clock clock) {
    this.props = properties.jwt();
    this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    this.clock = clock;
  }

  /** Access token da sessão {@code sessionId} (claim {@code sid}). */
  public String issueAccessToken(UUID userId, String email, UUID sessionId) {
    Instant now = clock.instant();
    return Jwts.builder()
        .issuer(props.issuer())
        .subject(userId.toString())
        .claim(EMAIL_CLAIM, email)
        .claim(SESSION_CLAIM, sessionId.toString())
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
      // Token sem sessão (emitido antes da V7) não vale: o frontend renova pelo refresh.
      String sid = claims.get(SESSION_CLAIM, String.class);
      if (sid == null) {
        return Optional.empty();
      }
      return Optional.of(
          new AuthenticatedUser(
              UUID.fromString(claims.getSubject()),
              claims.get(EMAIL_CLAIM, String.class),
              UUID.fromString(sid)));
    } catch (JwtException | IllegalArgumentException ex) {
      return Optional.empty();
    }
  }
}
