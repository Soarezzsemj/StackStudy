package com.stackstudy.account;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Perfil, preferências, e-mail, senha, sessões, exportação e exclusão da conta. */
class AccountIntegrationTest extends IntegrationTest {

  private static final String PASSWORD = "alice-password";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  AuthTestClient auth;
  String aliceEmail;
  Session aliceSession;
  Api alice;
  Session bobSession;
  Api bob;

  @BeforeEach
  void setUp() throws Exception {
    auth = new AuthTestClient(mvc, json);
    aliceEmail = AuthTestClient.uniqueEmail();
    aliceSession = auth.register(aliceEmail, PASSWORD);
    alice = new Api(mvc, json, aliceSession);
    bobSession = auth.register(AuthTestClient.uniqueEmail(), "bob-password");
    bob = new Api(mvc, json, bobSession);
  }

  private void meStatus(Session session, int expected) throws Exception {
    mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, session.bearer()))
        .andExpect(status().is(expected));
  }

  private void refreshStatus(Session session, int expected) throws Exception {
    mvc.perform(post("/api/auth/refresh").cookie(session.refreshCookie()))
        .andExpect(status().is(expected));
  }

  // -------------------------------------------------------------------------
  // perfil
  // -------------------------------------------------------------------------

  @Test
  void profileUpdateAndValidation() throws Exception {
    Map<String, Object> profile = new HashMap<>();
    profile.put("name", "  Alice Silva ");
    profile.put("course", "Direito");
    profile.put("institution", "Universidade Exemplo");
    profile.put("currentPeriod", "3º período");
    profile.put("bio", "Estudando para a OAB.");
    profile.put("avatarColor", "#BD6CF7");
    JsonNode saved = alice.read(alice.put("/api/me/profile", profile).andExpect(status().isOk()));
    assertThat(saved.path("name").asText()).isEqualTo("Alice Silva");
    assertThat(saved.path("avatarColor").asText()).isEqualTo("#bd6cf7");

    JsonNode me = alice.fetch("/api/me");
    assertThat(me.path("course").asText()).isEqualTo("Direito");
    assertThat(me.path("currentPeriod").asText()).isEqualTo("3º período");
    // Nada de senha/hash na resposta.
    assertThat(me.toString()).doesNotContain("password").doesNotContain("$2a$");

    // Vazio vira "sem valor".
    profile.put("course", " ");
    assertThat(alice.read(alice.put("/api/me/profile", profile)).path("course").isMissingNode())
        .isTrue();

    Map<String, Object> bad = new HashMap<>(profile);
    bad.put("name", "");
    bad.put("avatarColor", "#123456");
    bad.put("bio", "x".repeat(281));
    alice
        .put("/api/me/profile", bad)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'name')]").exists())
        .andExpect(jsonPath("$.details[?(@.field == 'avatarColor')]").exists())
        .andExpect(jsonPath("$.details[?(@.field == 'bio')]").exists());

    // O perfil de Bob não mudou.
    assertThat(bob.fetch("/api/me").path("course").isMissingNode()).isTrue();
  }

  // -------------------------------------------------------------------------
  // preferências
  // -------------------------------------------------------------------------

  @Test
  void preferencesAreTheDefaultsTheOtherModulesUse() throws Exception {
    JsonNode defaults = alice.fetch("/api/me/preferences");
    assertThat(defaults.path("locale").asText()).isEqualTo("pt-BR");
    assertThat(defaults.path("timeZone").isMissingNode()).isTrue();
    assertThat(defaults.path("focusMinutes").asInt()).isEqualTo(25);
    assertThat(defaults.path("gradeMethod").asText()).isEqualTo("WEIGHTED");
    assertThat(defaults.path("newCardsPerDay").asInt()).isEqualTo(20);

    Map<String, Object> patch = new HashMap<>();
    patch.put("locale", "en");
    patch.put("timeZone", "America/Manaus");
    patch.put("gradeMethod", "SUM");
    patch.put("passingGrade", 7);
    patch.put("focusMinutes", 50);
    patch.put("weeklyGoalMinutes", 900);
    patch.put("reviewsPerDay", 150);
    JsonNode saved =
        alice.read(alice.patch("/api/me/preferences", patch).andExpect(status().isOk()));
    assertThat(saved.path("timeZone").asText()).isEqualTo("America/Manaus");
    // Campos não enviados ficam como estavam.
    assertThat(saved.path("shortBreakMinutes").asInt()).isEqualTo(5);
    assertThat(saved.path("newCardsPerDay").asInt()).isEqualTo(20);

    // As outras fases leem os mesmos valores (sem duplicar nada).
    assertThat(alice.fetch("/api/deepwork/settings").path("focusMinutes").asInt()).isEqualTo(50);
    assertThat(alice.fetch("/api/deepwork/settings").path("weeklyGoalMinutes").asInt())
        .isEqualTo(900);
    JsonNode grades = alice.fetch("/api/grades/settings");
    assertThat(grades.path("method").asText()).isEqualTo("SUM");
    assertThat(grades.path("passingGrade").decimalValue()).isEqualByComparingTo("7");
    assertThat(alice.fetch("/api/flashcards/settings").path("reviewsPerDay").asInt())
        .isEqualTo(150);
    assertThat(alice.fetch("/api/me").path("locale").asText()).isEqualTo("en");
    // Bob continua com os padrões.
    assertThat(bob.fetch("/api/deepwork/settings").path("focusMinutes").asInt()).isEqualTo(25);

    // Fuso vazio volta ao do navegador.
    assertThat(
            alice
                .read(alice.patch("/api/me/preferences", Map.of("timeZone", "")))
                .path("timeZone")
                .isMissingNode())
        .isTrue();

    // Validação: fuso que não é IANA, idioma, faixas e mínima acima da escala.
    alice
        .patch("/api/me/preferences", Map.of("timeZone", "GMT-3"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'timeZone')]").exists());
    alice
        .patch(
            "/api/me/preferences", Map.of("locale", "fr", "focusMinutes", 0, "newCardsPerDay", -1))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'locale')]").exists())
        .andExpect(jsonPath("$.details[?(@.field == 'focusMinutes')]").exists())
        .andExpect(jsonPath("$.details[?(@.field == 'newCardsPerDay')]").exists());
    alice
        .patch("/api/me/preferences", Map.of("gradeScaleMax", 5, "passingGrade", 7))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'passingGrade')]").exists());
    // Nada parcial foi gravado na requisição inválida.
    assertThat(alice.fetch("/api/grades/settings").path("scaleMax").decimalValue())
        .isEqualByComparingTo("10");
  }

  // -------------------------------------------------------------------------
  // e-mail e senha
  // -------------------------------------------------------------------------

  @Test
  void changeEmailRequiresPasswordAndRejectsDuplicates() throws Exception {
    String bobEmail = bob.fetch("/api/me").path("email").asText();
    alice
        .put("/api/me/email", Map.of("email", "new@example.com", "currentPassword", "wrong-pass"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"))
        .andExpect(jsonPath("$.details[?(@.field == 'currentPassword')]").exists());
    alice
        .put("/api/me/email", Map.of("email", bobEmail.toUpperCase(), "currentPassword", PASSWORD))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    alice
        .put("/api/me/email", Map.of("email", "not-an-email", "currentPassword", PASSWORD))
        .andExpect(status().isBadRequest());

    String fresh = AuthTestClient.uniqueEmail();
    JsonNode changed =
        alice.read(
            alice
                .put(
                    "/api/me/email",
                    Map.of("email", fresh.toUpperCase(), "currentPassword", PASSWORD))
                .andExpect(status().isOk()));
    assertThat(changed.path("email").asText()).isEqualTo(fresh);
    auth.login(fresh, PASSWORD, "test");
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(Map.of("email", aliceEmail, "password", PASSWORD))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void changePasswordRequiresTheCurrentOneAndKeepsOnlyThisSession() throws Exception {
    Session laptop = auth.login(aliceEmail, PASSWORD, "Laptop");
    Session phone = auth.login(aliceEmail, PASSWORD, "Phone");

    alice
        .put(
            "/api/me/password",
            Map.of("currentPassword", "wrong-pass", "newPassword", "brand-new-pass"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
    alice
        .put("/api/me/password", Map.of("currentPassword", PASSWORD, "newPassword", "short"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'newPassword')]").exists());
    alice
        .put("/api/me/password", Map.of("currentPassword", PASSWORD, "newPassword", PASSWORD))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'newPassword')]").exists());
    // As tentativas inválidas não derrubaram ninguém.
    meStatus(laptop, 200);

    MvcResult changed =
        alice
            .put(
                "/api/me/password",
                Map.of("currentPassword", PASSWORD, "newPassword", "brand-new-pass"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isString())
            .andReturn();
    Session rotated = auth.toSession(changed);
    assertThat(changed.getResponse().getContentAsString()).doesNotContain("brand-new-pass");

    // Outros dispositivos: access token e refresh deixam de valer na hora.
    meStatus(laptop, 401);
    meStatus(phone, 401);
    refreshStatus(laptop, 401);
    refreshStatus(phone, 401);
    // Esta sessão continua; o cookie antigo dela foi trocado pelo novo.
    meStatus(aliceSession, 200);
    refreshStatus(rotated, 200);
    assertThat(alice.fetch("/api/me/sessions")).hasSize(1);
    // Só o token novo desta sessão continua valendo.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens t JOIN users u ON u.id = t.user_id"
                    + " WHERE u.email = ? AND NOT t.revoked",
                Integer.class,
                aliceEmail))
        .isEqualTo(1);

    auth.login(aliceEmail, "brand-new-pass", "test");
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content(
                    json.writeValueAsString(Map.of("email", aliceEmail, "password", PASSWORD))))
        .andExpect(status().isUnauthorized());
  }

  // -------------------------------------------------------------------------
  // sessões
  // -------------------------------------------------------------------------

  @Test
  void listAndEndSessions() throws Exception {
    Session laptop = auth.login(aliceEmail, PASSWORD, "Mozilla/5.0 (X11; Linux) Firefox/140");
    Session phone = auth.login(aliceEmail, PASSWORD, "Mozilla/5.0 (iPhone) Safari");

    JsonNode list = alice.fetch("/api/me/sessions");
    assertThat(list).hasSize(3);
    List<JsonNode> current = new java.util.ArrayList<>();
    list.forEach(
        s -> {
          if (s.path("current").asBoolean()) current.add(s);
        });
    assertThat(current).hasSize(1);
    assertThat(list.toString()).doesNotContain("token").doesNotContain("hash");
    String laptopId = null;
    for (JsonNode s : list) {
      if (s.path("userAgent").asText().contains("Firefox")) {
        laptopId = s.path("id").asText();
      }
    }
    assertThat(laptopId).isNotNull();

    // Outro usuário não encerra (nem vê) sessão de Alice: 404.
    bob.delete("/api/me/sessions/" + laptopId).andExpect(status().isNotFound());
    assertThat(bob.fetch("/api/me/sessions")).hasSize(1);
    meStatus(laptop, 200);

    // Encerrar uma: aquele dispositivo cai na hora (access e refresh).
    alice.delete("/api/me/sessions/" + laptopId).andExpect(status().isNoContent());
    meStatus(laptop, 401);
    refreshStatus(laptop, 401);
    meStatus(phone, 200);
    assertThat(alice.fetch("/api/me/sessions")).hasSize(2);

    // Encerrar as outras: só esta fica.
    alice.delete("/api/me/sessions/others").andExpect(status().isNoContent());
    meStatus(phone, 401);
    meStatus(aliceSession, 200);
    assertThat(alice.fetch("/api/me/sessions")).hasSize(1);

    // Sair de todos: inclusive este (cookie limpo).
    MvcResult all = alice.delete("/api/me/sessions").andExpect(status().isNoContent()).andReturn();
    assertThat(all.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
    meStatus(aliceSession, 401);
    refreshStatus(aliceSession, 401);
    // Bob segue logado.
    meStatus(bobSession, 200);
  }

  @Test
  void logoutEndsTheSession() throws Exception {
    Session laptop = auth.login(aliceEmail, PASSWORD, "Laptop");
    mvc.perform(post("/api/auth/logout").cookie(laptop.refreshCookie()))
        .andExpect(status().isNoContent());
    meStatus(laptop, 401);
    assertThat(alice.fetch("/api/me/sessions")).hasSize(1);
  }

  // -------------------------------------------------------------------------
  // exportação
  // -------------------------------------------------------------------------

  private void seedSomeData(Api api, String tag) throws Exception {
    String semester = api.semester("Semestre " + tag);
    String subject = api.subject(semester, "Matéria " + tag);
    api.create(
        "/api/tasks", Map.of("title", "Tarefa " + tag, "type", "OTHER", "subjectId", subject));
    String deck =
        api.create("/api/flashcards/decks", Map.of("name", "Deck " + tag)).path("id").asText();
    api.create(
        "/api/flashcards/cards",
        Map.of(
            "deckId",
            deck,
            "front",
            "Frente " + tag,
            "back",
            "Verso " + tag,
            "tags",
            List.of("t1", "t2")));
    api.put(
            "/api/subjects/" + subject + "/assessments",
            Map.of("items", List.of(Map.of("name", "P1 " + tag, "weight", 1, "score", 7))))
        .andExpect(status().isOk());
    api.post("/api/subjects/" + subject + "/absences", Map.of("date", "2026-09-01", "count", 2))
        .andExpect(status().isCreated());
    api.patch("/api/me/preferences", Map.of("focusMinutes", 30)).andExpect(status().isOk());
  }

  @Test
  void exportHasAllMyDataAndNothingElse() throws Exception {
    seedSomeData(alice, "Alice");
    seedSomeData(bob, "Bob");

    MvcResult result = alice.get("/api/me/export").andExpect(status().isOk()).andReturn();
    assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
        .startsWith("attachment; filename=\"stackstudy-export-");
    String body = result.getResponse().getContentAsString();
    JsonNode export = json.readTree(body);

    assertThat(export.path("profile").path("email").asText()).isEqualTo(aliceEmail);
    for (String section :
        List.of(
            "semesters",
            "subjects",
            "tasks",
            "assessments",
            "absences",
            "decks",
            "cards",
            "deepWorkSettings",
            "sessions")) {
      assertThat(export.path(section)).as(section).hasSize(1);
    }
    assertThat(export.path("cards").get(0).path("tags")).hasSize(2);
    assertThat(export.path("tasks").get(0).path("title").asText()).isEqualTo("Tarefa Alice");
    assertThat(export.path("studySessions").isArray()).isTrue();

    // Só dados de Alice; nenhum segredo de autenticação.
    assertThat(body).doesNotContain("Bob").doesNotContain("bob-password");
    assertThat(body)
        .doesNotContain("passwordHash")
        .doesNotContain("tokenHash")
        .doesNotContain("$2a$");

    // Toda tabela com dono direto (user_id) aparece na exportação (exceto refresh_tokens).
    Set<String> withUser =
        new HashSet<>(
            jdbc.queryForList(
                "SELECT table_name FROM information_schema.columns WHERE column_name = 'user_id'"
                    + " AND table_schema = current_schema()",
                String.class));
    withUser.remove("refresh_tokens");
    Set<String> covered = new HashSet<>();
    DataExportService.QUERIES
        .values()
        .forEach(
            sql -> {
              for (String table : withUser) {
                if (sql.contains(" " + table + " ")) {
                  covered.add(table);
                }
              }
            });
    assertThat(covered).containsExactlyInAnyOrderElementsOf(withUser);
  }

  // -------------------------------------------------------------------------
  // exclusão
  // -------------------------------------------------------------------------

  @Test
  void deleteAccountRemovesEverythingAndEndsTheSession() throws Exception {
    String tag = UUID.randomUUID().toString();
    seedSomeData(alice, "Alice" + tag);
    seedSomeData(bob, "Bob" + tag);
    Session laptop = auth.login(aliceEmail, PASSWORD, "Laptop");
    UUID aliceId = UUID.fromString(alice.fetch("/api/me").path("id").asText());

    // Confirmação errada: nada acontece.
    alice
        .delete("/api/me", Map.of("email", "other@example.com", "currentPassword", PASSWORD))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'email')]").exists());
    alice
        .delete("/api/me", Map.of("email", aliceEmail, "currentPassword", "wrong-pass"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
    meStatus(aliceSession, 200);

    MvcResult deleted =
        alice
            .delete(
                "/api/me", Map.of("email", aliceEmail.toUpperCase(), "currentPassword", PASSWORD))
            .andExpect(status().isNoContent())
            .andReturn();
    assertThat(deleted.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");

    // Sessões invalidadas (todos os dispositivos).
    meStatus(aliceSession, 401);
    meStatus(laptop, 401);
    refreshStatus(aliceSession, 401);
    refreshStatus(laptop, 401);

    // Nenhuma linha de Alice sobra em tabela nenhuma.
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, aliceId))
        .isZero();
    for (String table :
        List.of(
            "refresh_tokens",
            "auth_sessions",
            "semesters",
            "tasks",
            "study_sessions",
            "decks",
            "cards",
            "card_reviews",
            "deep_work_settings",
            "grade_settings",
            "flashcard_settings")) {
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM " + table + " WHERE user_id = ?", Integer.class, aliceId))
          .as(table)
          .isZero();
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM subjects WHERE name = ?",
                Integer.class,
                "Matéria Alice" + tag))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM assessments WHERE name = ?", Integer.class, "P1 Alice" + tag))
        .isZero();

    // Bob intacto.
    meStatus(bobSession, 200);
    JsonNode bobExport = bob.fetch("/api/me/export");
    assertThat(bobExport.path("subjects")).hasSize(1);
    assertThat(bobExport.path("assessments")).hasSize(1);
  }
}
