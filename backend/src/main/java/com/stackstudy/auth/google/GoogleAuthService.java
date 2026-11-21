package com.stackstudy.auth.google;

import com.stackstudy.auth.AuthService;
import com.stackstudy.auth.SignupProperties;
import com.stackstudy.auth.User;
import com.stackstudy.auth.UserRepository;
import com.stackstudy.auth.google.GoogleIdTokenVerifier.GoogleIdentity;
import com.stackstudy.common.security.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.Keys;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Login com Google (OIDC, fluxo de código com PKCE, tudo no servidor). Desligado por padrão ({@code
 * AUTH_GOOGLE_ENABLED=false}): as rotas respondem 404.
 *
 * <ul>
 *   <li>{@code start}: gera {@code state}, {@code nonce} e o verificador PKCE, guarda os três num
 *       cookie curto (assinado, HttpOnly, 10 min) e redireciona ao Google.
 *   <li>{@code callback}: confere o {@code state}, troca o código pelo {@code id_token}, valida o
 *       token ({@link GoogleIdTokenVerifier}) e entra na conta: pelo {@code sub} já vinculado ou,
 *       na primeira vez, pelo e-mail <b>verificado</b> (e-mail não verificado nunca vincula).
 *   <li>Sem conta com aquele e-mail: não cria conta (o cadastro pede aceite e perfil); manda para o
 *       cadastro.
 * </ul>
 */
@Service
public class GoogleAuthService {

  static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
  static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
  static final String JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
  static final Duration FLOW_TTL = Duration.ofMinutes(10);
  static final Duration JWKS_TTL = Duration.ofHours(1);

  /** Para onde o navegador vai depois do callback. */
  public enum Result {
    SIGNED_IN,
    NO_ACCOUNT,
    UNVERIFIED_EMAIL,
    ERROR
  }

  public record Start(String redirectUrl, String flowCookie) {}

  public record Callback(Result result, AuthService.AuthResult session) {}

  private final SignupProperties signup;
  private final UserRepository users;
  private final AuthService auth;
  private final SecretKey flowKey;
  private final Clock clock;
  private final RestClient http = RestClient.create();
  private final SecureRandom random = new SecureRandom();
  private final AtomicReference<CachedKeys> jwks = new AtomicReference<>();

  private record CachedKeys(JwkSet set, Instant fetchedAt) {}

  public GoogleAuthService(
      SignupProperties signup,
      UserRepository users,
      AuthService auth,
      SecurityProperties security,
      Clock clock) {
    this.signup = signup;
    this.users = users;
    this.auth = auth;
    this.clock = clock;
    this.flowKey = Keys.hmacShaKeyFor(security.jwt().secret().getBytes(StandardCharsets.UTF_8));
    if (signup.google().enabled() && !signup.google().configured()) {
      throw new IllegalStateException(
          "AUTH_GOOGLE_ENABLED=true needs AUTH_GOOGLE_CLIENT_ID, AUTH_GOOGLE_CLIENT_SECRET and"
              + " AUTH_GOOGLE_REDIRECT_URI");
    }
  }

  public boolean enabled() {
    return signup.google().enabled() && signup.google().configured();
  }

  public Start start() {
    String state = randomToken();
    String nonce = randomToken();
    String verifier = randomToken() + randomToken();
    String cookie =
        Jwts.builder()
            .claims(Map.of("state", state, "nonce", nonce, "verifier", verifier))
            .expiration(Date.from(clock.instant().plus(FLOW_TTL)))
            .signWith(flowKey)
            .compact();
    String url =
        AUTH_URL
            + "?response_type=code&scope=openid%20email%20profile"
            + "&client_id="
            + enc(signup.google().clientId())
            + "&redirect_uri="
            + enc(signup.google().redirectUri())
            + "&state="
            + enc(state)
            + "&nonce="
            + enc(nonce)
            + "&code_challenge="
            + enc(challenge(verifier))
            + "&code_challenge_method=S256";
    return new Start(url, cookie);
  }

  @Transactional
  public Callback callback(String code, String state, String flowCookie, String userAgent) {
    Claims flow;
    try {
      flow =
          Jwts.parser()
              .verifyWith(flowKey)
              .clock(() -> Date.from(clock.instant()))
              .build()
              .parseSignedClaims(flowCookie == null ? "" : flowCookie)
              .getPayload();
    } catch (JwtException | IllegalArgumentException e) {
      return new Callback(Result.ERROR, null);
    }
    if (code == null || state == null || !state.equals(flow.get("state", String.class))) {
      return new Callback(Result.ERROR, null);
    }
    GoogleIdentity identity;
    try {
      String idToken = exchange(code, flow.get("verifier", String.class));
      identity =
          new GoogleIdTokenVerifier(signup.google().clientId(), this::key, clock)
              .verify(idToken, flow.get("nonce", String.class));
    } catch (RuntimeException e) {
      return new Callback(Result.ERROR, null);
    }
    return signIn(identity, userAgent);
  }

  /** Regra do vínculo (separada para teste): só e-mail verificado e sem conflito de {@code sub}. */
  @Transactional
  public Callback signIn(GoogleIdentity identity, String userAgent) {
    if (!identity.emailVerified()) {
      return new Callback(Result.UNVERIFIED_EMAIL, null);
    }
    Optional<User> bySub = users.findByGoogleSub(identity.sub());
    User user;
    if (bySub.isPresent()) {
      user = bySub.get();
    } else {
      Optional<User> byEmail = users.findByEmail(AuthService.normalizeEmail(identity.email()));
      if (byEmail.isEmpty()) {
        return new Callback(Result.NO_ACCOUNT, null);
      }
      user = byEmail.get();
      if (user.getGoogleSub() != null) {
        // Já vinculada a outra conta Google: não troca o vínculo.
        return new Callback(Result.ERROR, null);
      }
      user.linkGoogle(identity.sub());
      // O Google verificou o e-mail: vale como verificação aqui também.
      user.markEmailVerified(clock.instant());
    }
    return new Callback(Result.SIGNED_IN, auth.startSession(user, userAgent));
  }

  private String exchange(String code, String verifier) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "authorization_code");
    form.add("code", code);
    form.add("client_id", signup.google().clientId());
    form.add("client_secret", signup.google().clientSecret());
    form.add("redirect_uri", signup.google().redirectUri());
    form.add("code_verifier", verifier);
    @SuppressWarnings("unchecked")
    Map<String, Object> body =
        http.post()
            .uri(TOKEN_URL)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map.class);
    Object idToken = body == null ? null : body.get("id_token");
    if (!(idToken instanceof String s)) {
      throw new IllegalStateException("No id_token in Google response");
    }
    return s;
  }

  /** Chaves públicas do Google (JWKS), em cache por 1 h. */
  private Key key(String kid) {
    CachedKeys cached = jwks.get();
    if (cached == null || cached.fetchedAt().plus(JWKS_TTL).isBefore(clock.instant())) {
      String json = http.get().uri(JWKS_URL).retrieve().body(String.class);
      cached = new CachedKeys(Jwks.setParser().build().parse(json), clock.instant());
      jwks.set(cached);
    }
    for (Jwk<?> jwk : cached.set().getKeys()) {
      if (kid.equals(jwk.getId())) {
        return jwk.toKey();
      }
    }
    return null;
  }

  private String randomToken() {
    byte[] bytes = new byte[24];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String challenge(String verifier) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String enc(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
