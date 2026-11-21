package com.stackstudy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackstudy.support.AuthTestClient;
import com.stackstudy.support.AuthTestClient.Session;
import com.stackstudy.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class AuthFlowIntegrationTest extends IntegrationTest {

  private static final String PASSWORD = "correct-horse-battery";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired RefreshTokenRepository refreshTokens;
  @Autowired RefreshTokenGenerator tokenGenerator;
  @Autowired UserRepository users;

  AuthTestClient auth;

  @BeforeEach
  void setUp() {
    auth = new AuthTestClient(mvc, json);
  }

  @Test
  void registerReturnsAccessTokenAndHttpOnlyRefreshCookie() throws Exception {
    String email = AuthTestClient.uniqueEmail();
    MvcResult result =
        mvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        auth.toJson(
                            Map.of(
                                "name",
                                "Ada",
                                "email",
                                email.toUpperCase(),
                                "password",
                                PASSWORD))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(jsonPath("$.user.email").value(email))
            .andExpect(jsonPath("$.user.locale").value("pt-BR"))
            .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
            .andReturn();

    String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(setCookie)
        .contains("ss_refresh=")
        .contains("HttpOnly")
        .contains("SameSite=Strict")
        .contains("Path=/api/auth");

    Session session = auth.toSession(result);
    // Só o hash é salvo.
    assertThat(refreshTokens.findByTokenHash(session.refreshCookie().getValue())).isEmpty();
    assertThat(
            refreshTokens.findByTokenHash(tokenGenerator.hash(session.refreshCookie().getValue())))
        .isPresent();
    // A senha é salva com BCrypt.
    assertThat(users.findByEmail(email).orElseThrow().getPasswordHash()).startsWith("$2");
  }

  @Test
  void meRequiresAValidAccessToken() throws Exception {
    Session session = auth.register(AuthTestClient.uniqueEmail(), PASSWORD);

    mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, session.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Ada Lovelace"));

    mvc.perform(get("/api/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.message").isString());

    mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void loginWithValidAndInvalidCredentials() throws Exception {
    String email = AuthTestClient.uniqueEmail();
    auth.register(email, PASSWORD);

    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(auth.toJson(Map.of("email", email, "password", PASSWORD))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isString())
        .andExpect(header().exists(HttpHeaders.SET_COOKIE));

    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(auth.toJson(Map.of("email", email, "password", "wrong-password"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void duplicateEmailIsRejected() throws Exception {
    String email = AuthTestClient.uniqueEmail();
    auth.register(email, PASSWORD);

    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    auth.toJson(Map.of("name", "Other", "email", email, "password", PASSWORD))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
  }

  @Test
  void validationErrorsUseTheStandardFormat() throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    auth.toJson(Map.of("name", "", "email", "not-an-email", "password", "short"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.details.length()").value(3))
        .andExpect(jsonPath("$.details[*].field").isArray());
  }

  @Test
  void refreshRotatesTheTokenAndDetectsReuse() throws Exception {
    Session first = auth.register(AuthTestClient.uniqueEmail(), PASSWORD);

    MvcResult refreshed =
        mvc.perform(post("/api/auth/refresh").cookie(first.refreshCookie()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isString())
            .andReturn();
    Session second = auth.toSession(refreshed);
    assertThat(second.refreshCookie().getValue()).isNotEqualTo(first.refreshCookie().getValue());

    // O novo access token funciona.
    mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, second.bearer()))
        .andExpect(status().isOk());

    // Reusar o token antigo (já revogado) falha...
    mvc.perform(post("/api/auth/refresh").cookie(first.refreshCookie()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

    // ...e, por ser reuso, também revoga o token novo.
    mvc.perform(post("/api/auth/refresh").cookie(second.refreshCookie()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void refreshWithoutOrWithUnknownCookieFails() throws Exception {
    mvc.perform(post("/api/auth/refresh"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

    mvc.perform(post("/api/auth/refresh").cookie(new Cookie("ss_refresh", "made-up")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void logoutRevokesTheRefreshTokenAndClearsTheCookie() throws Exception {
    Session session = auth.register(AuthTestClient.uniqueEmail(), PASSWORD);

    MvcResult logout =
        mvc.perform(post("/api/auth/logout").cookie(session.refreshCookie()))
            .andExpect(status().isNoContent())
            .andReturn();
    assertThat(logout.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");

    mvc.perform(post("/api/auth/refresh").cookie(session.refreshCookie()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void healthAndApiDocsArePublic() throws Exception {
    // Só os probes são públicos; o health agregado pede login (PublicEndpointsIntegrationTest).
    mvc.perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    // Em dev/teste o Swagger está ligado e é público; em produção, desligado.
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
  }
}
