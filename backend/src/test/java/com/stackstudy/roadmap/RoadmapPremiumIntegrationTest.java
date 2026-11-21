package com.stackstudy.roadmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackstudy.support.Api;
import com.stackstudy.support.AuthTestClient;
import com.stackstudy.support.IntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** ROADMAPS_PREMIUM_ENABLED=true: etapas PREMIUM vêm bloqueadas e a escrita nelas é recusada. */
@TestPropertySource(properties = "stackstudy.roadmaps.premium-enabled=true")
class RoadmapPremiumIntegrationTest extends IntegrationTest {

  static final String CS = "/api/roadmaps/ciencia-da-computacao";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  Api alice;

  @BeforeEach
  void setUp() throws Exception {
    AuthTestClient auth = new AuthTestClient(mvc, json);
    alice = new Api(mvc, json, auth.register(AuthTestClient.uniqueEmail(), "alice-password"));
  }

  @Test
  void premiumStagesAreLockedAndHideTheirContent() throws Exception {
    JsonNode detail = alice.fetch(CS);
    assertThat(detail.path("premiumEnabled").asBoolean()).isTrue();
    JsonNode stages = detail.path("stages");
    assertThat(stages.get(0).path("locked").asBoolean()).isFalse();
    assertThat(stages.get(2).path("locked").asBoolean()).isFalse();
    JsonNode premium = stages.get(3);
    assertThat(premium.path("access").asText()).isEqualTo("PREMIUM");
    assertThat(premium.path("locked").asBoolean()).isTrue();
    // Títulos aparecem (vitrine); descrição e materiais não.
    JsonNode node = premium.path("nodes").get(0);
    assertThat(node.path("title").asText()).isEqualTo("Engenharia de software");
    assertThat(absent(node.path("description"))).isTrue();
    assertThat(node.path("resources")).isEmpty();
    // Etapa grátis continua com o conteúdo.
    assertThat(stages.get(0).path("nodes").get(0).path("resources")).isNotEmpty();
    assertThat(alice.fetch("/api/roadmaps").path("premiumEnabled").asBoolean()).isTrue();
  }

  @Test
  void writingProgressOnALockedStageIsBlocked() throws Exception {
    JsonNode detail = alice.fetch(CS);
    JsonNode locked = detail.path("stages").get(3).path("nodes").get(0);
    JsonNode open = detail.path("stages").get(0).path("nodes").get(0);

    alice
        .put(CS + "/nodes/" + locked.path("id").asText() + "/progress", Map.of("status", "DONE"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ROADMAP_STAGE_LOCKED"));
    alice
        .put(CS + "/nodes/" + open.path("id").asText() + "/progress", Map.of("status", "DONE"))
        .andExpect(status().isOk());

    // Sessão de foco ligada a um tópico bloqueado também não passa.
    String subject = alice.subject(alice.semester("2026.2"), "Software");
    alice
        .post(
            "/api/deepwork/sessions/manual",
            Map.of(
                "subjectId",
                subject,
                "startedAt",
                Instant.now().minus(2, ChronoUnit.HOURS).toString(),
                "durationMinutes",
                30,
                "roadmapNodeId",
                locked.path("id").asText()))
        .andExpect(status().isForbidden());
  }

  /** null não vem no JSON (non_null). */
  static boolean absent(JsonNode n) {
    return n.isMissingNode() || n.isNull();
  }
}
