package com.stackstudy.auth;

import com.stackstudy.common.security.SecurityProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Monta o cookie do refresh token (HttpOnly, SameSite, só em {@code /api/auth}). */
@Component
public class RefreshCookieFactory {

  private final SecurityProperties properties;

  public RefreshCookieFactory(SecurityProperties properties) {
    this.properties = properties;
  }

  public String cookieName() {
    return properties.refreshCookie().name();
  }

  public ResponseCookie create(String rawToken) {
    return base(rawToken).maxAge(properties.jwt().refreshTokenTtl()).build();
  }

  public ResponseCookie clear() {
    return base("").maxAge(Duration.ZERO).build();
  }

  private ResponseCookie.ResponseCookieBuilder base(String value) {
    SecurityProperties.RefreshCookie cookie = properties.refreshCookie();
    return ResponseCookie.from(cookie.name(), value)
        .httpOnly(true)
        .secure(cookie.secure())
        .sameSite(cookie.sameSite())
        .path(cookie.path());
  }
}
