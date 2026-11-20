package com.stackstudy.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackstudy.support.Api;
import com.stackstudy.support.AuthTestClient;
import com.stackstudy.support.IntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Ciclo de vida pela API. Para simular o tempo passando sem esperar, os testes "voltam" os
 * timestamps no banco (o cálculo é sempre a partir deles).
 */
class SessionIntegrationTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  Api alice;
  Api bob;
  String subject;
  String task;

  @BeforeEach
  void setUp() throws Exception {
    AuthTestClient auth = new AuthTestClient(mvc, json);
    alice = new Api(mvc, json, auth.register(AuthTestClient.uniqueEmail(), "alice-password"));
    bob = new Api(mvc, json, auth.register(AuthTestClient.uniqueEmail(), "bob-password"));
    subject = alice.subject(alice.semester("2026.2"), "Anatomia");
    task = alice.create("/api/tasks", Map.of("title", "Revisar ossos")).path("id").asText();
  }

  private JsonNode start(Api api, String subjectId, String mode) throws Exception {
    return api.create("/api/deepwork/sessions", Map.of("subjectId", subjectId, "mode", mode));
  }

  /** Move o início (e a pausa, se houver) para o passado. */
  private void shift(String id, long seconds) {
    jdbc.update(
        "UPDATE study_sessions SET started_at = started_at - make_interval(secs => ?),"
            + " paused_at = paused_at - make_interval(secs => ?) WHERE id = ?::uuid",
        seconds,
        seconds,
        id);
  }

  @Test
  void fullLifecycleComputesEffectiveTimeWithoutPauses() throws Exception {
    JsonNode started =
        alice.create(
            "/api/deepwork/sessions", Map.of("subjectId", subject, "taskId", task, "mode", "FLOW"));
    String id = started.path("id").asText();
    assertThat(started.path("active").asBoolean()).isTrue();
    assertThat(started.has("serverTime")).isTrue();

    // Reabrir o app: a sessão ativa reaparece.
    alice.get("/api/deepwork/sessions/active").andExpect(jsonPath("$.id").value(id));

    // 30 min de estudo, depois pausa.
    shift(id, 1800);
    alice
        .post("/api/deepwork/sessions/" + id + "/pause")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pausedAt").exists());
    // Pausa de 10 min.
    shift(id, 600);
    JsonNode resumed = alice.read(alice.post("/api/deepwork/sessions/" + id + "/resume"));
    assertThat(resumed.path("pausedSeconds").asInt()).isCloseTo(600, within(5));
    assertThat(resumed.path("effectiveSeconds").asLong()).isCloseTo(1800L, within(5L));

    // Mais 15 min e conclui com a "commit message".
    shift(id, 900);
    JsonNode done =
        alice.read(
            alice
                .post(
                    "/api/deepwork/sessions/" + id + "/complete",
                    Map.of("note", "anatomia do membro superior", "focusRating", 4))
                .andExpect(status().isOk()));
    assertThat(done.path("active").asBoolean()).isFalse();
    assertThat(done.path("effectiveSeconds").asLong()).isCloseTo(2700L, within(5L));
    assertThat(done.path("note").asText()).isEqualTo("anatomia do membro superior");

    alice.get("/api/deepwork/sessions/active").andExpect(status().isNoContent());
    // Encerrada não pode mais ser pausada nem concluída.
    alice
        .post("/api/deepwork/sessions/" + id + "/pause")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_NOT_ACTIVE"));
    assertThat(alice.fetch("/api/deepwork/sessions").path("items").get(0).path("id").asText())
        .isEqualTo(id);
  }

  @Test
  void pomodoroUsesSettingsAndCapsAtPlannedTime() throws Exception {
    alice
        .put(
            "/api/deepwork/settings",
            Map.of(
                "focusMinutes", 50,
                "shortBreakMinutes", 10,
                "longBreakMinutes", 20,
                "longBreakEvery", 3,
                "weeklyGoalMinutes", 900,
                "soundEnabled", false,
                "notificationsEnabled", true))
        .andExpect(status().isOk());
    JsonNode started = start(alice, subject, "POMODORO");
    assertThat(started.path("plannedMinutes").asInt()).isEqualTo(50);

    // App ficou fechado 3 h depois do início: conta só os 50 min planejados.
    shift(started.path("id").asText(), 3 * 3600);
    JsonNode done =
        alice.read(
            alice.post("/api/deepwork/sessions/" + started.path("id").asText() + "/complete"));
    assertThat(done.path("effectiveSeconds").asLong()).isEqualTo(3000);
  }

  @Test
  void onlyOneActiveSessionPerUser() throws Exception {
    String first = start(alice, subject, "FLOW").path("id").asText();
    alice
        .post("/api/deepwork/sessions", Map.of("subjectId", subject, "mode", "FLOW"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_ALREADY_ACTIVE"));

    // Outro usuário pode ter a dele ao mesmo tempo.
    String bobSubject = bob.subject(bob.semester("bob"), "Contabilidade");
    start(bob, bobSubject, "FLOW");

    // Cancelar libera para iniciar outra; a cancelada não fica no histórico.
    alice.post("/api/deepwork/sessions/" + first + "/cancel").andExpect(status().isNoContent());
    start(alice, subject, "POMODORO");
    assertThat(alice.fetch("/api/deepwork/sessions").path("totalElements").asLong()).isZero();
  }

  @Test
  void manualSessionsCanBeCreatedEditedAndDeleted() throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("subjectId", subject);
    body.put("startedAt", "2026-03-10T13:00:00Z");
    body.put("durationMinutes", 90);
    body.put("note", "estudo na biblioteca");
    JsonNode created = alice.create("/api/deepwork/sessions/manual", body);
    String id = created.path("id").asText();
    assertThat(created.path("source").asText()).isEqualTo("MANUAL");
    assertThat(created.path("effectiveSeconds").asLong()).isEqualTo(5400);
    assertThat(created.path("endedAt").asText()).isEqualTo("2026-03-10T14:30:00Z");

    body.put("durationMinutes", 45);
    alice
        .put("/api/deepwork/sessions/" + id, body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.effectiveSeconds").value(2700));

    alice.delete("/api/deepwork/sessions/" + id).andExpect(status().isNoContent());
    alice.get("/api/deepwork/sessions/" + id).andExpect(status().isMethodNotAllowed());
    assertThat(alice.fetch("/api/deepwork/sessions").path("totalElements").asLong()).isZero();
  }

  @Test
  void validation() throws Exception {
    alice
        .post("/api/deepwork/sessions", Map.of("mode", "FLOW"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'subjectId')]").exists());
    alice
        .post(
            "/api/deepwork/sessions",
            Map.of("subjectId", subject, "mode", "POMODORO", "plannedMinutes", 0))
        .andExpect(status().isBadRequest());
    alice
        .post(
            "/api/deepwork/sessions/manual",
            Map.of(
                "subjectId",
                subject,
                "startedAt",
                Instant.now().plus(1, ChronoUnit.DAYS).toString(),
                "durationMinutes",
                30))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[0].field").value("startedAt"));
    alice
        .post(
            "/api/deepwork/sessions/manual",
            Map.of("subjectId", subject, "startedAt", "2026-01-01T10:00:00Z", "durationMinutes", 0))
        .andExpect(status().isBadRequest());
    alice
        .post("/api/deepwork/sessions", Map.of("subjectId", UUID.randomUUID(), "mode", "FLOW"))
        .andExpect(status().isNotFound());
    alice
        .get("/api/deepwork/heatmap?tz=Mars/Olympus")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[0].field").value("tz"));
    alice.get("/api/deepwork/heatmap?tz=%2B03:00").andExpect(status().isBadRequest());
    alice
        .get("/api/deepwork/heatmap?from=2024-01-01&to=2026-01-01")
        .andExpect(status().isBadRequest());
  }

  @Test
  void editingAnActiveSessionIsRejected() throws Exception {
    String id = start(alice, subject, "FLOW").path("id").asText();
    alice
        .put(
            "/api/deepwork/sessions/" + id,
            Map.of(
                "subjectId", subject, "startedAt", "2026-01-01T10:00:00Z", "durationMinutes", 30))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_STILL_ACTIVE"));
  }

  @Test
  void otherUserCannotSeeOrTouchSessions() throws Exception {
    String id = start(alice, subject, "FLOW").path("id").asText();
    String bobSubject = bob.subject(bob.semester("bob"), "Contabilidade");

    bob.get("/api/deepwork/sessions/active").andExpect(status().isNoContent());
    for (String action : new String[] {"pause", "resume", "complete", "cancel"}) {
      bob.post("/api/deepwork/sessions/" + id + "/" + action).andExpect(status().isNotFound());
    }
    bob.delete("/api/deepwork/sessions/" + id).andExpect(status().isNotFound());
    // Não usa matéria nem tarefa de Alice.
    bob.post("/api/deepwork/sessions", Map.of("subjectId", subject, "mode", "FLOW"))
        .andExpect(status().isNotFound());
    bob.post(
            "/api/deepwork/sessions",
            Map.of("subjectId", bobSubject, "taskId", task, "mode", "FLOW"))
        .andExpect(status().isNotFound());

    alice.post("/api/deepwork/sessions/" + id + "/complete").andExpect(status().isOk());
    bob.put(
            "/api/deepwork/sessions/" + id,
            Map.of(
                "subjectId", bobSubject, "startedAt", "2026-01-01T10:00:00Z", "durationMinutes", 5))
        .andExpect(status().isNotFound());
    assertThat(bob.fetch("/api/deepwork/sessions").path("totalElements").asLong()).isZero();
    assertThat(bob.fetch("/api/deepwork/heatmap").path("days").size()).isZero();
    assertThat(bob.fetch("/api/deepwork/stats").path("weekBySubject").size()).isZero();
    assertThat(alice.fetch("/api/deepwork/sessions").path("totalElements").asLong()).isEqualTo(1);
  }
}
