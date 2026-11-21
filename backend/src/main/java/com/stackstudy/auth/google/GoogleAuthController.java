package com.stackstudy.auth.google;

import com.stackstudy.auth.RefreshCookieFactory;
import com.stackstudy.auth.SignupProperties;
import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.security.SecurityProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Arrays;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login com Google. {@code AUTH_GOOGLE_ENABLED=false} (padrão): as duas rotas respondem 404 e o
 * botão não aparece na tela ({@code GET /api/auth/config}).
 */
@RestController
@RequestMapping("/api/auth/google")
@Tag(name = "Auth")
@SecurityRequirements
public class GoogleAuthController {

  static final String FLOW_COOKIE = "ss_google_flow";
  static final String FLOW_PATH = "/api/auth/google";

  private final GoogleAuthService google;
  private final RefreshCookieFactory cookies;
  private final SignupProperties signup;
  private final SecurityProperties security;

  public GoogleAuthController(
      GoogleAuthService google,
      RefreshCookieFactory cookies,
      SignupProperties signup,
      SecurityProperties security) {
    this.google = google;
    this.cookies = cookies;
    this.signup = signup;
    this.security = security;
  }

  @GetMapping("/start")
  @Operation(summary = "Redirect to Google (OIDC + PKCE); 404 when disabled")
  public ResponseEntity<Void> start() {
    requireEnabled();
    GoogleAuthService.Start start = google.start();
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(start.redirectUrl()))
        .header(HttpHeaders.SET_COOKIE, flowCookie(start.flowCookie(), 600).toString())
        .build();
  }

  @GetMapping("/callback")
  @Operation(summary = "Google redirect target: signs in a linked or verified account")
  public ResponseEntity<Void> callback(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      HttpServletRequest request) {
    requireEnabled();
    String flow =
        request.getCookies() == null
            ? null
            : Arrays.stream(request.getCookies())
                .filter(c -> FLOW_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    GoogleAuthService.Callback result =
        google.callback(code, state, flow, request.getHeader(HttpHeaders.USER_AGENT));
    String target =
        switch (result.result()) {
          case SIGNED_IN -> "/?login=google";
          case NO_ACCOUNT -> "/signup?google=no-account";
          case UNVERIFIED_EMAIL -> "/login?google=unverified";
          case ERROR -> "/login?google=error";
        };
    ResponseEntity.BodyBuilder response =
        ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(signup.appUrl() + target))
            .header(HttpHeaders.SET_COOKIE, flowCookie("", 0).toString());
    if (result.session() != null) {
      // O app restaura a sessão pelo cookie de refresh (useSessionBootstrap).
      response.header(
          HttpHeaders.SET_COOKIE, cookies.create(result.session().refreshToken()).toString());
    }
    return response.build();
  }

  private void requireEnabled() {
    if (!google.enabled()) {
      throw ApiException.notFound("Endpoint");
    }
  }

  /** Lax (e não Strict): o cookie precisa voltar no redirecionamento vindo do Google. */
  private ResponseCookie flowCookie(String value, long maxAgeSeconds) {
    return ResponseCookie.from(FLOW_COOKIE, value)
        .httpOnly(true)
        .secure(security.refreshCookie().secure())
        .sameSite("Lax")
        .path(FLOW_PATH)
        .maxAge(maxAgeSeconds)
        .build();
  }
}
