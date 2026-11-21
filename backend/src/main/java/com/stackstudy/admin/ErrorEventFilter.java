package com.stackstudy.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.Supplier;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * Registra respostas de erro da API. Fica por fora da cadeia de segurança (vê também 401, 403 e 429
 * que ela devolve). A rota é sempre o padrão do mapeamento ({@code GET /api/tasks/{id}}): se a
 * requisição não chegou ao controller, o padrão é resolvido pelo handler mapping; sem rota
 * conhecida, vira "(unmatched)". O caminho real nunca é guardado.
 */
public class ErrorEventFilter extends OncePerRequestFilter {

  private final ErrorEventRecorder recorder;

  /** Buscado na hora (o filtro nasce antes do MVC; um proxy @Lazy quebra os métodos final). */
  private final Supplier<RequestMappingHandlerMapping> mappings;

  public ErrorEventFilter(
      ErrorEventRecorder recorder, Supplier<RequestMappingHandlerMapping> mappings) {
    this.recorder = recorder;
    this.mappings = mappings;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      chain.doFilter(request, response);
    } finally {
      int status = response.getStatus();
      if (status >= 400) {
        recorder.record(request.getMethod() + " " + pattern(request), status);
      }
    }
  }

  private String pattern(HttpServletRequest request) {
    Object known = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (known instanceof String s) {
      return s;
    }
    try {
      // Não chegou ao controller (ex.: 401 da segurança): descobre o padrão sem executar nada.
      // Fora do DispatcherServlet o caminho ainda não foi analisado (exigência do PathPattern).
      if (!ServletRequestPathUtils.hasParsedRequestPath(request)) {
        ServletRequestPathUtils.parseAndCache(request);
      }
      if (mappings.get().getHandler(request) != null) {
        Object resolved = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (resolved instanceof String s) {
          return s;
        }
      }
    } catch (Exception ignored) {
      // método não suportado etc.: cai no "(unmatched)"
    }
    return "(unmatched)";
  }
}
