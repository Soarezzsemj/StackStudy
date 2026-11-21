package com.stackstudy.grade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackstudy.support.Api;
import com.stackstudy.support.AuthTestClient;
import com.stackstudy.support.IntegrationTest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** Endpoints do Grade Predictor: avaliações, regra, faltas, visão geral, CR e isolamento. */
class GradeIntegrationTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  Api alice;
  Api bob;
  String semester;
  String subject;

  @BeforeEach
  void setUp() throws Exception {
    AuthTestClient auth = new AuthTestClient(mvc, json);
    alice = new Api(mvc, json, auth.register(AuthTestClient.uniqueEmail(), "alice-password"));
    bob = new Api(mvc, json, auth.register(AuthTestClient.uniqueEmail(), "bob-password"));
    semester = alice.semester("2026.2");
    subject = subjectWith(alice, semester, "Anatomia", 4, 60);
  }

  private static String subjectWith(
      Api api, String semesterId, String name, int credits, Integer hours) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("semesterId", semesterId);
    body.put("name", name);
    body.put("credits", credits);
    body.put("workloadHours", hours);
    return api.create("/api/subjects", body).path("id").asText();
  }

  private static Map<String, Object> item(String name, Object weight, Object score) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("name", name);
    m.put("weight", weight);
    m.put("score", score);
    return m;
  }

  private JsonNode putAssessments(Api api, String subjectId, List<Map<String, Object>> items)
      throws Exception {
    return api.read(
        api.put("/api/subjects/" + subjectId + "/assessments", Map.of("items", items))
            .andExpect(status().isOk()));
  }

  @Test
  void assessmentsCrudAndCalculation() throws Exception {
    JsonNode created =
        putAssessments(
            alice,
            subject,
            List.of(item(" P1 ", 40, 7.5), item("P2", 40, null), item("Trabalho", 20, 9)));
    assertThat(created.path("assessments")).hasSize(3);
    assertThat(created.path("assessments").get(0).path("name").asText()).isEqualTo("P1");
    JsonNode result = created.path("result");
    // (40×7,5 + 20×9) / 60 = 8,00; necessária: (5,995×100 − 480) / 40 = 2,9875 → 2,99.
    assertThat(result.path("standing").asText()).isEqualTo("ON_TRACK");
    assertThat(result.path("currentAverage").decimalValue()).isEqualByComparingTo("8.00");
    assertThat(result.path("requiredScore").decimalValue()).isEqualByComparingTo("2.99");
    assertThat(result.path("gradedWeightPct").decimalValue()).isEqualByComparingTo("60");
    assertThat(created.path("rule").path("passingGrade").decimalValue()).isEqualByComparingTo("6");

    // Atualiza P2 (pelo id), remove o trabalho e cria um novo.
    String p1 = created.path("assessments").get(0).path("id").asText();
    String p2 = created.path("assessments").get(1).path("id").asText();
    Map<String, Object> keepP2 = item("P2", 40, 4);
    keepP2.put("id", p2);
    Map<String, Object> keepP1 = item("P1", 40, 7.5);
    keepP1.put("id", p1);
    JsonNode updated =
        putAssessments(alice, subject, List.of(keepP2, keepP1, item("Seminário", 20, null)));
    assertThat(updated.path("assessments").get(0).path("id").asText()).isEqualTo(p2);
    assertThat(updated.path("assessments").get(2).path("name").asText()).isEqualTo("Seminário");
    assertThat(alice.fetch("/api/subjects/" + subject + "/grades").path("assessments")).hasSize(3);

    // Lista vazia apaga tudo.
    JsonNode empty = putAssessments(alice, subject, List.of());
    assertThat(empty.path("result").path("standing").asText()).isEqualTo("NO_ASSESSMENTS");
  }

  @Test
  void assessmentValidation() throws Exception {
    alice
        .put(
            "/api/subjects/" + subject + "/assessments",
            Map.of("items", List.of(item("", 0, 11), item("ok", 1, 10.555))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.details[?(@.field == 'items[0].name')]").exists())
        .andExpect(jsonPath("$.details[?(@.field == 'items[0].weight')]").exists())
        .andExpect(jsonPath("$.details[?(@.field == 'items[1].score')]").exists());

    // Acima da escala da matéria (10).
    alice
        .put(
            "/api/subjects/" + subject + "/assessments",
            Map.of("items", List.of(item("P1", 1, 10.5))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'items[0].score')]").exists());

    // Id que não existe nesta matéria.
    Map<String, Object> ghost = item("P1", 1, 5);
    ghost.put("id", "00000000-0000-0000-0000-000000000000");
    alice
        .put("/api/subjects/" + subject + "/assessments", Map.of("items", List.of(ghost)))
        .andExpect(status().isNotFound());
  }

  @Test
  void ruleOverridesInheritAndValidate() throws Exception {
    // Preferência do usuário: escala 100, mínima 70.
    Map<String, Object> settings = new HashMap<>();
    settings.put("method", "WEIGHTED");
    settings.put("scaleMax", 100);
    settings.put("passingGrade", 70);
    settings.put("maxAbsencePct", 25);
    settings.put("finalExamEnabled", false);
    settings.put("finalExamMinAverage", 40);
    settings.put("finalExamAverageWeight", 50);
    settings.put("finalExamPassingGrade", 50);
    settings.put("gpaScale", "FOUR");
    settings.put("gpaWeight", "CREDITS");
    alice
        .put("/api/grades/settings", settings)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gpaScale").value("FOUR"));

    JsonNode inherited = alice.fetch("/api/subjects/" + subject + "/grades");
    assertThat(inherited.path("rule").path("scaleMax").decimalValue()).isEqualByComparingTo("100");
    assertThat(inherited.path("overrides").path("scaleMax").isMissingNode()).isTrue();

    // Matéria com regra própria: escala 10, mínima 5, exame ligado, base de 40 aulas.
    Map<String, Object> rule = new HashMap<>();
    rule.put("scaleMax", 10);
    rule.put("passingGrade", 5);
    rule.put("method", "ARITHMETIC");
    rule.put("finalExamEnabled", true);
    rule.put("finalExamMinAverage", 3);
    rule.put("finalExamPassingGrade", 5);
    rule.put("totalClasses", 40);
    JsonNode own =
        alice.read(
            alice.put("/api/subjects/" + subject + "/grading", rule).andExpect(status().isOk()));
    assertThat(own.path("rule").path("method").asText()).isEqualTo("ARITHMETIC");
    assertThat(own.path("rule").path("passingGrade").decimalValue()).isEqualByComparingTo("5");
    // Peso do exame não foi definido: herda 50.
    assertThat(own.path("rule").path("finalExamAverageWeight").decimalValue())
        .isEqualByComparingTo("50");
    assertThat(own.path("rule").path("attendanceBase").asInt()).isEqualTo(40);
    assertThat(own.path("overrides").path("totalClasses").asInt()).isEqualTo(40);

    // Exame feito entra no resultado.
    putAssessments(alice, subject, List.of(item("P1", 1, 4), item("P2", 1, 4)));
    rule.put("finalExamScore", 6);
    JsonNode withExam =
        alice.read(
            alice.put("/api/subjects/" + subject + "/grading", rule).andExpect(status().isOk()));
    assertThat(withExam.path("result").path("standing").asText()).isEqualTo("PASSED");
    assertThat(withExam.path("result").path("finalGrade").decimalValue())
        .isEqualByComparingTo("5.00");
    assertThat(withExam.path("finalExamScore").decimalValue()).isEqualByComparingTo("6");

    // Mínima acima da escala, e escala abaixo da maior nota lançada.
    Map<String, Object> bad = new HashMap<>(rule);
    bad.put("passingGrade", 11);
    bad.put("finalExamScore", null);
    alice
        .put("/api/subjects/" + subject + "/grading", bad)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'passingGrade')]").exists());
    Map<String, Object> smallScale = new HashMap<>();
    smallScale.put("scaleMax", 3);
    smallScale.put("passingGrade", 2);
    alice
        .put("/api/subjects/" + subject + "/grading", smallScale)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'scaleMax')]").exists());
    // Peso da média 100 não é aceito.
    alice
        .put("/api/subjects/" + subject + "/grading", Map.of("finalExamAverageWeight", 100))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'finalExamAverageWeight')]").exists());

    // Limpar a regra volta a herdar tudo.
    JsonNode cleared =
        alice.read(
            alice
                .put("/api/subjects/" + subject + "/grading", Map.of())
                .andExpect(status().isOk()));
    assertThat(cleared.path("rule").path("scaleMax").decimalValue()).isEqualByComparingTo("100");
    assertThat(cleared.path("rule").path("method").asText()).isEqualTo("WEIGHTED");
    assertThat(cleared.path("finalExamScore").isMissingNode()).isTrue();
  }

  private static Map<String, Object> points(String name, Object maxPoints, Object score) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("name", name);
    m.put("maxPoints", maxPoints);
    m.put("score", score);
    return m;
  }

  private JsonNode putRule(Api api, String subjectId, Map<String, Object> rule) throws Exception {
    return api.read(
        api.put("/api/subjects/" + subjectId + "/grading", rule).andExpect(status().isOk()));
  }

  @Test
  void sumRuleSwitchCalculateAndSwitchBack() throws Exception {
    // Começa em média ponderada, com pesos.
    JsonNode weighted =
        putAssessments(alice, subject, List.of(item("P1", 40, 2.5), item("P2", 60, null)));
    assertThat(weighted.path("result").path("sum").isMissingNode()).isTrue();

    // Troca para soma de pontos, aprova com 7.
    JsonNode switched =
        putRule(alice, subject, new HashMap<>(Map.of("method", "SUM", "passingGrade", 7)));
    assertThat(switched.path("rule").path("method").asText()).isEqualTo("SUM");
    assertThat(switched.path("rule").path("roundBeforeCompare").asBoolean()).isFalse();
    // Avaliações antigas ainda sem "quanto vale": a conta não quebra, só avisa.
    assertThat(switched.path("result").path("sum").path("missingMaxPoints").asInt()).isEqualTo(2);

    // P1 vale 3, trabalho 2, P2 4, participação 1 (fecha 10). Peso não é enviado.
    List<Map<String, Object>> items = new java.util.ArrayList<>();
    Map<String, Object> p1 = points("P1", 3, 2.5);
    p1.put("id", weighted.path("assessments").get(0).path("id").asText());
    items.add(p1);
    items.add(points("Trabalho", 2, 2));
    Map<String, Object> p2 = points("P2", 4, null);
    p2.put("id", weighted.path("assessments").get(1).path("id").asText());
    items.add(p2);
    items.add(points("Participação", 1, 1));
    JsonNode sum = putAssessments(alice, subject, items);
    JsonNode result = sum.path("result");
    assertThat(result.path("standing").asText()).isEqualTo("ON_TRACK");
    assertThat(result.path("currentAverage").decimalValue()).isEqualByComparingTo("5.5");
    assertThat(result.path("finalGrade").isMissingNode()).isTrue();
    JsonNode detail = result.path("sum");
    assertThat(detail.path("total").decimalValue()).isEqualByComparingTo("5.5");
    assertThat(detail.path("pointsToPass").decimalValue()).isEqualByComparingTo("1.5");
    assertThat(detail.path("pointsAvailable").decimalValue()).isEqualByComparingTo("4");
    assertThat(detail.path("scaleMismatch").asBoolean()).isFalse();
    assertThat(detail.path("requiredPerItem").get(2).decimalValue()).isEqualByComparingTo("1.5");
    assertThat(sum.path("assessments").get(0).path("maxPoints").decimalValue())
        .isEqualByComparingTo("3");
    // O peso antigo foi preservado.
    assertThat(sum.path("assessments").get(0).path("weight").decimalValue())
        .isEqualByComparingTo("40");

    // Bônus e teto.
    Map<String, Object> bonus = points("Extra", 1, 1);
    bonus.put("bonus", true);
    items.add(bonus);
    JsonNode withBonus = putAssessments(alice, subject, items);
    assertThat(withBonus.path("result").path("sum").path("bonusPoints").decimalValue())
        .isEqualByComparingTo("1");
    assertThat(withBonus.path("assessments").get(4).path("bonus").asBoolean()).isTrue();

    // Pontuação que não fecha 10 salva normalmente (só aviso).
    items.remove(4);
    items.remove(3);
    JsonNode nine = putAssessments(alice, subject, items);
    assertThat(nine.path("result").path("sum").path("plannedPoints").decimalValue())
        .isEqualByComparingTo("9");
    assertThat(nine.path("result").path("sum").path("scaleMismatch").asBoolean()).isTrue();

    // Volta para média ponderada: os pesos de antes valem de novo.
    JsonNode back =
        putRule(alice, subject, new HashMap<>(Map.of("method", "WEIGHTED", "passingGrade", 6)));
    assertThat(back.path("rule").path("method").asText()).isEqualTo("WEIGHTED");
    assertThat(back.path("result").path("sum").isMissingNode()).isTrue();
    assertThat(back.path("assessments").get(0).path("weight").decimalValue())
        .isEqualByComparingTo("40");
  }

  @Test
  void sumRuleValidation() throws Exception {
    putRule(alice, subject, new HashMap<>(Map.of("method", "SUM", "passingGrade", 7)));
    String path = "/api/subjects/" + subject + "/assessments";

    // Valores negativos (Bean Validation).
    alice
        .put(path, Map.of("items", List.of(points("P1", -1, -2))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'items[0].maxPoints')]").exists())
        .andExpect(jsonPath("$.details[?(@.field == 'items[0].score')]").exists());
    // Nota acima do que a avaliação vale e avaliação sem "quanto vale".
    alice
        .put(path, Map.of("items", List.of(points("P1", 3, 3.5), points("P2", null, 1))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'items[0].score')]").exists())
        .andExpect(jsonPath("$.details[?(@.field == 'items[1].maxPoints')]").exists());
    // Uma avaliação não vale mais que a escala.
    alice
        .put(path, Map.of("items", List.of(points("P1", 11, null))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'items[0].maxPoints')]").exists());

    // Regra: escala inválida, casa de arredondamento inválida, teto abaixo da mínima.
    alice
        .put("/api/subjects/" + subject + "/grading", Map.of("method", "SUM", "scaleMax", 0))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'scaleMax')]").exists());
    alice
        .put("/api/subjects/" + subject + "/grading", Map.of("method", "SUM", "scaleMax", 101))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'scaleMax')]").exists());
    alice
        .put("/api/subjects/" + subject + "/grading", Map.of("roundingScale", 3))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'roundingScale')]").exists());
    alice
        .put(
            "/api/subjects/" + subject + "/grading",
            Map.of("method", "SUM", "passingGrade", 7, "pointsCap", 6))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'pointsCap')]").exists());
    alice
        .put("/api/subjects/" + subject + "/grading", Map.of("method", "MEDIAN"))
        .andExpect(status().isBadRequest());

    // Arredondar antes de comparar: 6,95 aprova com 1 casa.
    putRule(
        alice,
        subject,
        new HashMap<>(
            Map.of(
                "method",
                "SUM",
                "passingGrade",
                7,
                "roundBeforeCompare",
                true,
                "roundingScale",
                1)));
    JsonNode rounded =
        putAssessments(alice, subject, List.of(points("P1", 5, 5), points("P2", 5, 1.95)));
    assertThat(rounded.path("result").path("standing").asText()).isEqualTo("PASSED");
    assertThat(rounded.path("result").path("finalGrade").decimalValue())
        .isEqualByComparingTo("6.95");
  }

  @Test
  void subjectCreatedWithSumRuleAndOtherUserCannotTouchIt() throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("semesterId", semester);
    body.put("name", "História");
    body.put("gradingRule", "SUM");
    JsonNode created = alice.create("/api/subjects", body);
    assertThat(created.path("gradingRule").asText()).isEqualTo("SUM");
    String id = created.path("id").asText();
    JsonNode grades = alice.fetch("/api/subjects/" + id + "/grades");
    assertThat(grades.path("rule").path("method").asText()).isEqualTo("SUM");

    // Editar a matéria sem regra volta a herdar a preferência.
    body.put("gradingRule", null);
    alice.put("/api/subjects/" + id, body).andExpect(status().isOk());
    assertThat(alice.fetch("/api/subjects/" + id + "/grades").path("rule").path("method").asText())
        .isEqualTo("WEIGHTED");

    // Outro usuário: 404 em tudo.
    bob.get("/api/subjects/" + id + "/grades").andExpect(status().isNotFound());
    bob.put("/api/subjects/" + id + "/grading", Map.of("method", "SUM"))
        .andExpect(status().isNotFound());
    bob.put("/api/subjects/" + id + "/assessments", Map.of("items", List.of(points("P1", 3, 1))))
        .andExpect(status().isNotFound());
  }

  @Test
  void settingsDefaultsAndValidation() throws Exception {
    JsonNode defaults = alice.fetch("/api/grades/settings");
    assertThat(defaults.path("method").asText()).isEqualTo("WEIGHTED");
    assertThat(defaults.path("scaleMax").decimalValue()).isEqualByComparingTo("10");
    assertThat(defaults.path("passingGrade").decimalValue()).isEqualByComparingTo("6");
    assertThat(defaults.path("maxAbsencePct").decimalValue()).isEqualByComparingTo("25");
    assertThat(defaults.path("gpaScale").asText()).isEqualTo("TEN");

    Map<String, Object> bad = new HashMap<>();
    bad.put("method", "WEIGHTED");
    bad.put("scaleMax", 10);
    bad.put("passingGrade", 12);
    bad.put("maxAbsencePct", 25);
    bad.put("finalExamEnabled", true);
    bad.put("finalExamMinAverage", 13);
    bad.put("finalExamAverageWeight", 50);
    bad.put("finalExamPassingGrade", 5);
    bad.put("gpaScale", "TEN");
    bad.put("gpaWeight", "CREDITS");
    alice
        .put("/api/grades/settings", bad)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'passingGrade')]").exists())
        .andExpect(jsonPath("$.details[?(@.field == 'finalExamMinAverage')]").exists());
    alice
        .put("/api/grades/settings", Map.of("method", "WEIGHTED"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'scaleMax')]").exists());
  }

  @Test
  void absencesCrudAndAttendance() throws Exception {
    String path = "/api/subjects/" + subject + "/absences";
    JsonNode first = alice.create(path, Map.of("date", "2026-09-01", "count", 2));
    alice.create(path, Map.of("date", "2026-09-08", "count", 10));
    // Mesmo dia de novo: 409.
    alice
        .post(path, Map.of("date", "2026-09-01", "count", 1))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ABSENCE_ALREADY_REGISTERED"));
    alice
        .post(path, Map.of("date", "2026-09-02", "count", 0))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'count')]").exists());

    JsonNode list = alice.fetch(path);
    assertThat(list).hasSize(2);
    assertThat(list.get(0).path("date").asText()).isEqualTo("2026-09-08");

    // 60 h × 25% = 15 permitidas; 12 usadas → aviso.
    JsonNode att = alice.fetch("/api/subjects/" + subject + "/grades").path("attendance");
    assertThat(att.path("used").asInt()).isEqualTo(12);
    assertThat(att.path("allowed").asInt()).isEqualTo(15);
    assertThat(att.path("remaining").asInt()).isEqualTo(3);
    assertThat(att.path("status").asText()).isEqualTo("WARNING");

    String id = first.path("id").asText();
    alice
        .put("/api/absences/" + id, Map.of("date", "2026-09-01", "count", 6))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(6));
    alice
        .put("/api/absences/" + id, Map.of("date", "2026-09-08", "count", 1))
        .andExpect(status().isConflict());
    assertThat(
            alice
                .fetch("/api/subjects/" + subject + "/grades")
                .path("attendance")
                .path("status")
                .asText())
        .isEqualTo("OVER");

    alice.delete("/api/absences/" + id).andExpect(status().isNoContent());
    assertThat(alice.fetch(path)).hasSize(1);
  }

  @Test
  void overviewAndGpaBySemesterWithCreditsAndDroppedSubjects() throws Exception {
    // Semestre atual (primeiro criado): Anatomia 4 créditos (8,0) e Direito 2 créditos (5,0).
    putAssessments(alice, subject, List.of(item("P1", 1, 8)));
    String law = subjectWith(alice, semester, "Direito", 2, 30);
    putAssessments(alice, law, List.of(item("P1", 1, 5)));
    // Trancada não entra; sem créditos também não.
    String dropped = subjectWith(alice, semester, "Trancada", 6, 60);
    putAssessments(alice, dropped, List.of(item("P1", 1, 0)));
    Map<String, Object> droppedBody = new HashMap<>();
    droppedBody.put("semesterId", semester);
    droppedBody.put("name", "Trancada");
    droppedBody.put("credits", 6);
    droppedBody.put("status", "DROPPED");
    alice.put("/api/subjects/" + dropped, droppedBody).andExpect(status().isOk());
    String noCredits = subjectWith(alice, semester, "Sem créditos", 0, null);
    putAssessments(alice, noCredits, List.of(item("P1", 1, 10)));

    // Semestre anterior: 4 créditos com 10,0 (tudo lançado).
    String old = alice.semester("2026.1");
    String history = subjectWith(alice, old, "História", 4, 60);
    putAssessments(alice, history, List.of(item("P1", 1, 10)));

    JsonNode overview = alice.fetch("/api/grades/overview");
    assertThat(overview.path("semesterId").asText()).isEqualTo(semester);
    assertThat(overview.path("subjects")).hasSize(4);
    assertThat(overview.path("settings").path("passingGrade").decimalValue())
        .isEqualByComparingTo("6");

    JsonNode gpa = overview.path("gpa");
    JsonNode current = null;
    for (JsonNode s : gpa.path("semesters")) {
      if (s.path("semesterId").asText().equals(semester)) {
        current = s;
      }
    }
    assertThat(current).isNotNull();
    // (8×4 + 5×2) / 6 = 7,00
    assertThat(current.path("value").decimalValue()).isEqualByComparingTo("7.00");
    assertThat(current.path("subjects").asInt()).isEqualTo(2);
    assertThat(current.path("excludedNoWeight").asInt()).isEqualTo(1);
    // Todas com nota completa (avaliação única lançada).
    assertThat(current.path("provisional").asInt()).isZero();
    // Acumulado: (8×4 + 5×2 + 10×4) / 10 = 8,20
    assertThat(gpa.path("cumulative").decimalValue()).isEqualByComparingTo("8.20");
    assertThat(gpa.path("cumulativeSubjects").asInt()).isEqualTo(3);

    // Outro semestre escolhido explicitamente.
    JsonNode oldView = alice.fetch("/api/grades/overview?semesterId=" + old);
    assertThat(oldView.path("subjects")).hasSize(1);

    // Escala 4.0 nas preferências muda o CR.
    Map<String, Object> settings = new HashMap<>();
    settings.put("method", "WEIGHTED");
    settings.put("scaleMax", 10);
    settings.put("passingGrade", 6);
    settings.put("maxAbsencePct", 25);
    settings.put("finalExamEnabled", false);
    settings.put("finalExamMinAverage", 4);
    settings.put("finalExamAverageWeight", 50);
    settings.put("finalExamPassingGrade", 5);
    settings.put("gpaScale", "FOUR");
    settings.put("gpaWeight", "WORKLOAD");
    alice.put("/api/grades/settings", settings).andExpect(status().isOk());
    JsonNode four = alice.fetch("/api/grades/overview").path("gpa");
    // Por horas: (3,2×60 + 2,0×30 + 4,0×60) / 150 = 3,28
    assertThat(four.path("cumulative").decimalValue()).isEqualByComparingTo("3.28");
    assertThat(four.path("weightBasis").asText()).isEqualTo("WORKLOAD");
  }

  @Test
  void overviewWithoutSemesterIsEmpty() throws Exception {
    JsonNode overview = bob.fetch("/api/grades/overview");
    assertThat(overview.path("semesterId").isMissingNode()).isTrue();
    assertThat(overview.path("subjects")).isEmpty();
    assertThat(overview.path("gpa").path("cumulative").isMissingNode()).isTrue();
  }

  @Test
  void otherUserCannotSeeOrChangeGradesOrAbsences() throws Exception {
    putAssessments(alice, subject, List.of(item("P1", 1, 8)));
    JsonNode absence =
        alice.create(
            "/api/subjects/" + subject + "/absences", Map.of("date", "2026-09-01", "count", 2));
    String absenceId = absence.path("id").asText();
    String assessmentId =
        alice
            .fetch("/api/subjects/" + subject + "/grades")
            .path("assessments")
            .get(0)
            .path("id")
            .asText();

    bob.get("/api/subjects/" + subject + "/grades").andExpect(status().isNotFound());
    bob.put("/api/subjects/" + subject + "/grading", Map.of("passingGrade", 1))
        .andExpect(status().isNotFound());
    bob.put(
            "/api/subjects/" + subject + "/assessments",
            Map.of("items", List.of(item("hack", 1, 10))))
        .andExpect(status().isNotFound());
    bob.get("/api/subjects/" + subject + "/absences").andExpect(status().isNotFound());
    bob.post("/api/subjects/" + subject + "/absences", Map.of("date", "2026-09-02", "count", 1))
        .andExpect(status().isNotFound());
    bob.put("/api/absences/" + absenceId, Map.of("date", "2026-09-03", "count", 1))
        .andExpect(status().isNotFound());
    bob.delete("/api/absences/" + absenceId).andExpect(status().isNotFound());
    bob.get("/api/grades/overview?semesterId=" + semester).andExpect(status().isNotFound());
    assertThat(bob.fetch("/api/grades/overview").path("subjects")).isEmpty();

    // Bob não consegue "roubar" a avaliação de Alice pelo id numa matéria dele.
    String bobSemester = bob.semester("bob");
    String bobSubject = bob.subject(bobSemester, "minha");
    Map<String, Object> steal = item("P1", 1, 0);
    steal.put("id", assessmentId);
    bob.put("/api/subjects/" + bobSubject + "/assessments", Map.of("items", List.of(steal)))
        .andExpect(status().isNotFound());

    JsonNode mine = alice.fetch("/api/subjects/" + subject + "/grades");
    assertThat(mine.path("assessments").get(0).path("score").decimalValue())
        .isEqualByComparingTo("8");
    assertThat(mine.path("attendance").path("used").asInt()).isEqualTo(2);
    assertThat(alice.fetch("/api/grades/settings").path("passingGrade").decimalValue())
        .isEqualByComparingTo("6");
  }

  @Test
  void deletingTheSubjectRemovesItsGradesAndAbsences() throws Exception {
    putAssessments(alice, subject, List.of(item("P1", 1, 8)));
    String absenceId =
        alice
            .create(
                "/api/subjects/" + subject + "/absences", Map.of("date", "2026-09-01", "count", 2))
            .path("id")
            .asText();
    alice.delete("/api/subjects/" + subject).andExpect(status().isNoContent());
    alice.delete("/api/absences/" + absenceId).andExpect(status().isNotFound());
    assertThat(alice.fetch("/api/grades/overview").path("subjects")).isEmpty();
  }
}
