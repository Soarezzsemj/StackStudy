package com.stackstudy.roadmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackstudy.support.Api;
import com.stackstudy.support.AuthTestClient;
import com.stackstudy.support.IntegrationTest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Roadmaps com a flag do Premium desligada (padrão): tudo liberado. */
class RoadmapIntegrationTest extends IntegrationTest {

  static final String CS = "/api/roadmaps/ciencia-da-computacao";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  Api alice;
  Api bob;

  @BeforeEach
  void setUp() throws Exception {
    AuthTestClient auth = new AuthTestClient(mvc, json);
    alice = new Api(mvc, json, auth.register(AuthTestClient.uniqueEmail(), "alice-password"));
    bob = new Api(mvc, json, auth.register(AuthTestClient.uniqueEmail(), "bob-password"));
  }

  /** Nós na ordem da trilha. */
  static List<JsonNode> nodes(JsonNode detail) {
    List<JsonNode> out = new ArrayList<>();
    detail.path("stages").forEach(s -> s.path("nodes").forEach(out::add));
    return out;
  }

  static JsonNode byTitle(JsonNode detail, String title) {
    return nodes(detail).stream()
        .filter(n -> n.path("title").asText().equals(title))
        .findFirst()
        .orElseThrow();
  }

  static String progressPath(String roadmap, JsonNode node) {
    return roadmap + "/nodes/" + node.path("id").asText() + "/progress";
  }

  @Test
  void listsTheSampleRoadmapsWithCountsFiltersAndSearch() throws Exception {
    JsonNode list = alice.fetch("/api/roadmaps");
    assertThat(list.path("premiumEnabled").asBoolean()).isFalse();
    assertThat(list.path("items")).hasSize(3);
    assertThat(list.path("areas").toString())
        .contains("Ciência da Computação", "Direito", "Medicina");
    JsonNode cs = list.path("items").get(0);
    assertThat(cs.path("slug").asText()).isEqualTo("ciencia-da-computacao");
    assertThat(cs.path("sample").asBoolean()).isTrue();
    assertThat(cs.path("stageCount").asInt()).isEqualTo(4);
    // 15 nós, 1 opcional.
    assertThat(cs.path("topicCount").asInt()).isEqualTo(14);
    assertThat(cs.path("totalHours").decimalValue()).isPositive();
    assertThat(cs.path("following").asBoolean()).isFalse();
    assertThat(absent(cs.path("percent"))).isTrue();

    assertThat(alice.fetch("/api/roadmaps?area={a}", "Direito").path("items")).hasSize(1);
    // Busca sem acento e sem diferenciar maiúsculas.
    JsonNode found = alice.fetch("/api/roadmaps?q={q}", "FISIOLOGIA anatomia");
    assertThat(found.path("items")).isEmpty();
    assertThat(
            alice
                .fetch("/api/roadmaps?q={q}", "ciencia")
                .path("items")
                .get(0)
                .path("slug")
                .asText())
        .isEqualTo("ciencia-da-computacao");
    alice.get("/api/roadmaps/nao-existe").andExpect(status().isNotFound());
  }

  @Test
  void withTheFlagOffEverythingIsOpenEvenPremiumStages() throws Exception {
    JsonNode detail = alice.fetch(CS);
    assertThat(detail.path("premiumEnabled").asBoolean()).isFalse();
    assertThat(absent(detail.path("following"))).isTrue();
    JsonNode stages = detail.path("stages");
    assertThat(stages).hasSize(4);
    assertThat(stages.get(3).path("access").asText()).isEqualTo("PREMIUM");
    stages.forEach(s -> assertThat(s.path("locked").asBoolean()).isFalse());
    // Conteúdo da etapa Premium vem inteiro.
    JsonNode git = byTitle(detail, "Controle de versão com Git");
    assertThat(git.path("description").asText()).isNotBlank();
    assertThat(git.path("resources").get(0).path("url").asText()).startsWith("https://");
    assertThat(git.path("kind").asText()).isEqualTo("SUB");
    assertThat(git.path("side").asText()).isEqualTo("LEFT");

    // Escrever numa etapa Premium também é permitido.
    alice.put(progressPath(CS, git), Map.of("status", "DONE")).andExpect(status().isOk());
  }

  @Test
  void followingSetsPrimaryAndCurrentAndCompletingAdvances() throws Exception {
    JsonNode followed = alice.read(alice.post(CS + "/follow").andExpect(status().isOk()));
    JsonNode first = nodes(followed).get(0);
    assertThat(first.path("title").asText()).isEqualTo("Lógica de programação");
    assertThat(followed.path("following").path("primary").asBoolean()).isTrue();
    assertThat(followed.path("following").path("currentNodeId").asText())
        .isEqualTo(first.path("id").asText());
    assertThat(first.path("current").asBoolean()).isTrue();

    // Concluir o atual avança para o próximo (o subtópico obrigatório).
    JsonNode after = alice.read(alice.put(progressPath(CS, first), Map.of("status", "DONE")));
    JsonNode next = byTitle(after, "Algoritmos e pseudocódigo");
    assertThat(after.path("following").path("currentNodeId").asText())
        .isEqualTo(next.path("id").asText());
    assertThat(absent(byTitle(after, "Lógica de programação").path("completedAt"))).isFalse();
    assertThat(after.path("summary").path("doneTopics").asInt()).isEqualTo(1);
    // 1 de 14 = 7,1%.
    assertThat(after.path("summary").path("percent").decimalValue()).isEqualByComparingTo("7.1");
    assertThat(after.path("stages").get(0).path("doneCount").asInt()).isEqualTo(1);

    // "Em andamento" em outro tópico o torna o atual.
    JsonNode sql = byTitle(after, "SQL");
    JsonNode doing =
        alice.read(alice.put(progressPath(CS, sql), Map.of("status", "DOING", "manualHours", 2.5)));
    assertThat(doing.path("following").path("currentNodeId").asText())
        .isEqualTo(sql.path("id").asText());
    assertThat(byTitle(doing, "SQL").path("hoursSpent").decimalValue()).isEqualByComparingTo("2.5");

    // Voltar a "a fazer" limpa a data de conclusão.
    JsonNode undone = alice.read(alice.put(progressPath(CS, first), Map.of("status", "TODO")));
    assertThat(absent(byTitle(undone, "Lógica de programação").path("completedAt"))).isTrue();

    // Seguir de novo é idempotente; um segundo roadmap não rouba o principal sem pedir.
    alice.post(CS + "/follow").andExpect(status().isOk());
    JsonNode law = alice.read(alice.post("/api/roadmaps/direito/follow"));
    assertThat(law.path("following").path("primary").asBoolean()).isFalse();
    JsonNode lawPrimary =
        alice.read(alice.post("/api/roadmaps/direito/follow", Map.of("primary", true)));
    assertThat(lawPrimary.path("following").path("primary").asBoolean()).isTrue();
    assertThat(alice.fetch(CS).path("following").path("primary").asBoolean()).isFalse();

    // Parar de seguir o principal promove o outro; o progresso continua guardado.
    alice.delete("/api/roadmaps/direito/follow").andExpect(status().isNoContent());
    assertThat(alice.fetch(CS).path("following").path("primary").asBoolean()).isTrue();
    alice.delete(CS + "/follow").andExpect(status().isNoContent());
    JsonNode gone = alice.fetch(CS);
    assertThat(absent(gone.path("following"))).isTrue();
    assertThat(byTitle(gone, "SQL").path("status").asText()).isEqualTo("DOING");
    JsonNode list = alice.fetch("/api/roadmaps");
    list.path("items").forEach(i -> assertThat(i.path("following").asBoolean()).isFalse());
  }

  @Test
  void eachUserOnlySeesAndChangesTheirOwnProgress() throws Exception {
    JsonNode detail = alice.fetch(CS);
    JsonNode first = nodes(detail).get(0);
    alice.put(progressPath(CS, first), Map.of("status", "DONE", "manualHours", 3));
    alice.put(CS + "/goal", Map.of("weeklyGoalHours", 6)).andExpect(status().isOk());

    // Bob não vê nada da Alice...
    JsonNode bobs = bob.fetch(CS);
    assertThat(absent(bobs.path("following"))).isTrue();
    assertThat(byTitle(bobs, "Lógica de programação").path("status").asText()).isEqualTo("TODO");
    assertThat(bobs.path("summary").path("doneTopics").asInt()).isZero();
    assertThat(bob.fetch("/api/roadmaps").path("items").get(0).path("following").asBoolean())
        .isFalse();

    // ...e o que ele grava fica só com ele.
    bob.put(progressPath(CS, first), Map.of("status", "TODO", "manualHours", 0))
        .andExpect(status().isOk());
    JsonNode alices = alice.fetch(CS);
    JsonNode aliceFirst = byTitle(alices, "Lógica de programação");
    assertThat(aliceFirst.path("status").asText()).isEqualTo("DONE");
    assertThat(aliceFirst.path("manualHours").decimalValue()).isEqualByComparingTo("3");
    assertThat(alices.path("following").path("weeklyGoalHours").asInt()).isEqualTo(6);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM user_node_progress p JOIN users u ON u.id = p.user_id"
                    + " WHERE u.email = ? AND p.node_id = ?::uuid",
                Integer.class,
                bob.read(bob.get("/api/me")).path("email").asText(),
                first.path("id").asText()))
        .isEqualTo(1);
  }

  @Test
  void nodeFromAnotherRoadmapIsRejected() throws Exception {
    JsonNode csNode = nodes(alice.fetch(CS)).get(0);
    alice
        .put(progressPath("/api/roadmaps/direito", csNode), Map.of("status", "DONE"))
        .andExpect(status().isNotFound());
    alice
        .put(
            "/api/roadmaps/direito/nodes/00000000-0000-0000-0000-000000000000/progress",
            Map.of("status", "DONE"))
        .andExpect(status().isNotFound());
    // E nada foi gravado.
    assertThat(byTitle(alice.fetch(CS), "Lógica de programação").path("status").asText())
        .isEqualTo("TODO");
  }

  @Test
  void hoursAndGoalAreValidated() throws Exception {
    JsonNode node = nodes(alice.fetch(CS)).get(0);
    String path = progressPath(CS, node);
    alice
        .put(path, Map.of("status", "DONE", "manualHours", 501))
        .andExpect(status().isBadRequest());
    alice.put(path, Map.of("status", "DONE", "manualHours", -1)).andExpect(status().isBadRequest());
    alice
        .put(path, Map.of("status", "DONE", "manualHours", 1.234))
        .andExpect(status().isBadRequest());
    alice.put(path, Map.of("manualHours", 2)).andExpect(status().isBadRequest());
    alice.put(path, Map.of("status", "MAYBE")).andExpect(status().isBadRequest());
    alice.put(CS + "/goal", Map.of("weeklyGoalHours", 0)).andExpect(status().isBadRequest());
    alice.put(CS + "/goal", Map.of("weeklyGoalHours", 81)).andExpect(status().isBadRequest());
    alice.put(CS + "/goal", Map.of()).andExpect(status().isBadRequest());
  }

  @Test
  void summaryComputesPercentHoursAndEstimateIncludingZeroHours() throws Exception {
    JsonNode start = alice.read(alice.post(CS + "/follow"));
    JsonNode summary = start.path("summary");
    assertThat(summary.path("percent").decimalValue()).isEqualByComparingTo("0");
    assertThat(summary.path("hoursDone").decimalValue()).isEqualByComparingTo("0");
    // Sem meta: sem estimativa (nada de divisão por zero).
    assertThat(absent(summary.path("weeklyGoalHours"))).isTrue();
    assertThat(absent(summary.path("estimatedCompletion"))).isTrue();
    BigDecimal total = summary.path("totalHours").decimalValue();
    assertThat(summary.path("hoursRemaining").decimalValue()).isEqualByComparingTo(total);

    JsonNode withGoal = alice.read(alice.put(CS + "/goal", Map.of("weeklyGoalHours", 10)));
    JsonNode s = withGoal.path("summary");
    int expectedDays =
        s.path("hoursRemaining")
            .decimalValue()
            .multiply(BigDecimal.valueOf(7))
            .divide(BigDecimal.TEN, 0, RoundingMode.CEILING)
            .intValueExact();
    assertThat(s.path("daysRemaining").asInt()).isEqualTo(expectedDays);
    assertThat(s.path("estimatedCompletion").asText()).isNotBlank();

    // Horas a mais num tópico concluído não deixam o restante negativo.
    JsonNode first = nodes(withGoal).get(0);
    JsonNode done =
        alice.read(
            alice.put(progressPath(CS, first), Map.of("status", "DONE", "manualHours", 100)));
    BigDecimal firstEstimate = first.path("estimatedHours").decimalValue();
    assertThat(done.path("summary").path("hoursRemaining").decimalValue())
        .isEqualByComparingTo(total.subtract(firstEstimate));
    assertThat(done.path("summary").path("hoursDone").decimalValue()).isEqualByComparingTo("100");
  }

  @Test
  void suggestionsAreStoredValidatedAndRateLimited() throws Exception {
    alice
        .post(
            "/api/roadmaps/suggestions",
            Map.of(
                "title", "Arquitetura e Urbanismo", "area", "Arquitetura", "details", "Etapas: "))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM roadmap_suggestions WHERE title = 'Arquitetura e Urbanismo'",
                Integer.class))
        .isPositive();

    alice
        .post("/api/roadmaps/suggestions", Map.of("title", "<b>Nutrição</b>"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[0].field").value("title"));
    alice
        .post("/api/roadmaps/suggestions", Map.of("title", "Psicologia", "details", "<script>"))
        .andExpect(status().isBadRequest());
    alice
        .post("/api/roadmaps/suggestions", Map.of("title", "ab"))
        .andExpect(status().isBadRequest());
    alice
        .post("/api/roadmaps/suggestions", Map.of("title", "x".repeat(121)))
        .andExpect(status().isBadRequest());
    alice
        .post("/api/roadmaps/suggestions", Map.of("title", "Letras", "details", "y".repeat(1001)))
        .andExpect(status().isBadRequest());

    // 5 por dia por usuário (a primeira já contou): a 6ª dá 429; outro usuário não é afetado.
    for (int i = 0; i < 4; i++) {
      alice
          .post("/api/roadmaps/suggestions", Map.of("title", "Sugestão " + i))
          .andExpect(status().isCreated());
    }
    alice
        .post("/api/roadmaps/suggestions", Map.of("title", "Mais uma"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    bob.post("/api/roadmaps/suggestions", Map.of("title", "Enfermagem"))
        .andExpect(status().isCreated());
  }

  @Test
  void invalidResourceUrlsAreRejectedByTheDatabase() {
    String node = jdbc.queryForObject("SELECT id::text FROM roadmap_nodes LIMIT 1", String.class);
    for (String url :
        List.of("javascript:alert(1)", "ftp://x.org/a", "https://x.org/a b", "data:text/html,x")) {
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "INSERT INTO roadmap_node_resources (node_id, title, url) VALUES (?::uuid, 't', ?)",
                      node,
                      url))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
    // http e https passam (e o rollback manual deixa o seed como estava).
    jdbc.update(
        "INSERT INTO roadmap_node_resources (node_id, title, url) VALUES (?::uuid, 'ok', 'http://x.org/ok')",
        node);
    jdbc.update("DELETE FROM roadmap_node_resources WHERE url = 'http://x.org/ok'");
  }

  @Test
  void deepWorkSessionsLinkedToATopicAddUpInItsHours() throws Exception {
    String subject = alice.subject(alice.semester("2026.2"), "Algoritmos");
    JsonNode node = byTitle(alice.fetch(CS), "Estruturas de dados");
    String id = node.path("id").asText();

    Map<String, Object> manual = new HashMap<>();
    manual.put("subjectId", subject);
    manual.put("startedAt", Instant.now().minus(3, ChronoUnit.HOURS).toString());
    manual.put("durationMinutes", 90);
    manual.put("roadmapNodeId", id);
    JsonNode session = alice.create("/api/deepwork/sessions/manual", manual);
    assertThat(session.path("roadmapNodeId").asText()).isEqualTo(id);

    JsonNode after = byTitle(alice.fetch(CS), "Estruturas de dados");
    assertThat(after.path("focusHours").decimalValue()).isEqualByComparingTo("1.5");
    assertThat(after.path("hoursSpent").decimalValue()).isEqualByComparingTo("1.5");
    // Foco num tópico a fazer o põe em andamento.
    assertThat(after.path("status").asText()).isEqualTo("DOING");

    // Horas manuais somam às do Deep Work.
    alice.put(progressPath(CS, node), Map.of("status", "DOING", "manualHours", 1));
    assertThat(byTitle(alice.fetch(CS), "Estruturas de dados").path("hoursSpent").decimalValue())
        .isEqualByComparingTo("2.5");

    // Sessão cronometrada ligada ao tópico conta ao concluir.
    JsonNode timer =
        alice.create(
            "/api/deepwork/sessions",
            Map.of("subjectId", subject, "mode", "FLOW", "roadmapNodeId", id));
    jdbc.update(
        "UPDATE study_sessions SET started_at = started_at - interval '30 minutes' WHERE id = ?::uuid",
        timer.path("id").asText());
    alice.post("/api/deepwork/sessions/" + timer.path("id").asText() + "/complete", Map.of());
    assertThat(byTitle(alice.fetch(CS), "Estruturas de dados").path("focusHours").decimalValue())
        .isEqualByComparingTo("2");

    // Apagar a sessão tira as horas dela.
    alice
        .delete("/api/deepwork/sessions/" + session.path("id").asText())
        .andExpect(status().isNoContent());
    assertThat(byTitle(alice.fetch(CS), "Estruturas de dados").path("focusHours").decimalValue())
        .isEqualByComparingTo("0.5");

    // Tópico inexistente: 404; sessões do Bob não contam para a Alice.
    manual.put("roadmapNodeId", "00000000-0000-0000-0000-000000000000");
    alice.post("/api/deepwork/sessions/manual", manual).andExpect(status().isNotFound());
    assertThat(byTitle(bob.fetch(CS), "Estruturas de dados").path("focusHours").decimalValue())
        .isEqualByComparingTo("0");
  }

  @Test
  void cockpitShowsThePrimaryRoadmapOnlyForFollowers() throws Exception {
    JsonNode none = alice.fetch("/api/cockpit?tz={tz}", "America/Sao_Paulo");
    assertThat(absent(none.path("roadmap"))).isTrue();

    JsonNode followed = alice.read(alice.post(CS + "/follow"));
    alice.put(progressPath(CS, nodes(followed).get(0)), Map.of("status", "DONE"));
    JsonNode cockpit = alice.fetch("/api/cockpit?tz={tz}", "America/Sao_Paulo").path("roadmap");
    assertThat(cockpit.path("slug").asText()).isEqualTo("ciencia-da-computacao");
    assertThat(cockpit.path("percent").decimalValue()).isEqualByComparingTo("7.1");
    assertThat(cockpit.path("nextNodeTitle").asText()).isEqualTo("Algoritmos e pseudocódigo");
    assertThat(cockpit.path("nextStageTitle").asText()).isEqualTo("Fundamentos");
    assertThat(cockpit.path("nextStagePosition").asInt()).isEqualTo(1);
  }

  /** null não vem no JSON (non_null). */
  static boolean absent(JsonNode n) {
    return n.isMissingNode() || n.isNull();
  }
}
