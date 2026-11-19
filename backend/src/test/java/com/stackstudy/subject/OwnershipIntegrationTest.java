package com.stackstudy.subject;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackstudy.support.AuthTestClient;
import com.stackstudy.support.AuthTestClient.Session;
import com.stackstudy.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Um usuário nunca pode ver nem acessar dados de outro. */
class OwnershipIntegrationTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  Session alice;
  Session bob;
  AuthTestClient auth;

  @BeforeEach
  void setUp() throws Exception {
    auth = new AuthTestClient(mvc, json);
    alice = auth.register(AuthTestClient.uniqueEmail(), "alice-password");
    bob = auth.register(AuthTestClient.uniqueEmail(), "bob-password");
  }

  @Test
  void otherUsersSemesterIsNotFoundAndNotListed() throws Exception {
    String body =
        mvc.perform(
                post("/api/semesters")
                    .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        auth.toJson(
                            Map.of(
                                "name", "2026.2",
                                "startDate", "2026-08-01",
                                "endDate", "2026-12-15"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String semesterId = json.readTree(body).path("id").asText();

    // O dono consegue ler.
    mvc.perform(
            get("/api/semesters/" + semesterId).header(HttpHeaders.AUTHORIZATION, alice.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("2026.2"));

    // Outro usuário recebe 404 (não revela que existe)...
    mvc.perform(get("/api/semesters/" + semesterId).header(HttpHeaders.AUTHORIZATION, bob.bearer()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));

    // ...e não vê na lista dele.
    mvc.perform(get("/api/semesters").header(HttpHeaders.AUTHORIZATION, bob.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    // Sem login, o acesso é negado.
    mvc.perform(get("/api/semesters/" + semesterId)).andExpect(status().isUnauthorized());
  }

  @Test
  void invalidPeriodIsRejected() throws Exception {
    mvc.perform(
            post("/api/semesters")
                .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    auth.toJson(
                        Map.of("name", "bad", "startDate", "2026-12-01", "endDate", "2026-08-01"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }
}
