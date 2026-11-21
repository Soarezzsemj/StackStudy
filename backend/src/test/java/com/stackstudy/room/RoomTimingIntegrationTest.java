package com.stackstudy.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackstudy.support.Api;
import com.stackstudy.support.AuthTestClient;
import com.stackstudy.support.IntegrationTest;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tempo das salas pela API, com um relógio controlado (as horas "passam" sem esperar). O token dura
 * 3 dias neste contexto para o relógio poder andar.
 */
@TestPropertySource(properties = "stackstudy.security.jwt.access-token-ttl=P3D")
class RoomTimingIntegrationTest extends IntegrationTest {

  /** Relógio que só anda quando o teste manda. */
  static final class TestClock extends Clock {
    final AtomicReference<Instant> now = new AtomicReference<>(Instant.now());

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now.get();
    }

    void advance(Duration d) {
      now.updateAndGet(i -> i.plus(d));
    }
  }

  static final TestClock CLOCK = new TestClock();

  @TestConfiguration
  static class ClockConfig {
    @Bean
    @Primary
    Clock testClock() {
      return CLOCK;
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired RoomPresence presence;
  @Autowired RoomDirectory directory;
  @MockitoSpyBean DataSource dataSource;

  AuthTestClient auth;
  AuthTestClient.Session aliceSession;
  Api alice;
  Api bob;

  @BeforeEach
  void setUp() throws Exception {
    // Cada teste começa numa quarta-feira às 06:00 UTC (o relógio só anda para a frente): "hoje"
    // e "semana" ficam previsíveis.
    Instant next = CLOCK.instant().truncatedTo(ChronoUnit.DAYS).plus(Duration.ofDays(1));
    while (next.atZone(ZoneOffset.UTC).getDayOfWeek() != DayOfWeek.WEDNESDAY) {
      next = next.plus(Duration.ofDays(1));
    }
    CLOCK.now.set(next.plus(Duration.ofHours(6)));
    auth = new AuthTestClient(mvc, json);
    aliceSession = auth.register(AuthTestClient.uniqueEmail(), "alice-password");
    alice = new Api(mvc, json, aliceSession);
    bob = new Api(mvc, json, auth.register(AuthTestClient.uniqueEmail(), "bob-password"));
  }

  // atalhos ------------------------------------------------------------------------

  String room(Api owner, String mode) throws Exception {
    return owner
        .create(
            "/api/rooms", Map.of("name", "Sala " + mode, "nickname", "Dona", "defaultMode", mode))
        .path("id")
        .asText();
  }

  String join(Api api, String roomId, String owner) throws Exception {
    String code =
        owner.equals("alice")
            ? alice.fetch("/api/rooms/" + roomId).path("inviteCode").asText()
            : null;
    api.post("/api/rooms/join", Map.of("code", code, "nickname", "Bob")).andExpect(status().isOk());
    return code;
  }

  JsonNode start(Api api, String roomId) throws Exception {
    return api.read(
        api.post("/api/rooms/" + roomId + "/session/start", Map.of()).andExpect(status().isOk()));
  }

  JsonNode heartbeat(Api api, String roomId) throws Exception {
    return api.read(
        api.post("/api/rooms/" + roomId + "/session/heartbeat").andExpect(status().isOk()));
  }

  /** Heartbeats a cada 30 s durante {@code length}; devolve a última resposta. */
  JsonNode beat(Api api, String roomId, Duration length) throws Exception {
    JsonNode last = null;
    for (long s = 0; s < length.toSeconds(); s += 30) {
      CLOCK.advance(Duration.ofSeconds(30));
      last = heartbeat(api, roomId);
    }
    return last;
  }

  JsonNode act(Api api, String roomId, String action) throws Exception {
    return api.read(
        api.post("/api/rooms/" + roomId + "/session/" + action).andExpect(status().isOk()));
  }

  Map<String, Object> row(String sessionId) {
    return jdbc.queryForMap(
        "SELECT counted_seconds, effective_seconds, ended_at, ended_by, source, last_heartbeat_at"
            + " FROM study_sessions WHERE id = ?::uuid",
        sessionId);
  }

  // testes ---------------------------------------------------------------------------

  @Test
  void theServerCountsTimeAndIgnoresAnyDurationFromTheClient() throws Exception {
    String roomId = room(alice, "FREE");
    JsonNode started = start(alice, roomId);
    assertThat(started.path("state").asText()).isEqualTo("STUDYING");
    assertThat(started.path("countedSeconds").asLong()).isZero();
    for (int i = 0; i < 3; i++) {
      CLOCK.advance(Duration.ofSeconds(30));
      // Corpo "malicioso": o endpoint não lê duração nenhuma.
      mvc.perform(
              post("/api/rooms/" + roomId + "/session/heartbeat")
                  .header(HttpHeaders.AUTHORIZATION, aliceSession.bearer())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"countedSeconds\":99999,\"durationSeconds\":99999,\"seconds\":99999}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.countedSeconds").value(30 * (i + 1)));
    }
    JsonNode stopped = act(alice, roomId, "stop");
    assertThat(stopped.path("countedSeconds").asLong()).isEqualTo(90);
    Map<String, Object> r = row(started.path("sessionId").asText());
    assertThat(r.get("effective_seconds")).isEqualTo(90);
    assertThat(r.get("ended_by")).isEqualTo("USER");
    assertThat(r.get("source")).isEqualTo("ROOM");
  }

  @Test
  void aTwelveHourSessionIsCountedInFull() throws Exception {
    String roomId = room(alice, "FREE");
    String sessionId = start(alice, roomId).path("sessionId").asText();
    for (int i = 1; i <= 12 * 120; i++) {
      CLOCK.advance(Duration.ofSeconds(30));
      heartbeat(alice, roomId);
      if (i % 120 == 0) {
        // Responde "Ainda está estudando?" a cada hora.
        act(alice, roomId, "confirm-presence");
      }
    }
    JsonNode stopped = act(alice, roomId, "stop");
    assertThat(stopped.path("countedSeconds").asLong()).isEqualTo(12 * 3600);
    assertThat(row(sessionId).get("effective_seconds")).isEqualTo(12 * 3600);

    // Entra no Deep Work uma vez só (é a mesma linha): histórico e estatística do dia.
    JsonNode history = alice.fetch("/api/deepwork/sessions");
    assertThat(history.path("items").get(0).path("id").asText()).isEqualTo(sessionId);
    assertThat(history.path("items").get(0).path("source").asText()).isEqualTo("ROOM");
    assertThat(history.path("items").get(0).path("effectiveSeconds").asLong()).isEqualTo(43200);
    JsonNode stats = alice.fetch("/api/deepwork/stats?tz=UTC");
    assertThat(stats.path("todaySeconds").asLong()).isEqualTo(43200);
  }

  @Test
  void pauseAndAutoPauseDoNotCountIdleTime() throws Exception {
    String roomId = room(alice, "FREE");
    start(alice, roomId);
    beat(alice, roomId, Duration.ofMinutes(10));
    act(alice, roomId, "pause");
    JsonNode paused = beat(alice, roomId, Duration.ofMinutes(20));
    assertThat(paused.path("state").asText()).isEqualTo("PAUSED");
    assertThat(paused.path("running").asBoolean()).isFalse();
    assertThat(paused.path("countedSeconds").asLong()).isEqualTo(600);

    act(alice, roomId, "resume");
    // Sem responder a confirmação: aos 60 min pede, aos 65 pausa sozinho.
    JsonNode asking = beat(alice, roomId, Duration.ofMinutes(61));
    assertThat(asking.path("confirmDeadline").asText()).isNotBlank();
    JsonNode auto = beat(alice, roomId, Duration.ofMinutes(5));
    assertThat(auto.path("state").asText()).isEqualTo("AUTO_PAUSED");
    assertThat(auto.path("countedSeconds").asLong()).isEqualTo(600 + 3600);
    JsonNode idle = beat(alice, roomId, Duration.ofMinutes(10));
    assertThat(idle.path("countedSeconds").asLong()).isEqualTo(600 + 3600);

    JsonNode back = act(alice, roomId, "confirm-presence");
    assertThat(back.path("state").asText()).isEqualTo("STUDYING");
    JsonNode after = beat(alice, roomId, Duration.ofMinutes(1));
    assertThat(after.path("countedSeconds").asLong()).isEqualTo(600 + 3600 + 60);
  }

  @Test
  void progressIsSavedEveryThirtyMinutesAndRebuiltAfterARestart() throws Exception {
    String roomId = room(alice, "FREE");
    String sessionId = start(alice, roomId).path("sessionId").asText();
    beat(alice, roomId, Duration.ofMinutes(29));
    assertThat(row(sessionId).get("counted_seconds")).isEqualTo(0);
    beat(alice, roomId, Duration.ofMinutes(2));
    assertThat(row(sessionId).get("counted_seconds")).isEqualTo(1800);
    assertThat(row(sessionId).get("ended_at")).isNull();

    // Reinício do servidor: a memória zera e se reconstrói no próximo heartbeat, do salvo.
    presence.clear();
    CLOCK.advance(Duration.ofSeconds(30));
    JsonNode rebuilt = heartbeat(alice, roomId);
    assertThat(rebuilt.path("sessionId").asText()).isEqualTo(sessionId);
    assertThat(rebuilt.path("countedSeconds").asLong()).isEqualTo(1800);
    JsonNode next = beat(alice, roomId, Duration.ofMinutes(1));
    assertThat(next.path("countedSeconds").asLong()).isEqualTo(1860);
  }

  @Test
  void missingHeartbeatsMakeTheMemberAwayAndThenTimeOut() throws Exception {
    String roomId = room(alice, "FREE");
    join(bob, roomId, "alice");
    String sessionId = start(alice, roomId).path("sessionId").asText();
    beat(alice, roomId, Duration.ofMinutes(5));
    Instant lastBeat = CLOCK.instant();

    CLOCK.advance(Duration.ofMinutes(3));
    JsonNode away = bob.fetch("/api/rooms/" + roomId + "/presence");
    assertThat(stateOf(away, "Dona")).isEqualTo("AWAY");
    assertThat(away.path("studyingNow").asInt()).isZero();

    CLOCK.advance(Duration.ofMinutes(15));
    JsonNode gone = bob.fetch("/api/rooms/" + roomId + "/presence");
    assertThat(stateOf(gone, "Dona")).isEqualTo("OFFLINE");
    Map<String, Object> r = row(sessionId);
    assertThat(r.get("ended_by")).isEqualTo("TIMEOUT");
    // Fica o que foi contado até o último heartbeat válido.
    assertThat(r.get("effective_seconds")).isEqualTo(300);
    assertThat(((java.sql.Timestamp) r.get("ended_at")).toInstant()).isEqualTo(lastBeat);
    alice.get("/api/deepwork/sessions/active").andExpect(status().isNoContent());
    alice.post("/api/rooms/" + roomId + "/session/heartbeat").andExpect(status().isConflict());
  }

  @Test
  void aStaleSessionWithoutMemoryIsClosedWhenTheOwnerComesBack() throws Exception {
    String roomId = room(alice, "FREE");
    String sessionId = start(alice, roomId).path("sessionId").asText();
    beat(alice, roomId, Duration.ofMinutes(31));
    presence.clear();
    CLOCK.advance(Duration.ofHours(2));
    // Deep Work encontra a sessão velha, encerra (TIMEOUT) e libera para começar outra.
    alice.get("/api/deepwork/sessions/active").andExpect(status().isNoContent());
    assertThat(row(sessionId).get("ended_by")).isEqualTo("TIMEOUT");
    assertThat(row(sessionId).get("effective_seconds")).isEqualTo(1800);
  }

  @Test
  void pomodoroPhaseComesFromTheRoomCycleAndOnlyFocusCounts() throws Exception {
    String roomId = room(alice, "POMODORO");
    join(bob, roomId, "alice");
    JsonNode started = start(alice, roomId);
    assertThat(started.path("mode").asText()).isEqualTo("POMODORO");
    JsonNode focus = beat(alice, roomId, Duration.ofMinutes(10));
    assertThat(focus.path("phase").path("phase").asText()).isEqualTo("FOCUS");
    assertThat(focus.path("phase").path("cycle").asInt()).isEqualTo(1);
    assertThat(focus.path("phase").path("of").asInt()).isEqualTo(4);
    assertThat(focus.path("running").asBoolean()).isTrue();

    JsonNode pause = beat(alice, roomId, Duration.ofMinutes(17));
    assertThat(pause.path("phase").path("phase").asText()).isEqualTo("SHORT_BREAK");
    assertThat(pause.path("running").asBoolean()).isFalse();
    assertThat(pause.path("countedSeconds").asLong()).isEqualTo(25 * 60);
    assertThat(pause.path("cyclesCompleted").asInt()).isEqualTo(1);

    // Bob vê a mesma fase (o ciclo é da sala), mesmo sem sessão.
    JsonNode bobsView = bob.fetch("/api/rooms/" + roomId + "/presence");
    assertThat(bobsView.path("phase").path("phase").asText()).isEqualTo("SHORT_BREAK");
    assertThat(bobsView.path("phase").path("endsAt").asText())
        .isEqualTo(pause.path("phase").path("endsAt").asText());

    // Rodada inteira (130 min) + 1 min do próximo foco.
    JsonNode round = beat(alice, roomId, Duration.ofMinutes(104));
    assertThat(round.path("cyclesCompleted").asInt()).isEqualTo(4);
    assertThat(round.path("countedSeconds").asLong()).isEqualTo(101 * 60);
    assertThat(round.path("phase").path("cycle").asInt()).isEqualTo(1);
  }

  @Test
  void rankingByPeriodAndMode() throws Exception {
    String roomId = room(alice, "FREE");
    join(bob, roomId, "alice");
    // Alice: 1 h ontem.
    start(alice, roomId);
    beat(alice, roomId, Duration.ofMinutes(60));
    act(alice, roomId, "stop");
    CLOCK.advance(Duration.ofHours(23));
    // Hoje: Bob 30 min, Alice 10 min.
    start(bob, roomId);
    beat(bob, roomId, Duration.ofMinutes(30));
    act(bob, roomId, "stop");
    start(alice, roomId);
    beat(alice, roomId, Duration.ofMinutes(10));

    JsonNode today = alice.fetch("/api/rooms/" + roomId + "/ranking?period=today&mode=FREE&tz=UTC");
    assertThat(today.path("entries")).hasSize(2);
    assertThat(today.path("entries").get(0).path("nickname").asText()).isEqualTo("Bob");
    assertThat(today.path("entries").get(0).path("seconds").asLong()).isEqualTo(1800);
    // A sessão em andamento entra com o valor da memória.
    assertThat(today.path("entries").get(1).path("seconds").asLong()).isEqualTo(600);
    assertThat(today.path("entries").get(1).path("me").asBoolean()).isTrue();

    JsonNode week = alice.fetch("/api/rooms/" + roomId + "/ranking?period=week&mode=FREE&tz=UTC");
    assertThat(week.path("entries").get(0).path("nickname").asText()).isEqualTo("Dona");
    assertThat(week.path("entries").get(0).path("seconds").asLong()).isEqualTo(4200);

    // Quem pede para não aparecer some do ranking.
    bob.patch("/api/rooms/" + roomId + "/me", Map.of("hideFromRanking", true))
        .andExpect(status().isOk());
    JsonNode hidden =
        alice.fetch("/api/rooms/" + roomId + "/ranking?period=today&mode=FREE&tz=UTC");
    assertThat(hidden.path("entries")).hasSize(1);

    // Pomodoro: por ciclos concluídos (Bob 2, Alice 1).
    act(alice, roomId, "stop");
    String pomo = room(alice, "POMODORO");
    join(bob, pomo, "alice");
    bob.patch("/api/rooms/" + pomo + "/me", Map.of("nickname", "Bob")).andExpect(status().isOk());
    start(bob, pomo);
    start(alice, pomo);
    for (int i = 0; i < 2 * 60; i++) {
      CLOCK.advance(Duration.ofSeconds(30));
      heartbeat(bob, pomo);
      if (i < 60) {
        heartbeat(alice, pomo);
      }
    }
    act(alice, pomo, "stop");
    JsonNode cycles =
        alice.fetch("/api/rooms/" + pomo + "/ranking?period=today&mode=POMODORO&tz=UTC");
    assertThat(cycles.path("mode").asText()).isEqualTo("POMODORO");
    assertThat(cycles.path("entries").get(0).path("nickname").asText()).isEqualTo("Bob");
    assertThat(cycles.path("entries").get(0).path("cycles").asLong()).isEqualTo(2);
    assertThat(cycles.path("entries").get(1).path("cycles").asLong()).isEqualTo(1);
    alice.get("/api/rooms/" + roomId + "/ranking?period=year").andExpect(status().isBadRequest());
  }

  @Test
  void onlyOneActiveSessionPerUserAcrossTabsRoomsAndDeepWork() throws Exception {
    String a = room(alice, "FREE");
    String b = room(alice, "FREE");
    String first = start(alice, a).path("sessionId").asText();

    // Outra aba ou outra sala: recusa com código claro, ou assume se pedir.
    alice
        .post("/api/rooms/" + b + "/session/start", Map.of())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_SESSION_ACTIVE"))
        .andExpect(jsonPath("$.details[0].message").value(a));
    alice.post("/api/rooms/" + a + "/session/start", Map.of()).andExpect(status().isConflict());
    JsonNode taken =
        alice.read(
            alice
                .post("/api/rooms/" + b + "/session/start", Map.of("takeover", true))
                .andExpect(status().isOk()));
    assertThat(row(first).get("ended_by")).isEqualTo("USER");
    assertThat(taken.path("roomId").asText()).isEqualTo(b);

    // Deep Work vê a sessão da sala, mas não a controla nem começa outra por cima.
    JsonNode active = alice.fetch("/api/deepwork/sessions/active");
    assertThat(active.path("roomId").asText()).isEqualTo(b);
    String subject = alice.subject(alice.semester("2026.2"), "Cálculo");
    alice
        .post("/api/deepwork/sessions", Map.of("subjectId", subject, "mode", "FLOW"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_ALREADY_ACTIVE"));
    alice
        .post("/api/deepwork/sessions/" + taken.path("sessionId").asText() + "/pause")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_IN_ROOM"));

    // Com uma sessão do Deep Work ativa, a sala não começa.
    act(alice, b, "stop");
    alice.create("/api/deepwork/sessions", Map.of("subjectId", subject, "mode", "FLOW"));
    alice
        .post("/api/rooms/" + a + "/session/start", Map.of("takeover", true))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SESSION_ALREADY_ACTIVE"));
  }

  @Test
  void heartbeatsAndPresenceDoNotTouchTheDatabase() throws Exception {
    String roomId = room(alice, "FREE");
    start(alice, roomId);
    // Aquece os caches (sessão do token, atividade, sala e bloqueios).
    heartbeat(alice, roomId);
    alice.fetch("/api/rooms/" + roomId + "/presence");
    clearInvocations(dataSource);

    for (int i = 0; i < 5; i++) {
      heartbeat(alice, roomId);
      alice.fetch("/api/rooms/" + roomId + "/presence");
    }
    verify(dataSource, never()).getConnection();
    verify(dataSource, never()).getConnection(anyString(), anyString());
  }

  @Test
  void subjectIsShownOnlyWhenTheMemberAllows() throws Exception {
    String roomId = room(alice, "FREE");
    join(bob, roomId, "alice");
    String subject = alice.subject(alice.semester("2026.2"), "Direito Civil");
    Map<String, Object> body = new HashMap<>();
    body.put("subjectId", subject);
    alice.post("/api/rooms/" + roomId + "/session/start", body).andExpect(status().isOk());
    JsonNode hidden = bob.fetch("/api/rooms/" + roomId + "/presence");
    assertThat(member(hidden, "Dona").has("subject")).isFalse();
    alice
        .patch("/api/rooms/" + roomId + "/me", Map.of("showSubject", true))
        .andExpect(status().isOk());
    JsonNode shown = bob.fetch("/api/rooms/" + roomId + "/presence");
    assertThat(member(shown, "Dona").path("subject").asText()).isEqualTo("Direito Civil");
    // Matéria de outro usuário não pode ser usada.
    String bobs = bob.subject(bob.semester("2026.2"), "Minha");
    bob.post("/api/rooms/" + roomId + "/session/start", Map.of("subjectId", subject))
        .andExpect(status().isNotFound());
    assertThat(bobs).isNotBlank();
  }

  static JsonNode member(JsonNode presence, String nickname) {
    for (JsonNode m : presence.path("members")) {
      if (m.path("nickname").asText().equals(nickname)) {
        return m;
      }
    }
    throw new AssertionError("member " + nickname + " not found");
  }

  static String stateOf(JsonNode presence, String nickname) {
    return member(presence, nickname).path("state").asText();
  }
}
