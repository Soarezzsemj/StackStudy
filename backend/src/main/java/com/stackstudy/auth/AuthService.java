package com.stackstudy.auth;

import com.stackstudy.auth.dto.AuthResponse;
import com.stackstudy.auth.dto.LoginRequest;
import com.stackstudy.auth.dto.RegisterRequest;
import com.stackstudy.auth.dto.UserResponse;
import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.error.ErrorCode;
import com.stackstudy.common.error.RateLimitedException;
import com.stackstudy.common.security.JwtService;
import com.stackstudy.common.security.RateLimitProperties;
import com.stackstudy.common.security.RateLimiter;
import com.stackstudy.common.security.SecurityProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro, login, refresh e sessões. Cada login abre uma {@link AuthSession}; o refresh gira o
 * token dentro dela. Nunca loga senha, token nem hash.
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);
  private static final String DEFAULT_LOCALE = "pt-BR";

  /** Sessões encerradas/vencidas há mais que isso são apagadas no login do usuário. */
  private static final Duration STALE_SESSIONS = Duration.ofDays(30);

  /** Refresh token puro (vai no cookie) e o corpo da resposta. */
  public record AuthResult(AuthResponse body, String refreshToken) {}

  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final AuthSessionRepository sessions;
  private final ActiveSessionCache sessionCache;
  private final RefreshTokenGenerator tokenGenerator;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final SecurityProperties properties;
  private final RateLimiter limiter;
  private final RateLimitProperties rateLimits;
  private final Clock clock;

  /**
   * Hash de uma senha qualquer: o login de um e-mail que não existe também roda o BCrypt, para o
   * tempo de resposta não dizer se a conta existe.
   */
  private final String dummyHash;

  public AuthService(
      UserRepository users,
      RefreshTokenRepository refreshTokens,
      AuthSessionRepository sessions,
      ActiveSessionCache sessionCache,
      RefreshTokenGenerator tokenGenerator,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      SecurityProperties properties,
      RateLimiter limiter,
      RateLimitProperties rateLimits,
      Clock clock) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.sessions = sessions;
    this.sessionCache = sessionCache;
    this.tokenGenerator = tokenGenerator;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.properties = properties;
    this.limiter = limiter;
    this.rateLimits = rateLimits;
    this.clock = clock;
    this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
  }

  @Transactional
  public AuthResult register(RegisterRequest request, String userAgent) {
    String email = normalizeEmail(request.email());
    if (users.existsByEmail(email)) {
      throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, "Email already registered");
    }
    String locale = request.locale() == null ? DEFAULT_LOCALE : request.locale();
    User user;
    try {
      user =
          users.saveAndFlush(
              new User(
                  request.name().trim(),
                  email,
                  passwordEncoder.encode(request.password()),
                  locale));
    } catch (DataIntegrityViolationException ex) {
      throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, "Email already registered");
    }
    return issueTokens(user, newSession(user, userAgent));
  }

  /**
   * Login. A mesma resposta (e o mesmo custo de BCrypt) para e-mail inexistente e senha errada.
   * Muitas senhas erradas no mesmo e-mail bloqueiam aquele e-mail por um tempo, mesmo vindo de IPs
   * diferentes; durante o bloqueio, nem a senha certa entra.
   */
  @Transactional
  public AuthResult login(LoginRequest request, String userAgent) {
    String email = normalizeEmail(request.email());
    String failKey = "login-failures:" + email;
    RateLimitProperties.Rule rule = rateLimits.loginFailures();
    if (rateLimits.enabled()) {
      RateLimiter.Decision blocked = limiter.check(failKey, rule.limit());
      if (!blocked.allowed()) {
        throw new RateLimitedException(blocked.retryAfterSeconds());
      }
    }
    User found = users.findByEmail(email).orElse(null);
    boolean ok =
        passwordEncoder.matches(
            request.password(), found != null ? found.getPasswordHash() : dummyHash);
    if (found == null || !ok) {
      if (rateLimits.enabled()) {
        limiter.acquire(failKey, rule.limit(), rule.window());
      }
      throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
    }
    limiter.reset(failKey);
    User user = found;
    sessions.deleteStale(user.getId(), clock.instant().minus(STALE_SESSIONS));
    return issueTokens(user, newSession(user, userAgent));
  }

  /**
   * Troca o refresh token por um novo, na mesma sessão. Se um token já revogado for usado de novo,
   * encerra todas as sessões do usuário.
   */
  @Transactional(noRollbackFor = ApiException.class)
  public AuthResult refresh(String rawToken, String userAgent) {
    RefreshToken current = findToken(rawToken).orElseThrow(AuthService::invalidRefreshToken);
    Instant now = clock.instant();
    if (current.isRevoked()) {
      // Token de uma sessão já encerrada (logout, "sair deste dispositivo", troca de senha) não é
      // roubo: só não vale mais. Reuso de verdade = token girado numa sessão ainda ativa.
      boolean sessionEnded =
          current.getSessionId() != null
              && sessions
                  .findById(current.getSessionId())
                  .map(s -> s.getRevokedAt() != null)
                  .orElse(true);
      if (sessionEnded) {
        throw invalidRefreshToken();
      }
      log.warn("Refresh token reuse detected for user {}", current.getUserId());
      endAllSessions(current.getUserId(), null);
      throw invalidRefreshToken();
    }
    if (current.isExpired(now)) {
      current.revoke();
      throw invalidRefreshToken();
    }
    User user = users.findById(current.getUserId()).orElseThrow(AuthService::invalidRefreshToken);
    AuthSession session;
    if (current.getSessionId() == null) {
      // Token de antes da V7: ganha uma sessão agora.
      session = newSession(user, userAgent);
    } else {
      session = sessions.findById(current.getSessionId()).filter(s -> s.isActive(now)).orElse(null);
      if (session == null) {
        current.revoke();
        throw invalidRefreshToken();
      }
    }
    current.revoke();
    return issueTokens(user, session);
  }

  /** Sai deste dispositivo: revoga o token e encerra a sessão dele. */
  @Transactional
  public void logout(String rawToken) {
    findToken(rawToken)
        .ifPresent(
            token -> {
              token.revoke();
              if (token.getSessionId() != null) {
                endSession(token.getUserId(), token.getSessionId());
              }
            });
  }

  @Transactional(readOnly = true)
  public UserResponse me(UUID userId) {
    return users
        .findById(userId)
        .map(UserResponse::from)
        .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
  }

  // -------------------------------------------------------------------------
  // sessões (usadas também pela conta: troca de senha, dispositivos, exclusão)
  // -------------------------------------------------------------------------

  /** Encerra uma sessão do usuário e os tokens dela. Sessão de outro usuário = 404. */
  @Transactional
  public void endSession(UUID userId, UUID sessionId) {
    AuthSession session =
        sessions
            .findByIdAndUserId(sessionId, userId)
            .orElseThrow(() -> ApiException.notFound("Session"));
    session.revoke(clock.instant());
    refreshTokens.revokeAllForSession(sessionId);
    sessionCache.evict(sessionId);
  }

  /** Encerra todas as sessões do usuário, menos {@code keep} (null = todas, inclusive a atual). */
  @Transactional
  public void endAllSessions(UUID userId, UUID keep) {
    Instant now = clock.instant();
    if (keep == null) {
      sessions.revokeAll(userId, now);
      refreshTokens.revokeAllForUser(userId);
    } else {
      sessions.revokeAllExcept(userId, keep, now);
      refreshTokens.revokeAllForUserExcept(userId, keep);
    }
    sessionCache.evictUser(userId);
  }

  /**
   * Emite um refresh token novo para a sessão atual, revogando os anteriores dela (troca de senha:
   * o token antigo no cookie deixa de valer).
   */
  @Transactional
  public AuthResult rotateSession(User user, UUID sessionId) {
    AuthSession session =
        sessions
            .findByIdAndUserId(sessionId, user.getId())
            .filter(s -> s.isActive(clock.instant()))
            .orElseThrow(() -> ApiException.unauthorized("Session ended"));
    refreshTokens.revokeAllForSession(sessionId);
    return issueTokens(user, session);
  }

  private AuthSession newSession(User user, String userAgent) {
    Instant now = clock.instant();
    return sessions.save(
        new AuthSession(
            user.getId(), userAgent, now, now.plus(properties.jwt().refreshTokenTtl())));
  }

  private Optional<RefreshToken> findToken(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return Optional.empty();
    }
    return refreshTokens.findByTokenHash(tokenGenerator.hash(rawToken));
  }

  private AuthResult issueTokens(User user, AuthSession session) {
    String raw = tokenGenerator.newToken();
    Instant now = clock.instant();
    Instant expiresAt = now.plus(properties.jwt().refreshTokenTtl());
    refreshTokens.save(
        new RefreshToken(user.getId(), session.getId(), tokenGenerator.hash(raw), expiresAt));
    session.touch(now, expiresAt);
    String access = jwtService.issueAccessToken(user.getId(), user.getEmail(), session.getId());
    AuthResponse body =
        new AuthResponse(
            access, "Bearer", jwtService.accessTokenTtlSeconds(), UserResponse.from(user));
    return new AuthResult(body, raw);
  }

  public static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private static ApiException invalidRefreshToken() {
    return new ApiException(ErrorCode.INVALID_REFRESH_TOKEN, "Invalid or expired refresh token");
  }
}
