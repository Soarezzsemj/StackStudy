package com.stackstudy.auth;

import com.stackstudy.auth.dto.AuthResponse;
import com.stackstudy.auth.dto.LoginRequest;
import com.stackstudy.auth.dto.RegisterRequest;
import com.stackstudy.auth.dto.UserResponse;
import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.error.ErrorCode;
import com.stackstudy.common.security.JwtService;
import com.stackstudy.common.security.SecurityProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);
  private static final String DEFAULT_LOCALE = "pt-BR";

  /** Refresh token puro (vai no cookie) e o corpo da resposta. */
  public record AuthResult(AuthResponse body, String refreshToken) {}

  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final RefreshTokenGenerator tokenGenerator;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final SecurityProperties properties;
  private final Clock clock;

  public AuthService(
      UserRepository users,
      RefreshTokenRepository refreshTokens,
      RefreshTokenGenerator tokenGenerator,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      SecurityProperties properties,
      Clock clock) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.tokenGenerator = tokenGenerator;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional
  public AuthResult register(RegisterRequest request) {
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
    return issueTokens(user);
  }

  @Transactional
  public AuthResult login(LoginRequest request) {
    User user =
        users
            .findByEmail(normalizeEmail(request.email()))
            .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
            .orElseThrow(
                () -> new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password"));
    return issueTokens(user);
  }

  /**
   * Troca o refresh token por um novo. Se um token já revogado for usado de novo, revoga todos os
   * tokens do usuário.
   */
  @Transactional(noRollbackFor = ApiException.class)
  public AuthResult refresh(String rawToken) {
    RefreshToken current = findToken(rawToken).orElseThrow(AuthService::invalidRefreshToken);
    Instant now = clock.instant();
    if (current.isRevoked()) {
      log.warn("Refresh token reuse detected for user {}", current.getUserId());
      refreshTokens.revokeAllForUser(current.getUserId());
      throw invalidRefreshToken();
    }
    if (current.isExpired(now)) {
      current.revoke();
      throw invalidRefreshToken();
    }
    current.revoke();
    User user = users.findById(current.getUserId()).orElseThrow(AuthService::invalidRefreshToken);
    return issueTokens(user);
  }

  @Transactional
  public void logout(String rawToken) {
    findToken(rawToken).ifPresent(RefreshToken::revoke);
  }

  @Transactional(readOnly = true)
  public UserResponse me(java.util.UUID userId) {
    return users
        .findById(userId)
        .map(UserResponse::from)
        .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
  }

  private java.util.Optional<RefreshToken> findToken(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return java.util.Optional.empty();
    }
    return refreshTokens.findByTokenHash(tokenGenerator.hash(rawToken));
  }

  private AuthResult issueTokens(User user) {
    String raw = tokenGenerator.newToken();
    Instant expiresAt = clock.instant().plus(properties.jwt().refreshTokenTtl());
    refreshTokens.save(new RefreshToken(user.getId(), tokenGenerator.hash(raw), expiresAt));
    String access = jwtService.issueAccessToken(user.getId(), user.getEmail());
    AuthResponse body =
        new AuthResponse(
            access, "Bearer", jwtService.accessTokenTtlSeconds(), UserResponse.from(user));
    return new AuthResult(body, raw);
  }

  private static String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private static ApiException invalidRefreshToken() {
    return new ApiException(ErrorCode.INVALID_REFRESH_TOKEN, "Invalid or expired refresh token");
  }
}
