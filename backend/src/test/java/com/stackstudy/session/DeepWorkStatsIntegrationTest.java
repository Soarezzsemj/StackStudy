package com.stackstudy.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackstudy.support.Api;
import com.stackstudy.support.AuthTestClient;
import com.stackstudy.support.IntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Heatmap e estatísticas: agregação no fuso do usuário, por matéria, sequências e meta. */
class DeepWorkStatsIntegrationTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  Api alice;
  String anatomy;
  String law;

  @BeforeEach
  void setUp() throws Exception {
    AuthTestClient auth = new AuthTestClient(mvc, json);
    alice = new Api(mvc, json, auth.register(AuthTestClient.uniqueEmail(), "alice-password"));
    String semester = alice.semester("2026.2");
    anatomy = alice.subject(semester, "Anatomia");
    law = alice.subject(semester, "Direito Constitucional");
  }

  private void manual(String subjectId, Instant startedAt, int minutes) throws Exception {
    alice.create(
        "/api/deepwork/sessions/manual",
        Map.of(
            "subjectId", subjectId, "startedAt", startedAt.toString(), "durationMinutes", minutes));
  }

  /** Mapa dia -> segundos do heatmap. */
  private Map<String, Long> heatmap(String from, String to, String tz) throws Exception {
    JsonNode body = alice.fetch("/api/deepwork/heatmap?from={f}&to={t}&tz={z}", from, to, tz);
    assertThat(body.path("timeZone").asText()).isEqualTo(tz);
    Map<String, Long> out = new LinkedHashMap<>();
    body.path("days").forEach(d -> out.put(d.path("date").asText(), d.path("seconds").asLong()));
    return out;
  }

  @Test
  void heatmapGroupsByLocalDayInEachTimeZone() throws Exception {
    // 02:30 UTC de 10/03: ainda é dia 09 em São Paulo (UTC-3), já é 10 em Tóquio (UTC+9).
    manual(anatomy, Instant.parse("2026-03-10T02:30:00Z"), 60);
    // 23:30 UTC de 10/03: dia 10 em São Paulo, dia 11 em Tóquio.
    manual(law, Instant.parse("2026-03-10T23:30:00Z"), 30);
    // 12:00 UTC de 10/03: dia 10 em todos (09:00 em SP, 21:00 em Tóquio).
    manual(anatomy, Instant.parse("2026-03-10T12:00:00Z"), 15);

    assertThat(heatmap("2026-03-08", "2026-03-12", "UTC"))
        .containsExactly(Map.entry("2026-03-10", 6300L));
    assertThat(heatmap("2026-03-08", "2026-03-12", "America/Sao_Paulo"))
        .containsExactly(Map.entry("2026-03-09", 3600L), Map.entry("2026-03-10", 2700L));
    assertThat(heatmap("2026-03-08", "2026-03-12", "Asia/Tokyo"))
        .containsExactly(Map.entry("2026-03-10", 4500L), Map.entry("2026-03-11", 1800L));
    // O período respeita os limites do dia local.
    assertThat(heatmap("2026-03-10", "2026-03-10", "America/Sao_Paulo"))
        .containsExactly(Map.entry("2026-03-10", 2700L));
  }

  @Test
  void heatmapDefaultsToLastTwelveMonths() throws Exception {
    JsonNode body = alice.fetch("/api/deepwork/heatmap?tz=UTC");
    LocalDate to = LocalDate.parse(body.path("to").asText());
    LocalDate from = LocalDate.parse(body.path("from").asText());
    assertThat(from).isEqualTo(to.minusYears(1).plusDays(1));
  }

  @Test
  void statsSumBySubjectAndComputeStreaksInLocalTime() throws Exception {
    ZoneId zone = ZoneId.of("America/Sao_Paulo");
    Instant now = Instant.now();
    LocalDate today = LocalDate.now(zone);
    Instant todayStart = today.atStartOfDay(zone).toInstant();
    // Sessão de hoje: termina antes de "agora" (não pode estar no futuro).
    Instant todaySession =
        now.minusSeconds(120).isBefore(todayStart) ? todayStart : now.minusSeconds(120);
    manual(anatomy, todaySession, 1);
    // Ontem e anteontem ao meio-dia local: sequência de 3 dias.
    manual(law, today.minusDays(1).atTime(12, 0).atZone(zone).toInstant(), 60);
    manual(anatomy, today.minusDays(2).atTime(12, 0).atZone(zone).toInstant(), 30);
    // Sequência antiga mais longa: 4 dias seguidos há 20 dias.
    for (int i = 20; i < 24; i++) {
      manual(law, today.minusDays(i).atTime(9, 0).atZone(zone).toInstant(), 10);
    }

    JsonNode stats = alice.fetch("/api/deepwork/stats?tz={z}", "America/Sao_Paulo");
    assertThat(stats.path("today").asText()).isEqualTo(today.toString());
    assertThat(stats.path("todaySeconds").asLong()).isEqualTo(60);
    assertThat(stats.path("currentStreak").asInt()).isEqualTo(3);
    assertThat(stats.path("longestStreak").asInt()).isEqualTo(4);
    // Últimos 30 dias: 1 + 60 + 30 + 4×10 minutos.
    assertThat(stats.path("last30DaysSeconds").asLong()).isEqualTo((1 + 60 + 30 + 40) * 60);
    assertThat(stats.path("dailyAverageSeconds").asLong()).isEqualTo((1 + 60 + 30 + 40) * 60 / 30);
    assertThat(stats.path("weeklyGoalSeconds").asLong()).isEqualTo(600 * 60);

    // Mês por matéria: contém as duas, a maior primeiro, com nome e cor.
    JsonNode month = stats.path("monthBySubject");
    if (today.getDayOfMonth() >= 3) {
      assertThat(month.size()).isEqualTo(2);
      assertThat(month.get(0).path("seconds").asLong())
          .isGreaterThanOrEqualTo(month.get(1).path("seconds").asLong());
      assertThat(month.get(0).path("color").asText()).startsWith("#");
    }
    // Semana: tudo que caiu de segunda até hoje.
    long expectedWeek = 0;
    LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - 1L);
    if (!today.isBefore(monday)) expectedWeek += 60;
    if (!today.minusDays(1).isBefore(monday)) expectedWeek += 3600;
    if (!today.minusDays(2).isBefore(monday)) expectedWeek += 1800;
    assertThat(stats.path("weekSeconds").asLong()).isEqualTo(expectedWeek);
  }

  @Test
  void dayTurnDependsOnTimeZone() throws Exception {
    // 23:50 em São Paulo de ontem = 02:50 UTC de hoje. Em SP conta para ontem; em UTC, hoje.
    ZoneId sp = ZoneId.of("America/Sao_Paulo");
    LocalDate spToday = LocalDate.now(sp);
    Instant lateNight = spToday.minusDays(1).atTime(23, 50).atZone(sp).toInstant();
    manual(anatomy, lateNight, 5);

    LocalDate spDay = lateNight.atZone(sp).toLocalDate();
    LocalDate utcDay = lateNight.atZone(ZoneId.of("UTC")).toLocalDate();
    assertThat(spDay).isNotEqualTo(utcDay);
    assertThat(
            heatmap(
                spDay.minusDays(1).toString(), utcDay.plusDays(1).toString(), "America/Sao_Paulo"))
        .containsOnlyKeys(spDay.toString());
    assertThat(heatmap(spDay.minusDays(1).toString(), utcDay.plusDays(1).toString(), "UTC"))
        .containsOnlyKeys(utcDay.toString());
  }
}
