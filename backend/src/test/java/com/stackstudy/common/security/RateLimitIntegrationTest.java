package com.stackstudy.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackstudy.support.AuthTestClient;
import com.stackstudy.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Limites baixos só nesta classe. Cada teste usa IPs próprios (o limitador é um bean da classe
 * inteira).
 */
@TestPropertySource(
    properties = {
      "stackstudy.security.rate-limit.login.limit=3",
      "stackstudy.security.rate-limit.register.limit=2",
      "stackstudy.security.rate-limit.refresh.limit=3",
      "stackstudy.security.rate-limit.login-failures.limit=3",
      "stackstudy.security.rate-limit.password-failures.limit=2"
    })
class RateLimitIntegrationTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  private MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder request, String ip) {
    return request.with(
        r -> {
          r.setRemoteAddr(ip);
          return r;
        });
  }

  private ResultActions login(String email, String password, String ip) throws Exception {
    return mvc.perform(
        from(post("/api/auth/login"), ip)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email", email, "password", password))));
  }

  private ResultActions register(String email, String ip) throws Exception {
    return mvc.perform(
        from(post("/api/auth/register"), ip)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                json.writeValueAsString(
                    Map.of("name", "Ana", "email", email, "password", "ana-password"))));
  }

  private static String ip() {
    return "10." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250) + ".1";
  }

  @Test
  void loginIsLimitedPerIp() throws Exception {
    String ip = ip();
    for (int i = 0; i < 3; i++) {
      // E-mails diferentes: aqui o limite é o do IP, não o de falhas por e-mail.
      login(AuthTestClient.uniqueEmail(), "whatever-123", ip).andExpect(status().isUnauthorized());
    }
    login(AuthTestClient.uniqueEmail(), "whatever-123", ip)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    // Outro IP segue livre.
    login(AuthTestClient.uniqueEmail(), "whatever-123", ip()).andExpect(status().isUnauthorized());
  }

  @Test
  void registerIsLimitedPerIp() throws Exception {
    String ip = ip();
    register(AuthTestClient.uniqueEmail(), ip).andExpect(status().isCreated());
    register(AuthTestClient.uniqueEmail(), ip).andExpect(status().isCreated());
    register(AuthTestClient.uniqueEmail(), ip)
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
  }

  @Test
  void refreshIsLimitedPerIp() throws Exception {
    String ip = ip();
    for (int i = 0; i < 3; i++) {
      mvc.perform(from(post("/api/auth/refresh"), ip)).andExpect(status().isUnauthorized());
    }
    mvc.perform(from(post("/api/auth/refresh"), ip)).andExpect(status().isTooManyRequests());
  }

  @Test
  void wrongPasswordsLockTheEmailEvenFromOtherIpsAndSameAnswerForUnknownEmail() throws Exception {
    String email = AuthTestClient.uniqueEmail();
    register(email, ip()).andExpect(status().isCreated());

    // Uma falha por IP: o limite que pega é o de falhas do e-mail.
    for (int i = 0; i < 3; i++) {
      login(email, "wrong-password", ip())
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }
    // Bloqueado: nem a senha certa entra até a janela passar.
    login(email, "ana-password", ip())
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    // Outro e-mail não é afetado.
    String other = AuthTestClient.uniqueEmail();
    register(other, ip()).andExpect(status().isCreated());
    login(other, "ana-password", ip()).andExpect(status().isOk());

    // E-mail que não existe: mesma resposta que senha errada (não revela a conta).
    String unknown =
        login("nobody-" + UUID.randomUUID() + "@example.com", "x-password", ip())
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String wrong =
        login(other, "wrong-password", ip()).andReturn().getResponse().getContentAsString();
    assertThat(unknown).isEqualTo(wrong);
  }

  @Test
  void successfulLoginResetsTheFailures() throws Exception {
    String email = AuthTestClient.uniqueEmail();
    register(email, ip()).andExpect(status().isCreated());
    login(email, "wrong-password", ip()).andExpect(status().isUnauthorized());
    login(email, "wrong-password", ip()).andExpect(status().isUnauthorized());
    login(email, "ana-password", ip()).andExpect(status().isOk());
    login(email, "wrong-password", ip()).andExpect(status().isUnauthorized());
    login(email, "wrong-password", ip()).andExpect(status().isUnauthorized());
    login(email, "ana-password", ip()).andExpect(status().isOk());
  }

  @Test
  void wrongCurrentPasswordInAccountIsLimitedPerUser() throws Exception {
    String email = AuthTestClient.uniqueEmail();
    String body = register(email, ip()).andReturn().getResponse().getContentAsString();
    String token = json.readTree(body).path("accessToken").asText();
    Map<String, String> wrong =
        Map.of("currentPassword", "wrong-pass", "newPassword", "new-pass-123");
    for (int i = 0; i < 2; i++) {
      mvc.perform(
              put("/api/me/password")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json.writeValueAsString(wrong)))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(
            put("/api/me/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("currentPassword", "ana-password", "newPassword", "new-pass-123"))))
        .andExpect(status().isTooManyRequests());
  }
}
