package com.stackstudy.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackstudy.support.Api;
import com.stackstudy.support.AuthTestClient;
import com.stackstudy.support.IntegrationTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class TaskIntegrationTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  Api alice;
  Api bob;
  String semester;
  String calculus;
  String law;

  @BeforeEach
  void setUp() throws Exception {
    AuthTestClient auth = new AuthTestClient(mvc, json);
    alice = new Api(mvc, json, auth.register(AuthTestClient.uniqueEmail(), "alice-password"));
    bob = new Api(mvc, json, auth.register(AuthTestClient.uniqueEmail(), "bob-password"));
    semester = alice.semester("2026.2");
    calculus = alice.subject(semester, "Cálculo I");
    law = alice.subject(semester, "Direito Constitucional");
  }

  private JsonNode task(Map<String, Object> fields) throws Exception {
    return alice.create("/api/tasks", fields);
  }

  private List<String> titles(JsonNode page) {
    List<String> out = new ArrayList<>();
    page.path("items").forEach(t -> out.add(t.path("title").asText()));
    return out;
  }

  @Test
  void crudAndDefaults() throws Exception {
    JsonNode created = task(Map.of("title", "  Ler capítulo 2  "));
    String id = created.path("id").asText();
    assertThat(created.path("title").asText()).isEqualTo("Ler capítulo 2");
    assertThat(created.path("type").asText()).isEqualTo("OTHER");
    assertThat(created.path("priority").asText()).isEqualTo("MEDIUM");
    assertThat(created.path("status").asText()).isEqualTo("TODO");
    assertThat(created.has("subjectId")).isFalse();
    assertThat(created.path("dueAllDay").asBoolean()).isFalse();

    Map<String, Object> body = new HashMap<>();
    body.put("title", "Ler capítulo 2 e 3");
    body.put("description", "Páginas 30 a 80");
    body.put("subjectId", law);
    body.put("type", "READING");
    body.put("priority", "HIGH");
    body.put("status", "DOING");
    body.put("dueAt", "2026-12-01T23:59:59Z");
    body.put("dueAllDay", true);
    alice
        .put("/api/tasks/" + id, body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.subjectId").value(law))
        .andExpect(jsonPath("$.type").value("READING"))
        .andExpect(jsonPath("$.priority").value("HIGH"))
        .andExpect(jsonPath("$.status").value("DOING"))
        .andExpect(jsonPath("$.dueAt").value("2026-12-01T23:59:59Z"))
        .andExpect(jsonPath("$.dueAllDay").value(true))
        .andExpect(jsonPath("$.description").value("Páginas 30 a 80"));

    alice.get("/api/tasks/" + id).andExpect(status().isOk());
    alice.delete("/api/tasks/" + id).andExpect(status().isNoContent());
    alice.get("/api/tasks/" + id).andExpect(status().isNotFound());
  }

  @Test
  void statusPatchSetsAndClearsCompletedAt() throws Exception {
    String id = task(Map.of("title", "Lista 1")).path("id").asText();

    alice
        .patch("/api/tasks/" + id + "/status", Map.of("status", "DONE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE"))
        .andExpect(jsonPath("$.completedAt").exists());

    alice
        .patch("/api/tasks/" + id + "/status", Map.of("status", "TODO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completedAt").doesNotExist());

    alice
        .patch("/api/tasks/" + id + "/status", Map.of())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void dueAllDayWithoutDueIsIgnored() throws Exception {
    JsonNode created = task(Map.of("title", "x", "dueAllDay", true));
    assertThat(created.path("dueAllDay").asBoolean()).isFalse();
  }

  @Test
  void filtersBySubjectTypeStatusTextAndDue() throws Exception {
    task(
        Map.of(
            "title",
            "P1 de Cálculo",
            "subjectId",
            calculus,
            "type",
            "EXAM",
            "dueAt",
            "2026-11-10T12:00:00Z"));
    task(
        Map.of(
            "title",
            "Lista de derivadas",
            "subjectId",
            calculus,
            "type",
            "EXERCISE_LIST",
            "status",
            "DONE",
            "dueAt",
            "2026-11-05T12:00:00Z"));
    task(
        Map.of(
            "title",
            "Fichamento",
            "subjectId",
            law,
            "type",
            "ASSIGNMENT",
            "description",
            "Controle de CONSTITUCIONALIDADE",
            "dueAt",
            "2026-11-20T12:00:00Z"));
    task(Map.of("title", "Renovar livros 100%", "type", "OTHER"));

    assertThat(titles(alice.fetch("/api/tasks?subjectId=" + calculus)))
        .containsExactly("Lista de derivadas", "P1 de Cálculo");
    assertThat(titles(alice.fetch("/api/tasks?withoutSubject=true")))
        .containsExactly("Renovar livros 100%");
    assertThat(titles(alice.fetch("/api/tasks?type=EXAM&type=ASSIGNMENT")))
        .containsExactly("P1 de Cálculo", "Fichamento");
    assertThat(titles(alice.fetch("/api/tasks?status=TODO,DOING")))
        .containsExactly("P1 de Cálculo", "Fichamento", "Renovar livros 100%");
    assertThat(titles(alice.fetch("/api/tasks?status=DONE"))).containsExactly("Lista de derivadas");
    // Busca sem diferenciar maiúsculas, no título e na descrição.
    assertThat(titles(alice.fetch("/api/tasks?q=cálculo"))).containsExactly("P1 de Cálculo");
    assertThat(titles(alice.fetch("/api/tasks?q=constitucionalidade")))
        .containsExactly("Fichamento");
    // Curingas do LIKE são tratados como texto.
    assertThat(titles(alice.fetch("/api/tasks?q={q}", "%"))).containsExactly("Renovar livros 100%");
    assertThat(titles(alice.fetch("/api/tasks?q={q}", "_"))).isEmpty();
    assertThat(
            titles(
                alice.fetch("/api/tasks?dueFrom=2026-11-06T00:00:00Z&dueTo=2026-11-20T12:00:00Z")))
        .containsExactly("P1 de Cálculo");
    // Combinação de filtros.
    assertThat(titles(alice.fetch("/api/tasks?subjectId=" + calculus + "&status=TODO")))
        .containsExactly("P1 de Cálculo");
  }

  @Test
  void ordersByDueWithNoDueLastAndPaginates() throws Exception {
    task(Map.of("title", "sem prazo"));
    task(Map.of("title", "depois", "dueAt", "2026-12-10T10:00:00Z"));
    task(Map.of("title", "antes", "dueAt", "2026-12-01T10:00:00Z"));

    assertThat(titles(alice.fetch("/api/tasks"))).containsExactly("antes", "depois", "sem prazo");

    JsonNode first = alice.fetch("/api/tasks?size=2&page=0");
    assertThat(titles(first)).containsExactly("antes", "depois");
    assertThat(first.path("totalElements").asLong()).isEqualTo(3);
    assertThat(first.path("totalPages").asInt()).isEqualTo(2);
    assertThat(first.path("hasNext").asBoolean()).isTrue();
    JsonNode second = alice.fetch("/api/tasks?size=2&page=1");
    assertThat(titles(second)).containsExactly("sem prazo");
    assertThat(second.path("hasNext").asBoolean()).isFalse();
  }

  @Test
  void validation() throws Exception {
    alice
        .post("/api/tasks", Map.of("title", " "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.details[?(@.field == 'title')]").exists());
    alice
        .post("/api/tasks", Map.of("title", "x".repeat(201), "description", "y".repeat(5001)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'title')]").exists())
        .andExpect(jsonPath("$.details[?(@.field == 'description')]").exists());
    alice
        .post("/api/tasks", Map.of("title", "x", "type", "LAB"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    alice
        .post("/api/tasks", Map.of("title", "x", "dueAt", "amanhã"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    alice
        .get("/api/tasks?size=500")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.details[0].field").value("size"));
    alice.get("/api/tasks?status=NOPE").andExpect(status().isBadRequest());
    alice
        .post(
            "/api/tasks", Map.of("title", "x", "subjectId", "00000000-0000-0000-0000-000000000000"))
        .andExpect(status().isNotFound());
  }

  @Test
  void deletingSubjectDeletesItsTasks() throws Exception {
    String id = task(Map.of("title", "x", "subjectId", calculus)).path("id").asText();
    alice.delete("/api/subjects/" + calculus).andExpect(status().isNoContent());
    alice.get("/api/tasks/" + id).andExpect(status().isNotFound());
  }

  @Test
  void otherUserCannotSeeOrChangeTasks() throws Exception {
    String id =
        task(Map.of("title", "segredo da Alice", "subjectId", calculus)).path("id").asText();

    bob.get("/api/tasks/" + id).andExpect(status().isNotFound());
    bob.put("/api/tasks/" + id, Map.of("title", "hack")).andExpect(status().isNotFound());
    bob.patch("/api/tasks/" + id + "/status", Map.of("status", "DONE"))
        .andExpect(status().isNotFound());
    bob.delete("/api/tasks/" + id).andExpect(status().isNotFound());

    // Lista e filtros nunca trazem tarefas de outro usuário, nem filtrando pela matéria dela.
    assertThat(bob.fetch("/api/tasks").path("totalElements").asLong()).isZero();
    assertThat(bob.fetch("/api/tasks?subjectId=" + calculus).path("totalElements").asLong())
        .isZero();
    assertThat(bob.fetch("/api/tasks?q=segredo").path("totalElements").asLong()).isZero();

    // Não cria tarefa na matéria de Alice nem move a própria para lá.
    bob.post("/api/tasks", Map.of("title", "intrusa", "subjectId", calculus))
        .andExpect(status().isNotFound());
    String bobTask = bob.create("/api/tasks", Map.of("title", "minha")).path("id").asText();
    bob.put("/api/tasks/" + bobTask, Map.of("title", "minha", "subjectId", calculus))
        .andExpect(status().isNotFound());

    JsonNode mine = alice.fetch("/api/tasks/" + id);
    assertThat(mine.path("title").asText()).isEqualTo("segredo da Alice");
    assertThat(mine.path("status").asText()).isEqualTo("TODO");
    assertThat(alice.fetch("/api/tasks").path("totalElements").asLong()).isEqualTo(1);
  }
}
