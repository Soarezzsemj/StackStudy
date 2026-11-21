package com.stackstudy.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackstudy.common.error.ApiError;
import com.stackstudy.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limita login, cadastro e refresh por IP. O IP vem de {@code request.getRemoteAddr()}: em produção
 * o {@code server.forward-headers-strategy} troca pelo cliente do {@code X-Forwarded-For} (Vercel →
 * Render). Estourou: 429 {@code RATE_LIMITED} com {@code Retry-After}. Não é bean de propósito (só
 * roda dentro da cadeia de segurança).
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private final RateLimiter limiter;
  private final RateLimitProperties props;
  private final ObjectMapper json;

  public RateLimitFilter(RateLimiter limiter, RateLimitProperties props, ObjectMapper json) {
    this.limiter = limiter;
    this.props = props;
    this.json = json;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !props.enabled() || !"POST".equals(request.getMethod()) || rule(request) == null;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    RateLimitProperties.Rule rule = rule(request);
    String key = request.getRequestURI() + ":" + request.getRemoteAddr();
    RateLimiter.Decision decision = limiter.acquire(key, rule.limit(), rule.window());
    if (!decision.allowed()) {
      writeTooMany(response, decision.retryAfterSeconds());
      return;
    }
    chain.doFilter(request, response);
  }

  private RateLimitProperties.Rule rule(HttpServletRequest request) {
    return switch (request.getRequestURI()) {
      case "/api/auth/login" -> props.login();
      case "/api/auth/register" -> props.register();
      case "/api/auth/refresh" -> props.refresh();
      default -> null;
    };
  }

  private void writeTooMany(HttpServletResponse response, long retryAfter) throws IOException {
    response.setStatus(ErrorCode.RATE_LIMITED.status().value());
    response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    json.writeValue(
        response.getOutputStream(),
        ApiError.of(ErrorCode.RATE_LIMITED, "Too many attempts, try again later"));
  }
}
