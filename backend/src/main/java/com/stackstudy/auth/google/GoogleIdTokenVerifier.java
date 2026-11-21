package com.stackstudy.auth.google;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.ProtectedHeader;
import java.security.Key;
import java.time.Clock;
import java.util.Date;
import java.util.Set;
import java.util.function.Function;

/**
 * Valida o {@code id_token} do Google: assinatura RS256 com a chave do JWKS (pelo {@code kid}),
 * emissor, audiência (o nosso client id), validade e {@code nonce}. Testado com chaves locais.
 */
public class GoogleIdTokenVerifier {

  /** O que interessa do token: o vínculo só acontece com {@code emailVerified = true}. */
  public record GoogleIdentity(String sub, String email, boolean emailVerified, String name) {}

  static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

  private final String clientId;
  private final Function<String, Key> keyByKid;
  private final Clock clock;

  public GoogleIdTokenVerifier(String clientId, Function<String, Key> keyByKid, Clock clock) {
    this.clientId = clientId;
    this.keyByKid = keyByKid;
    this.clock = clock;
  }

  public GoogleIdentity verify(String idToken, String expectedNonce) {
    Claims claims;
    try {
      claims =
          Jwts.parser()
              .keyLocator(
                  header -> {
                    String kid = header instanceof ProtectedHeader p ? p.getKeyId() : null;
                    Key key = kid == null ? null : keyByKid.apply(kid);
                    if (key == null) {
                      throw new JwtException("Unknown signing key");
                    }
                    return key;
                  })
              .requireAudience(clientId)
              .clock(() -> Date.from(clock.instant()))
              .build()
              .parseSignedClaims(idToken)
              .getPayload();
    } catch (JwtException | IllegalArgumentException e) {
      throw new InvalidGoogleTokenException("Invalid Google id_token");
    }
    if (!ISSUERS.contains(claims.getIssuer())) {
      throw new InvalidGoogleTokenException("Unexpected issuer");
    }
    if (expectedNonce == null || !expectedNonce.equals(claims.get("nonce", String.class))) {
      throw new InvalidGoogleTokenException("Nonce mismatch");
    }
    Object verified = claims.get("email_verified");
    boolean emailVerified =
        Boolean.TRUE.equals(verified) || "true".equalsIgnoreCase(String.valueOf(verified));
    String email = claims.get("email", String.class);
    if (claims.getSubject() == null || email == null) {
      throw new InvalidGoogleTokenException("Missing subject or email");
    }
    return new GoogleIdentity(
        claims.getSubject(), email, emailVerified, claims.get("name", String.class));
  }

  public static class InvalidGoogleTokenException extends RuntimeException {
    public InvalidGoogleTokenException(String message) {
      super(message);
    }
  }
}
