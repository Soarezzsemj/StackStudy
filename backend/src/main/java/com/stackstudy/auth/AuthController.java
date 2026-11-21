package com.stackstudy.auth;

import com.stackstudy.auth.dto.AuthResponse;
import com.stackstudy.auth.dto.LoginRequest;
import com.stackstudy.auth.dto.RegisterRequest;
import com.stackstudy.auth.email.EmailVerificationService;
import com.stackstudy.common.error.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.Map;
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
  private final RegistrationService registration;
  private final EmailVerificationService verification;
  private final RefreshCookieFactory cookies;

  public AuthController(
      AuthService authService,
      RegistrationService registration,
      EmailVerificationService verification,
      RefreshCookieFactory cookies) {
    this.authService = authService;
    this.registration = registration;
    this.verification = verification;
    this.cookies = cookies;
  }

  /**
   * Cadastro em 3 passos de uma vez. 201 + sessão; com a verificação de e-mail ligada, 202 {@code
   * {"status": "VERIFICATION_SENT"}} igual para e-mail novo e já cadastrado.
   */
  @PostMapping("/register")
  @Operation(
      summary = "Create an account (account + profile + routine + consent) and start a session")
  public ResponseEntity<?> register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
    RegistrationService.Outcome outcome = registration.register(request, userAgent(http));
    if (outcome.verificationSentOnly()) {
      return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("status", "VERIFICATION_SENT"));
    }
    return withCookie(HttpStatus.CREATED, outcome.session());
  }

  /** Confirma o e-mail pelo link (EMAIL_VERIFICATION_ENABLED) e já entra. Desligada = 404. */
  @PostMapping("/verify-email")
  @Operation(summary = "Confirm the email with the one-time token and start a session")
  public ResponseEntity<AuthResponse> verifyEmail(
      @Valid @RequestBody VerifyEmailRequest request, HttpServletRequest http) {
    if (!verification.enabled()) {
      throw ApiException.notFound("Endpoint");
    }
    User user = verification.verify(request.token());
    return withCookie(HttpStatus.OK, authService.startSession(user, userAgent(http)));
  }

  public record VerifyEmailRequest(@NotBlank @Size(max = 200) String token) {}

  @PostMapping("/login")
  @Operation(summary = "Authenticate with email and password")
  public ResponseEntity<AuthResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest http) {
    return withCookie(HttpStatus.OK, authService.login(request, userAgent(http)));
  }

  @PostMapping("/refresh")
  @Operation(summary = "Rotate the refresh-token cookie and get a new access token")
  public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
    return withCookie(
        HttpStatus.OK, authService.refresh(readRefreshCookie(request), userAgent(request)));
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

  /** User-Agent só para listar o dispositivo na tela de sessões. */
  static String userAgent(HttpServletRequest request) {
    return request.getHeader(HttpHeaders.USER_AGENT);
  }

  String readRefreshCookie(HttpServletRequest request) {
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
