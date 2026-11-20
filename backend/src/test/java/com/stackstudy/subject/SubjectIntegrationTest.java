package com.stackstudy.subject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackstudy.support.Api;
import com.stackstudy.support.AuthTestClient;
import com.stackstudy.support.IntegrationTest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class SubjectIntegrationTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  Api alice;
  Api bob;
  String semester;

  @BeforeEach
  void setUp() throws Exception {
    AuthTestClient auth = new AuthTestClient(mvc, json);
    alice = new Api(mvc, json, auth.register(AuthTestClient.uniqueEmail(), "alice-password"));
    bob = new Api(mvc, json, auth.register(AuthTestClient.uniqueEmail(), "bob-password"));
    semester = alice.semester("2026.2");
  }

  @Test
  void crudWithAllFields() throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("semesterId", semester);
    body.put("name", " Anatomia ");
    body.put("code", "MED-110");
    body.put("professor", "Profa. Ana");
    body.put("credits", 6);
    body.put("workloadHours", 90);
    body.put("color", "#BD6CF7");
    body.put("status", "IN_PROGRESS");

    JsonNode created = alice.create("/api/subjects", body);
    String id = created.path("id").asText();
    assertThat(created.path("name").asText()).isEqualTo("Anatomia");
    assertThat(created.path("color").asText()).isEqualTo("#bd6cf7");
    assertThat(created.path("workloadHours").asInt()).isEqualTo(90);
    assertThat(created.path("pendingTasks").asLong()).isZero();

    body.put("status", "PASSED");
    body.put("code", "");
    alice
        .put("/api/subjects/" + id, body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PASSED"))
        .andExpect(jsonPath("$.code").doesNotExist());

    alice.delete("/api/subjects/" + id).andExpect(status().isNoContent());
    alice.get("/api/subjects/" + id).andExpect(status().isNotFound());
  }

  @Test
  void colorsFollowThePaletteOrderAndListIsBySemester() throws Exception {
    for (int i = 0; i < 7; i++) {
      alice.subject(semester, "Matéria " + i);
    }
    String other = alice.semester("2027.1");
    alice.subject(other, "Outra");

    JsonNode list = alice.fetch("/api/subjects?semesterId=" + semester);
    assertThat(list.size()).isEqualTo(7);
    for (int i = 0; i < 7; i++) {
      assertThat(list.get(i).path("color").asText()).isEqualTo(SubjectColors.forIndex(i));
    }
    assertThat(list.get(6).path("color").asText()).isEqualTo("#6aa7ff");
    assertThat(alice.fetch("/api/subjects").size()).isEqualTo(8);
  }

  @Test
  void pendingTasksCountsOnlyNotDone() throws Exception {
    String subject = alice.subject(semester, "Contabilidade");
    alice.create("/api/tasks", Map.of("title", "a", "subjectId", subject));
    alice.create("/api/tasks", Map.of("title", "b", "subjectId", subject, "status", "DOING"));
    alice.create("/api/tasks", Map.of("title", "c", "subjectId", subject, "status", "DONE"));

    JsonNode list = alice.fetch("/api/subjects?semesterId=" + semester);
    assertThat(list.get(0).path("pendingTasks").asLong()).isEqualTo(2);
    assertThat(alice.fetch("/api/subjects/" + subject).path("pendingTasks").asLong()).isEqualTo(2);
  }

  @Test
  void validation() throws Exception {
    alice
        .post(
            "/api/subjects",
            Map.of("semesterId", semester, "name", "x", "color", "#123456", "credits", -1))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.details[?(@.field == 'color')]").exists())
        .andExpect(jsonPath("$.details[?(@.field == 'credits')]").exists());

    alice
        .post("/api/subjects", Map.of("name", "sem semestre"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'semesterId')]").exists());

    alice
        .post("/api/subjects", Map.of("semesterId", semester, "name", "x", "status", "NOPE"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
  }

  @Test
  void otherUserCannotSeeChangeOrUseSubjectOrSemester() throws Exception {
    String subject = alice.subject(semester, "Direito Constitucional");

    bob.get("/api/subjects/" + subject).andExpect(status().isNotFound());
    bob.put("/api/subjects/" + subject, Map.of("semesterId", semester, "name", "hack"))
        .andExpect(status().isNotFound());
    bob.delete("/api/subjects/" + subject).andExpect(status().isNotFound());
    // Não lista as matérias de Alice, nem pelo semestre dela.
    assertThat(bob.fetch("/api/subjects").size()).isZero();
    bob.get("/api/subjects?semesterId=" + semester).andExpect(status().isNotFound());
    // Não cria matéria no semestre de Alice.
    bob.post("/api/subjects", Map.of("semesterId", semester, "name", "intrusa"))
        .andExpect(status().isNotFound());
    // Não move a própria matéria para o semestre de Alice.
    String bobSemester = bob.semester("bob");
    String bobSubject = bob.subject(bobSemester, "minha");
    bob.put("/api/subjects/" + bobSubject, Map.of("semesterId", semester, "name", "minha"))
        .andExpect(status().isNotFound());

    assertThat(alice.fetch("/api/subjects/" + subject).path("name").asText())
        .isEqualTo("Direito Constitucional");
    assertThat(alice.fetch("/api/subjects?semesterId=" + semester).size()).isEqualTo(1);
  }
}
