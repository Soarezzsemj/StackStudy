package com.stackstudy.cockpit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackstudy.support.Api;
import com.stackstudy.support.AuthTestClient;
import com.stackstudy.support.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Cockpit: dados de várias fases, usuário sem nada, isolamento e número fixo de consultas. */
@TestPropertySource(
    properties = {
      "spring.jpa.properties.hibernate.generate_statistics=true",
      "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"
    })
class CockpitIntegrationTest extends IntegrationTest {

  /** Consultas do GET /api/cockpit (ver o javadoc de CockpitService). */
  static final long EXPECTED_QUERIES = 17;

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired EntityManagerFactory emf;

  Api alice;
  Api bob;

  @BeforeEach
  void setUp() throws Exception {
    AuthTestClient auth = new AuthTestClient(mvc, json);
    alice = new Api(mvc, json, auth.register(AuthTestClient.uniqueEmail(), "alice-password"));
    bob = new Api(mvc, json, auth.register(AuthTestClient.uniqueEmail(), "bob-password"));
  }

  private JsonNode cockpit(Api api) throws Exception {
    return api.fetch("/api/cockpit?tz=UTC");
  }

  private String task(Api api, String title, String type, Instant due, String subjectId)
      throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("title", title);
    body.put("type", type);
    body.put("dueAt", due.toString());
    body.put("subjectId", subjectId);
    return api.create("/api/tasks", body).path("id").asText();
  }

  private String subject(Api api, String semester, String name, Integer hours) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("semesterId", semester);
    body.put("name", name);
    body.put("credits", 4);
    body.put("workloadHours", hours);
    return api.create("/api/subjects", body).path("id").asText();
  }

  /** Dados de todas as fases para Alice. Devolve o instante da sessão de foco. */
  private Instant seedAlice(int extraTasks) throws Exception {
    Instant now = Instant.now();
    String semester = alice.semester("2026.2");
    String anatomy = subject(alice, semester, "Anatomia", 60);
    String history = subject(alice, semester, "História", 60);
    subject(alice, semester, "Programação", 60);

    // Anatomia: zero na P1 (peso 50) = precisa 12 na P2 → impossível.
    alice
        .put(
            "/api/subjects/" + anatomy + "/assessments",
            Map.of(
                "items",
                List.of(
                    Map.of("name", "P1", "weight", 50, "score", 0),
                    Map.of("name", "P2", "weight", 50))))
        .andExpect(status().isOk());
    // História: 60 aulas, limite 15; 16 faltas → acima do limite.
    alice
        .post("/api/subjects/" + history + "/absences", Map.of("date", "2026-09-01", "count", 16))
        .andExpect(status().isCreated());

    task(alice, "Lista atrasada", "EXERCISE_LIST", now.minus(1, ChronoUnit.DAYS), anatomy);
    task(alice, "Trabalho em 2 dias", "ASSIGNMENT", now.plus(2, ChronoUnit.DAYS), history);
    task(alice, "Prova final", "EXAM", now.plus(30, ChronoUnit.DAYS), anatomy);
    String done = task(alice, "Já feita", "OTHER", now.plus(1, ChronoUnit.DAYS), null);
    alice
        .patch("/api/tasks/" + done + "/status", Map.of("status", "DONE"))
        .andExpect(status().isOk());
    for (int i = 0; i < extraTasks; i++) {
      task(alice, "Extra " + i, "READING", now.plus(3, ChronoUnit.DAYS), history);
    }

    Instant started = now.minus(5, ChronoUnit.MINUTES);
    alice
        .post(
            "/api/deepwork/sessions/manual",
            Map.of("subjectId", anatomy, "startedAt", started.toString(), "durationMinutes", 5))
        .andExpect(status().isCreated());

    String deck =
        alice.create("/api/flashcards/decks", Map.of("name", "Ossos")).path("id").asText();
    alice.create("/api/flashcards/cards", Map.of("deckId", deck, "front", "Fêmur", "back", "Osso"));
    alice.create("/api/flashcards/cards", Map.of("deckId", deck, "front", "Tíbia", "back", "Osso"));
    return started;
  }

  @Test
  void emptyUserGetsZerosAndTheOnboardingChecklist() throws Exception {
    JsonNode c = cockpit(alice);
    assertThat(c.path("timeZone").asText()).isEqualTo("UTC");
    assertThat(c.path("focus").path("todaySeconds").asLong()).isZero();
    assertThat(c.path("focus").path("currentStreak").asInt()).isZero();
    // Meta padrão 600 min/semana → 86 min/dia.
    assertThat(c.path("focus").path("dailyGoalSeconds").asLong()).isEqualTo(86 * 60);
    assertThat(c.path("tasks").path("overdueCount").asLong()).isZero();
    assertThat(c.path("tasks").path("upcoming")).isEmpty();
    assertThat(c.path("nextExam").isMissingNode()).isTrue();
    assertThat(c.path("flashcards").path("dueToday").asLong()).isZero();
    assertThat(c.path("atRisk")).isEmpty();
    assertThat(c.path("heatmap").path("days")).isEmpty();
    JsonNode o = c.path("onboarding");
    for (String step : List.of("hasSemester", "hasSubject", "hasTask", "hasFocusSession")) {
      assertThat(o.path(step).asBoolean()).as(step).isFalse();
    }
  }

  @Test
  void aggregatesEveryModule() throws Exception {
    Instant started = seedAlice(0);
    JsonNode c = cockpit(alice);
    String today = c.path("today").asText();

    // Foco: 5 min hoje (se a sessão não caiu ontem, perto da meia-noite UTC) e sequência ativa.
    long expectedToday =
        started.atZone(ZoneOffset.UTC).toLocalDate().toString().equals(today) ? 300 : 0;
    assertThat(c.path("focus").path("todaySeconds").asLong()).isEqualTo(expectedToday);
    assertThat(c.path("focus").path("currentStreak").asInt()).isEqualTo(1);
    assertThat(c.path("heatmap").path("days")).hasSize(1);
    assertThat(c.path("heatmap").path("days").get(0).path("seconds").asLong()).isEqualTo(300);

    // Prazos: 1 atrasada, 1 nos próximos 7 dias; a feita e a de 30 dias não entram.
    JsonNode tasks = c.path("tasks");
    assertThat(tasks.path("overdueCount").asLong()).isEqualTo(1);
    assertThat(tasks.path("upcomingCount").asLong()).isEqualTo(1);
    assertThat(tasks.path("overdue").get(0).path("title").asText()).isEqualTo("Lista atrasada");
    assertThat(tasks.path("overdue").get(0).path("subjectName").asText()).isEqualTo("Anatomia");
    assertThat(tasks.path("upcoming").get(0).path("title").asText())
        .isEqualTo("Trabalho em 2 dias");
    // Próxima prova: mesmo fora dos 7 dias.
    assertThat(c.path("nextExam").path("title").asText()).isEqualTo("Prova final");
    assertThat(c.path("nextExam").path("subjectColor").asText()).startsWith("#");

    // Flashcards: 2 cartas novas entram hoje.
    assertThat(c.path("flashcards").path("dueToday").asLong()).isEqualTo(2);
    assertThat(c.path("flashcards").path("newToday").asLong()).isEqualTo(2);
    assertThat(c.path("flashcards").path("decks").asInt()).isEqualTo(1);

    // Em risco: Anatomia (nota impossível) primeiro, História (faltas acima) depois.
    JsonNode risk = c.path("atRisk");
    assertThat(risk).hasSize(2);
    assertThat(risk.get(0).path("subject").path("name").asText()).isEqualTo("Anatomia");
    assertThat(risk.get(0).path("gradeRisk").asBoolean()).isTrue();
    assertThat(risk.get(0).path("subject").path("result").path("standing").asText())
        .isEqualTo("IMPOSSIBLE");
    assertThat(risk.get(1).path("subject").path("name").asText()).isEqualTo("História");
    assertThat(risk.get(1).path("attendanceRisk").asBoolean()).isTrue();

    JsonNode o = c.path("onboarding");
    for (String step :
        List.of("hasSemester", "hasSubject", "hasTask", "hasFocusSession", "hasDeck")) {
      assertThat(o.path(step).asBoolean()).as(step).isTrue();
    }
  }

  @Test
  void otherUsersDataNeverShowsUp() throws Exception {
    seedAlice(0);
    JsonNode c = cockpit(bob);
    assertThat(c.path("tasks").path("overdueCount").asLong()).isZero();
    assertThat(c.path("tasks").path("upcoming")).isEmpty();
    assertThat(c.path("nextExam").isMissingNode()).isTrue();
    assertThat(c.path("flashcards").path("totalCards").asLong()).isZero();
    assertThat(c.path("atRisk")).isEmpty();
    assertThat(c.path("heatmap").path("days")).isEmpty();
    assertThat(c.path("focus").path("currentStreak").asInt()).isZero();
    assertThat(c.path("onboarding").path("hasSemester").asBoolean()).isFalse();
    assertThat(c.toString()).doesNotContain("Anatomia").doesNotContain("Prova final");
  }

  @Test
  void invalidTimeZoneIsRejected() throws Exception {
    alice.get("/api/cockpit?tz=GMT-3").andExpect(status().isBadRequest());
  }

  @Test
  void fixedNumberOfQueriesWhateverTheAmountOfData() throws Exception {
    Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
    // Aquece (a sessão do token fica no cache) e mede com pouco dado.
    cockpit(alice);
    stats.clear();
    cockpit(alice);
    long empty = stats.getPrepareStatementCount();

    seedAlice(15);
    cockpit(alice);
    stats.clear();
    cockpit(alice);
    long full = stats.getPrepareStatementCount();

    assertThat(empty).isEqualTo(EXPECTED_QUERIES);
    assertThat(full).as("sem N+1: o número de consultas não cresce com os dados").isEqualTo(empty);
  }
}
