package com.stackstudy.auth;

import com.stackstudy.auth.dto.AuthResponse;
import com.stackstudy.auth.dto.LoginRequest;
import com.stackstudy.auth.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Arrays;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth")
@SecurityRequirements
public class AuthController {

  private final AuthService authService;
  private final RefreshCookieFactory cookies;

  public AuthController(AuthService authService, RefreshCookieFactory cookies) {
    this.authService = authService;
    this.cookies = cookies;
  }

  @PostMapping("/register")
  @Operation(summary = "Create an account and start a session")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    return withCookie(HttpStatus.CREATED, authService.register(request));
  }

  @PostMapping("/login")
  @Operation(summary = "Authenticate with email and password")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    return withCookie(HttpStatus.OK, authService.login(request));
  }

  @PostMapping("/refresh")
  @Operation(summary = "Rotate the refresh-token cookie and get a new access token")
  public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
    return withCookie(HttpStatus.OK, authService.refresh(readRefreshCookie(request)));
  }

  @PostMapping("/logout")
  @Operation(summary = "Revoke the current refresh token and clear the cookie")
  public ResponseEntity<Void> logout(HttpServletRequest request) {
    authService.logout(readRefreshCookie(request));
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
        .build();
  }

  private ResponseEntity<AuthResponse> withCookie(
      HttpStatus status, AuthService.AuthResult result) {
    return ResponseEntity.status(status)
        .header(HttpHeaders.SET_COOKIE, cookies.create(result.refreshToken()).toString())
        .body(result.body());
  }

  private String readRefreshCookie(HttpServletRequest request) {
    Cookie[] all = request.getCookies();
    if (all == null) {
      return null;
    }
    return Arrays.stream(all)
        .filter(c -> cookies.cookieName().equals(c.getName()))
        .map(Cookie::getValue)
        .findFirst()
        .orElse(null);
  }
}
