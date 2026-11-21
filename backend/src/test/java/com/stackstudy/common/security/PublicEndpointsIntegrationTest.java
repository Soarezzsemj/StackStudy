package com.stackstudy.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stackstudy.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Varre TODAS as rotas da aplicação e confere que, sem token, só as da lista abaixo respondem algo
 * diferente de 401. Rota nova que esquecer a proteção quebra este teste. Roda com o Swagger
 * desligado, como em produção.
 */
@TestPropertySource(
    properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
class PublicEndpointsIntegrationTest extends IntegrationTest {

  /** "MÉTODO caminho" que podem ser chamados sem login. */
  static final Set<String> PUBLIC =
      Set.of(
          "POST /api/auth/register",
          "POST /api/auth/login",
          "POST /api/auth/refresh",
          "POST /api/auth/logout");

  @Autowired MockMvc mvc;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping mappings;

  @Test
  void everyApplicationRouteNeedsATokenExceptAuth() throws Exception {
    List<String> checked = new ArrayList<>();
    List<String> open = new ArrayList<>();
    for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
      Set<String> patterns = info.getPatternValues();
      Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
      for (String pattern : patterns) {
        if (!pattern.startsWith("/api/")) {
          continue;
        }
        for (RequestMethod method : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
          String route = method.name() + " " + pattern;
          if (PUBLIC.contains(route)) {
            continue;
          }
          String path = pattern.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
          int status =
              mvc.perform(
                      request(HttpMethod.valueOf(method.name()), path)
                          .contentType(MediaType.APPLICATION_JSON)
                          .content("{}"))
                  .andReturn()
                  .getResponse()
                  .getStatus();
          checked.add(route);
          if (status != 401) {
            open.add(route + " -> " + status);
          }
        }
      }
    }
    assertThat(checked).as("a varredura achou as rotas").hasSizeGreaterThan(40);
    assertThat(open).as("rotas abertas sem login").isEmpty();
  }

  @Test
  void onlyLivenessAndReadinessArePublicInActuatorAndDocsAreOffInProd() throws Exception {
    mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    for (String path :
        List.of(
            "/actuator/health",
            "/actuator/health/db",
            "/actuator",
            "/actuator/info",
            "/actuator/env",
            "/v3/api-docs",
            "/swagger-ui/index.html")) {
      mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
  }

  @Test
  void apiResponsesCarrySecurityHeaders() throws Exception {
    mvc.perform(get("/api/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(
            header()
                .string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
        .andExpect(header().exists("Permissions-Policy"))
        .andExpect(
            header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
  }

  @Test
  void corsOnlyForTheConfiguredOrigin() throws Exception {
    mvc.perform(
            request(HttpMethod.OPTIONS, "/api/auth/login")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden());
    mvc.perform(
            request(HttpMethod.OPTIONS, "/api/auth/login")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
  }

  @Test
  void refreshCookieIsHttpOnlyAndScoped() throws Exception {
    String setCookie =
        mvc.perform(
                request(HttpMethod.POST, "/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"Ana\",\"email\":\"cookie-"
                            + UUID.randomUUID()
                            + "@example.com\",\"password\":\"ana-password\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getHeader("Set-Cookie");
    assertThat(setCookie)
        .contains("ss_refresh=")
        .contains("HttpOnly")
        .contains("Path=/api/auth")
        .contains("SameSite=Strict");
  }
}
