package com.stackstudy.subject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackstudy.support.Api;
import com.stackstudy.support.AuthTestClient;
import com.stackstudy.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class SemesterIntegrationTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  Api alice;
  Api bob;

  @BeforeEach
  void setUp() throws Exception {
    AuthTestClient auth = new AuthTestClient(mvc, json);
    alice = new Api(mvc, json, auth.register(AuthTestClient.uniqueEmail(), "alice-password"));
    bob = new Api(mvc, json, auth.register(AuthTestClient.uniqueEmail(), "bob-password"));
  }

  @Test
  void firstSemesterBecomesCurrentAndOnlyOneIsCurrent() throws Exception {
    String first = alice.semester("2026.1");
    String second = alice.semester("2026.2");

    assertThat(alice.fetch("/api/semesters/" + first).path("current").asBoolean()).isTrue();
    assertThat(alice.fetch("/api/semesters/" + second).path("current").asBoolean()).isFalse();

    alice
        .post("/api/semesters/" + second + "/current")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.current").value(true));

    JsonNode list = alice.fetch("/api/semesters");
    long currents = 0;
    for (JsonNode s : list) {
      if (s.path("current").asBoolean()) currents++;
    }
    assertThat(currents).isEqualTo(1);
    assertThat(alice.fetch("/api/semesters/" + first).path("current").asBoolean()).isFalse();
  }

  @Test
  void updateChangesNameAndPeriod() throws Exception {
    String id = alice.semester("2026.1");
    alice
        .put(
            "/api/semesters/" + id,
            Map.of("name", "  2026 / 1  ", "startDate", "2026-02-01", "endDate", "2026-07-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("2026 / 1"))
        .andExpect(jsonPath("$.startDate").value("2026-02-01"))
        .andExpect(jsonPath("$.current").value(true));
  }

  @Test
  void archivingCurrentPromotesAnotherAndArchivedCannotBeCurrent() throws Exception {
    String older = alice.semester("2026.1");
    String newer = alice.semester("2026.2");

    alice
        .post("/api/semesters/" + older + "/archive")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived").value(true))
        .andExpect(jsonPath("$.current").value(false));
    assertThat(alice.fetch("/api/semesters/" + newer).path("current").asBoolean()).isTrue();

    alice
        .post("/api/semesters/" + older + "/current")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SEMESTER_ARCHIVED"));

    alice
        .post("/api/semesters/" + older + "/unarchive")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived").value(false))
        .andExpect(jsonPath("$.current").value(false));
  }

  @Test
  void validationErrorsUseStandardFormat() throws Exception {
    alice
        .post("/api/semesters", Map.of("name", "", "startDate", "2026-08-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.details[?(@.field == 'name')]").exists())
        .andExpect(jsonPath("$.details[?(@.field == 'endDate')]").exists());
  }

  @Test
  void otherUserCannotReadOrChangeSemester() throws Exception {
    String id = alice.semester("2026.1");
    Map<String, String> body =
        Map.of("name", "hacked", "startDate", "2026-08-01", "endDate", "2026-12-15");

    bob.get("/api/semesters/" + id).andExpect(status().isNotFound());
    bob.put("/api/semesters/" + id, body).andExpect(status().isNotFound());
    bob.post("/api/semesters/" + id + "/archive").andExpect(status().isNotFound());
    bob.post("/api/semesters/" + id + "/current").andExpect(status().isNotFound());

    assertThat(alice.fetch("/api/semesters/" + id).path("name").asText()).isEqualTo("2026.1");
    assertThat(alice.fetch("/api/semesters/" + id).path("archived").asBoolean()).isFalse();
    // O semestre de Bob não interfere no "atual" de Alice, e vice-versa.
    String bobs = bob.semester("bob 1");
    assertThat(bob.fetch("/api/semesters/" + bobs).path("current").asBoolean()).isTrue();
    assertThat(alice.fetch("/api/semesters/" + id).path("current").asBoolean()).isTrue();
  }
}
