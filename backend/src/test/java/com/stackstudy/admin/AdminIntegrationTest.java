package com.stackstudy.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackstudy.support.Api;
import com.stackstudy.support.AuthTestClient;
import com.stackstudy.support.AuthTestClient.Session;
import com.stackstudy.support.IntegrationTest;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Painel do admin: acesso, auditoria, papel, privacidade, agregações, limpeza e throttle. */
class AdminIntegrationTest extends IntegrationTest {

  static final List<String> ROUTES =
      List.of(
          "/api/admin/status/overview?range=7d",
          "/api/admin/status/health",
          "/api/admin/status/signups",
          "/api/admin/status/errors");

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired AdminBootstrap bootstrap;
  @Autowired MaintenanceService maintenance;
  @Autowired ActivityTracker activity;

  AuthTestClient auth;
  String adminEmail;
  Api admin;
  UUID adminId;
  String userEmail;
  Api user;
  UUID userId;

  @BeforeEach
  void setUp() throws Exception {
    auth = new AuthTestClient(mvc, json);
    adminEmail = AuthTestClient.uniqueEmail();
    Session adminSession = auth.register(adminEmail, "admin-password");
    admin = new Api(mvc, json, adminSession);
    adminId = UUID.fromString(adminSession.body().path("user").path("id").asText());
    userEmail = AuthTestClient.uniqueEmail();
    Session userSession = auth.register(userEmail, "user-password");
    user = new Api(mvc, json, userSession);
    userId = UUID.fromString(userSession.body().path("user").path("id").asText());
    bootstrap.sync(List.of(adminEmail));
  }

  @AfterEach
  void tearDown() {
    bootstrap.sync(List.of());
  }

  private String role(UUID id) {
    return jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, id);
  }

  // -------------------------------------------------------------------------
  // acesso
  // -------------------------------------------------------------------------

  @Test
  void anonymous401CommonUser403Admin200OnEveryRouteAndEveryAccessIsLogged() throws Exception {
    for (String route : ROUTES) {
      mvc.perform(get(route)).andExpect(status().isUnauthorized());
      user.get(route)
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("FORBIDDEN"));
      admin.get(route).andExpect(status().isOk());
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM admin_access_log WHERE user_id = ? AND allowed",
                Integer.class,
                adminId))
        .isEqualTo(4);
    // A tentativa negada também fica registrada, com a rota-modelo.
    assertThat(
            jdbc.queryForList(
                "SELECT endpoint FROM admin_access_log WHERE user_id = ? AND NOT allowed",
                String.class,
                userId))
        .containsExactlyInAnyOrder(
            "GET /api/admin/status/overview",
            "GET /api/admin/status/health",
            "GET /api/admin/status/signups",
            "GET /api/admin/status/errors");
    // O perfil devolve o papel (o frontend decide se mostra o link).
    assertThat(admin.fetch("/api/me").path("role").asText()).isEqualTo("ADMIN");
    assertThat(user.fetch("/api/me").path("role").asText()).isEqualTo("USER");
  }

  @Test
  void roleIsReadFromTheDatabaseOnEveryRequest() throws Exception {
    admin.get("/api/admin/status/health").andExpect(status().isOk());
    // Rebaixado (variável mudou e o app reiniciou): o mesmo token deixa de abrir o painel.
    bootstrap.sync(List.of());
    admin.get("/api/admin/status/health").andExpect(status().isForbidden());
  }

  // -------------------------------------------------------------------------
  // papel
  // -------------------------------------------------------------------------

  @Test
  void promotionOnlyViaAdminEmailsForExistingAccounts() throws Exception {
    String future = AuthTestClient.uniqueEmail();
    // O e-mail está na lista antes de a conta existir: nada acontece...
    int[] result = bootstrap.sync(List.of(adminEmail, future));
    assertThat(result[0]).isZero();
    // ...e quem se cadastra com ele não vira admin no cadastro.
    Session late = auth.register(future, "late-password");
    assertThat(late.body().path("user").path("role").asText()).isEqualTo("USER");
    UUID lateId = UUID.fromString(late.body().path("user").path("id").asText());
    assertThat(role(lateId)).isEqualTo("USER");
    // Só na próxima inicialização (sync), e só porque a conta agora existe.
    assertThat(bootstrap.sync(List.of(adminEmail, future))[0]).isEqualTo(1);
    assertThat(role(lateId)).isEqualTo("ADMIN");
    // E-mail com maiúsculas/espaços na variável também casa (normalizado).
    bootstrap.sync(
        new AdminProperties(List.of(" " + userEmail.toUpperCase() + " "), null, null).emails());
    assertThat(role(userId)).isEqualTo("ADMIN");
    assertThat(role(adminId)).isEqualTo("USER");
  }

  private static Map<String, Object> withRole(Map<String, Object> body) {
    body.put("role", "ADMIN");
    return body;
  }

  @Test
  void noRouteCanChangeTheRole() throws Exception {
    // Cadastro com "role": ignorado.
    String email = AuthTestClient.uniqueEmail();
    String body =
        mvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            withRole(AuthTestClient.body("Eve", email, "eve-password")))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(json.readTree(body).path("user").path("role").asText()).isEqualTo("USER");
    Api eve = new Api(mvc, json, auth.toSession(mvcLogin(email, "eve-password")));

    Map<String, Object> profile = new HashMap<>();
    profile.put("name", "Eve");
    profile.put("role", "ADMIN");
    eve.put("/api/me/profile", profile).andExpect(status().isOk());
    eve.patch("/api/me/preferences", Map.of("role", "ADMIN", "locale", "en"))
        .andExpect(status().isOk());
    eve.put(
            "/api/me/email",
            Map.of("email", email, "currentPassword", "eve-password", "role", "ADMIN"))
        .andExpect(status().isOk());

    UUID eveId = UUID.fromString(eve.fetch("/api/me").path("id").asText());
    assertThat(role(eveId)).isEqualTo("USER");
    eve.get("/api/admin/status/health").andExpect(status().isForbidden());
  }

  private org.springframework.test.web.servlet.MvcResult mvcLogin(String email, String password)
      throws Exception {
    return mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password))))
        .andExpect(status().isOk())
        .andReturn();
  }

  // -------------------------------------------------------------------------
  // privacidade
  // -------------------------------------------------------------------------

  @Test
  void signupsAreMaskedAndNoResponseLeaksPersonalData() throws Exception {
    user.put("/api/me/profile", Map.of("name", "Fulana de Tal Completa", "course", "Direito"))
        .andExpect(status().isOk());
    JsonNode signups = admin.fetch("/api/admin/status/signups");
    assertThat(signups.size()).isBetween(2, 20);
    boolean found = false;
    for (JsonNode s : signups) {
      List<String> fields = new ArrayList<>();
      s.fieldNames().forEachRemaining(fields::add);
      assertThat(fields).isSubsetOf("email", "course", "createdAt");
      assertThat(s.path("email").asText()).matches("^[^*@]{1,2}\\*\\*\\*@.+$");
      if (s.path("email").asText().equals(userEmail.substring(0, 2) + "***@example.com")
          && "Direito".equals(s.path("course").asText())) {
        found = true;
      }
    }
    assertThat(found).isTrue();

    // Nenhuma resposta do painel traz e-mail completo, nome, id ou User-Agent.
    StringBuilder all = new StringBuilder();
    for (String route : ROUTES) {
      all.append(admin.get(route).andReturn().getResponse().getContentAsString());
    }
    String text = all.toString();
    assertThat(text)
        .doesNotContain(userEmail)
        .doesNotContain(adminEmail)
        .doesNotContain("Fulana")
        .doesNotContain(userId.toString())
        .doesNotContain(adminId.toString())
        .doesNotContain("passwordHash")
        .doesNotContain("userAgent");
  }

  // -------------------------------------------------------------------------
  // agregações
  // -------------------------------------------------------------------------

  /** Usuário criado direto no banco, com data de cadastro escolhida. */
  private UUID seedUser(Instant createdAt) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, name, email, password_hash, created_at) VALUES (?, 'x', ?, 'h', ?)",
        id,
        "seed-" + id + "@example.com",
        Timestamp.from(createdAt));
    return id;
  }

  private void activityOn(UUID id, LocalDate day) {
    jdbc.update(
        "INSERT INTO user_activity_daily (user_id, day) VALUES (?, ?) ON CONFLICT DO NOTHING",
        id,
        Date.valueOf(day));
  }

  private UUID seedSubject(UUID userId) {
    UUID semester = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO semesters (id, user_id, name, start_date, end_date) VALUES (?, ?, 's', DATE"
            + " '2026-01-01', DATE '2026-12-31')",
        semester,
        userId);
    UUID subject = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO subjects (id, semester_id, name, color) VALUES (?, ?, 'm', '#6aa7ff')",
        subject,
        semester);
    return subject;
  }

  private void seedFocus(UUID userId, UUID subject, Instant started, int seconds) {
    jdbc.update(
        "INSERT INTO study_sessions (user_id, subject_id, started_at, ended_at, mode,"
            + " effective_seconds, source) VALUES (?, ?, ?, ?, 'FLOW', ?, 'MANUAL')",
        userId,
        subject,
        Timestamp.from(started),
        Timestamp.from(started.plusSeconds(seconds)),
        seconds);
  }

  @Test
  void aggregationsCountDauWauRetentionFunnelAndUsage() throws Exception {
    // As chamadas do próprio admin também contam como atividade: registra antes da base.
    admin.fetch("/api/me");
    JsonNode before = admin.fetch("/api/admin/status/overview?range=30d");

    Instant now = Instant.now();
    LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
    Instant tenDaysAgo = now.minus(10, ChronoUnit.DAYS);
    LocalDate signupDay = LocalDate.ofInstant(tenDaysAgo, ZoneOffset.UTC);

    // Coorte de 10 dias atrás: 3 voltaram em até 7 dias, 1 só no dia 8 (não conta).
    UUID a = seedUser(tenDaysAgo);
    UUID b = seedUser(tenDaysAgo);
    UUID c = seedUser(tenDaysAgo);
    UUID d = seedUser(tenDaysAgo);
    activityOn(a, signupDay.plusDays(1));
    activityOn(b, signupDay.plusDays(7));
    activityOn(c, signupDay.plusDays(3));
    activityOn(d, signupDay.plusDays(8));
    // Hoje: a e b ativos (DAU +2); semana: a, b e c (c há 3 dias); d há 10 dias (fora da WAU).
    activityOn(a, today);
    activityOn(b, today);
    activityOn(c, today.minusDays(3));
    // Ontem: só d (DAU anterior +1).
    activityOn(d, today.minusDays(1));
    // Funil: a criou matéria e fez foco esta semana (uso: Deep Work); b só matéria.
    UUID subjectA = seedSubject(a);
    seedSubject(b);
    seedFocus(a, subjectA, now.minus(1, ChronoUnit.DAYS), 7200);

    JsonNode after = admin.fetch("/api/admin/status/overview?range=30d");
    assertThat(after.path("range").asText()).isEqualTo("30d");
    assertThat(delta(before, after, "totalUsers")).isEqualTo(4);
    assertThat(delta(before, after, "newUsers", "value")).isEqualTo(4);
    assertThat(delta(before, after, "dailyActive", "value")).isEqualTo(2);
    assertThat(delta(before, after, "dailyActive", "previous")).isEqualTo(1);
    assertThat(delta(before, after, "weeklyActive", "value")).isEqualTo(4);
    assertThat(delta(before, after, "retentionD7", "cohort")).isEqualTo(4);
    assertThat(delta(before, after, "retentionD7", "retained")).isEqualTo(3);
    assertThat(delta(before, after, "funnel", "registered")).isEqualTo(4);
    assertThat(delta(before, after, "funnel", "firstSubject")).isEqualTo(2);
    assertThat(delta(before, after, "funnel", "firstFocusSession")).isEqualTo(1);
    assertThat(delta(before, after, "funnel", "returned7d")).isEqualTo(3);
    assertThat(delta(before, after, "usage", "weeklyActive")).isEqualTo(4);
    assertThat(delta(before, after, "usage", "deepWork")).isEqualTo(1);
    assertThat(after.path("focusHoursPerActive").asDouble()).isPositive();

    // Série diária de 30 dias com os cadastros do dia certo e os ativos de hoje.
    JsonNode series = after.path("series");
    assertThat(series).hasSize(30);
    assertThat(series.get(29).path("day").asText()).isEqualTo(today.toString());
    long signupsThatDay = 0;
    long signupsBefore = 0;
    for (int i = 0; i < 30; i++) {
      if (series.get(i).path("day").asText().equals(signupDay.toString())) {
        signupsThatDay = series.get(i).path("signups").asLong();
        signupsBefore = before.path("series").get(i).path("signups").asLong();
      }
    }
    assertThat(signupsThatDay - signupsBefore).isEqualTo(4);

    admin.get("/api/admin/status/overview?range=1y").andExpect(status().isBadRequest());
    assertThat(admin.fetch("/api/admin/status/overview?range=24h").path("series")).hasSize(7);
  }

  private static long delta(JsonNode before, JsonNode after, String... path) {
    JsonNode b = before;
    JsonNode a = after;
    for (String p : path) {
      b = b.path(p);
      a = a.path(p);
    }
    return a.asLong() - b.asLong();
  }

  @Test
  void activityDayIsUtcWhateverTheUserTimeZone() throws Exception {
    // Kiritimati = UTC+14: o dia local quase sempre é outro, mas a métrica usa o dia UTC.
    user.patch("/api/me/preferences", Map.of("timeZone", "Pacific/Kiritimati"))
        .andExpect(status().isOk());
    jdbc.update("DELETE FROM user_activity_daily WHERE user_id = ?", userId);
    activity.forget(userId);
    user.fetch("/api/me");
    assertThat(
            jdbc.queryForList(
                "SELECT day FROM user_activity_daily WHERE user_id = ?", LocalDate.class, userId))
        .containsExactly(LocalDate.now(ZoneOffset.UTC));
  }

  @Test
  void lastSeenIsWrittenAtMostOncePerMinute() throws Exception {
    activity.forget(userId);
    user.fetch("/api/me");
    Timestamp first =
        jdbc.queryForObject("SELECT last_seen_at FROM users WHERE id = ?", Timestamp.class, userId);
    assertThat(first).isNotNull();
    // Apaga e faz mais requisições logo em seguida: nenhuma grava de novo (throttle de 1 min).
    jdbc.update("UPDATE users SET last_seen_at = NULL WHERE id = ?", userId);
    for (int i = 0; i < 5; i++) {
      user.fetch("/api/me");
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT last_seen_at FROM users WHERE id = ?", Timestamp.class, userId))
        .isNull();
    // "Online agora" conta quem foi visto nos últimos 5 min.
    activity.forget(userId);
    user.fetch("/api/me");
    assertThat(admin.fetch("/api/admin/status/overview").path("onlineNow").asLong()).isPositive();
  }

  // -------------------------------------------------------------------------
  // erros e saúde
  // -------------------------------------------------------------------------

  @Test
  void errorsAreAggregatedByRoutePatternWithoutRealPaths() throws Exception {
    String secretId = UUID.randomUUID().toString();
    for (int i = 0; i < 3; i++) {
      mvc.perform(get("/api/tasks/" + secretId)).andExpect(status().isUnauthorized());
    }
    user.get("/api/tasks/" + secretId).andExpect(status().isNotFound());
    JsonNode errors = admin.fetch("/api/admin/status/errors");
    assertThat(errors.toString()).doesNotContain(secretId);
    long unauthorized = 0;
    long notFound = 0;
    for (JsonNode e : errors) {
      if (e.path("route").asText().equals("GET /api/tasks/{id}")) {
        if (e.path("status").asInt() == 401) {
          unauthorized = e.path("count").asLong();
        }
        if (e.path("status").asInt() == 404) {
          notFound = e.path("count").asLong();
        }
        assertThat(e.path("lastSeenAt").asText()).isNotBlank();
      }
    }
    assertThat(unauthorized).isGreaterThanOrEqualTo(3);
    assertThat(notFound).isGreaterThanOrEqualTo(1);
  }

  @Test
  void healthReportsOnlyWhatTheAppMeasures() throws Exception {
    JsonNode h = admin.fetch("/api/admin/status/health");
    assertThat(h.path("dbUp").asBoolean()).isTrue();
    assertThat(h.path("dbSizeBytes").asLong()).isPositive();
    assertThat(h.path("postgresVersion").asText()).startsWith("16");
    assertThat(h.path("uptimeSeconds").asLong()).isGreaterThanOrEqualTo(0);
    assertThat(h.path("heapUsedBytes").asLong()).isPositive();
    assertThat(h.path("startsLast24h").asLong()).isGreaterThanOrEqualTo(1);
    // Sem ADMIN_LINK_* configurado: links ausentes (nada inventado).
    assertThat(h.path("links").path("render").isMissingNode()).isTrue();
  }

  // -------------------------------------------------------------------------
  // limpeza
  // -------------------------------------------------------------------------

  @Test
  void oldDataIsCleanedOpportunisticallyAtMostOncePerDay() {
    Instant now = Instant.now();
    jdbc.update(
        "INSERT INTO error_events (route, status, hour, count, last_seen_at) VALUES ('GET /old',"
            + " 500, ?, 1, ?), ('GET /recent', 500, ?, 1, ?)",
        Timestamp.from(now.minus(8, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS)),
        Timestamp.from(now.minus(8, ChronoUnit.DAYS)),
        Timestamp.from(now.minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS)),
        Timestamp.from(now.minus(1, ChronoUnit.DAYS)));
    jdbc.update(
        "INSERT INTO admin_access_log (user_id, endpoint, allowed, accessed_at) VALUES (?, 'old',"
            + " true, ?), (?, 'recent', true, ?)",
        adminId,
        Timestamp.from(now.minus(91, ChronoUnit.DAYS)),
        adminId,
        Timestamp.from(now.minus(89, ChronoUnit.DAYS)));
    jdbc.update(
        "INSERT INTO app_starts (started_at) VALUES (?)",
        Timestamp.from(now.minus(8, ChronoUnit.DAYS)));
    // Última limpeza ontem: a próxima escrita dispara a de hoje.
    jdbc.update(
        "UPDATE maintenance_runs SET last_run_at = ? WHERE task = 'daily-cleanup'",
        Timestamp.from(now.minus(1, ChronoUnit.DAYS)));
    maintenance.resetForTests();
    maintenance.runDailyIfDue();

    assertThat(count("SELECT count(*) FROM error_events WHERE route = 'GET /old'")).isZero();
    assertThat(count("SELECT count(*) FROM error_events WHERE route = 'GET /recent'")).isEqualTo(1);
    assertThat(count("SELECT count(*) FROM admin_access_log WHERE endpoint = 'old'")).isZero();
    assertThat(count("SELECT count(*) FROM admin_access_log WHERE endpoint = 'recent'"))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app_starts WHERE started_at < ?",
                Integer.class,
                Timestamp.from(now.minus(7, ChronoUnit.DAYS))))
        .isZero();

    // No mesmo dia não roda de novo, mesmo em outra instância (trava no banco).
    jdbc.update(
        "INSERT INTO error_events (route, status, hour, count, last_seen_at) VALUES ('GET /old2',"
            + " 500, ?, 1, ?)",
        Timestamp.from(now.minus(9, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS)),
        Timestamp.from(now.minus(9, ChronoUnit.DAYS)));
    maintenance.resetForTests();
    maintenance.runDailyIfDue();
    assertThat(count("SELECT count(*) FROM error_events WHERE route = 'GET /old2'")).isEqualTo(1);
    jdbc.update("DELETE FROM error_events WHERE route LIKE 'GET /%'");
  }

  private int count(String sql) {
    return jdbc.queryForObject(sql, Integer.class);
  }
}
