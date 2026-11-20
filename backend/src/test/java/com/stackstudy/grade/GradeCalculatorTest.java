package com.stackstudy.grade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackstudy.grade.GradeCalculator.Assessment;
import com.stackstudy.grade.GradeCalculator.AttendanceStatus;
import com.stackstudy.grade.GradeCalculator.FinalExamRule;
import com.stackstudy.grade.GradeCalculator.GpaItem;
import com.stackstudy.grade.GradeCalculator.GpaScale;
import com.stackstudy.grade.GradeCalculator.Method;
import com.stackstudy.grade.GradeCalculator.Result;
import com.stackstudy.grade.GradeCalculator.Rule;
import com.stackstudy.grade.GradeCalculator.Standing;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Calculadora pura. Os casos de {@code grade-calculator-cases.json} também rodam no frontend
 * ({@code calculator.test.ts}): as duas implementações precisam dar o mesmo resultado.
 */
class GradeCalculatorTest {

  private static final Rule DEFAULT =
      new Rule(Method.WEIGHTED, bd("10"), bd("6"), FinalExamRule.disabled());

  private static BigDecimal bd(String v) {
    return new BigDecimal(v);
  }

  private static Assessment a(String weight, String score) {
    return new Assessment(bd(weight), score == null ? null : bd(score));
  }

  // -------------------------------------------------------------------------
  // casos compartilhados com o frontend
  // -------------------------------------------------------------------------

  static Stream<Arguments> sharedCases() throws Exception {
    try (InputStream in =
        GradeCalculatorTest.class.getResourceAsStream("/grade-calculator-cases.json")) {
      JsonNode root = new ObjectMapper().readTree(in);
      List<Arguments> out = new ArrayList<>();
      root.forEach(c -> out.add(Arguments.of(c.path("name").asText(), c)));
      return out.stream();
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("sharedCases")
  void sharedCase(String name, JsonNode c) {
    JsonNode r = c.path("rule");
    JsonNode e = r.path("exam");
    FinalExamRule exam =
        e.isNull() || e.isMissingNode()
            ? FinalExamRule.disabled()
            : new FinalExamRule(
                true,
                bd(e.path("minAverage").asText()),
                bd(e.path("averageWeight").asText()),
                bd(e.path("passingGrade").asText()));
    Rule rule =
        new Rule(
            Method.valueOf(r.path("method").asText()),
            bd(r.path("scaleMax").asText()),
            bd(r.path("passingGrade").asText()),
            exam);
    List<Assessment> items = new ArrayList<>();
    c.path("assessments")
        .forEach(
            x ->
                items.add(
                    a(
                        x.path("weight").asText(),
                        x.path("score").isNull() ? null : x.path("score").asText())));
    BigDecimal examScore = c.path("examScore").isNull() ? null : bd(c.path("examScore").asText());

    Result result = GradeCalculator.evaluate(rule, items, examScore);
    JsonNode x = c.path("expected");

    assertThat(result.standing()).isEqualTo(Standing.valueOf(x.path("standing").asText()));
    assertThat(result.complete()).isEqualTo(x.path("complete").asBoolean());
    assertDecimal("gradedWeightPct", result.gradedWeightPct(), x);
    assertDecimal("currentAverage", result.currentAverage(), x);
    assertDecimal("minimumAverage", result.minimumAverage(), x);
    assertDecimal("maximumAverage", result.maximumAverage(), x);
    assertDecimal("requiredScore", result.requiredScore(), x);
    assertDecimal("examRequired", result.examRequired(), x);
    assertDecimal("finalGrade", result.finalGrade(), x);
  }

  private static void assertDecimal(String field, BigDecimal actual, JsonNode expected) {
    JsonNode node = expected.path(field);
    if (node.isNull()) {
      assertThat(actual).as(field).isNull();
    } else {
      assertThat(actual).as(field).isEqualByComparingTo(node.asText());
    }
  }

  // -------------------------------------------------------------------------
  // notas
  // -------------------------------------------------------------------------

  @Nested
  class Grades {

    @Test
    void resultsAlwaysHaveTwoDecimals() {
      Result r = GradeCalculator.evaluate(DEFAULT, List.of(a("1", "7"), a("2", null)), null);
      assertThat(r.currentAverage().scale()).isEqualTo(2);
      assertThat(r.requiredScore().scale()).isEqualTo(2);
      assertThat(r.minimumAverage().toPlainString()).isEqualTo("2.33");
      assertThat(r.totalWeight().toPlainString()).isEqualTo("3.00");
      assertThat(r.totalCount()).isEqualTo(2);
      assertThat(r.gradedCount()).isEqualTo(1);
    }

    @Test
    void requiredScoreIsTheSmallestThatPasses() {
      // Pesos 1 e 2, nota 7: precisa x tal que (7 + 2x) / 3 arredondado ≥ 6.
      Result r = GradeCalculator.evaluate(DEFAULT, List.of(a("1", "7"), a("2", null)), null);
      BigDecimal x = r.requiredScore();
      assertThat(x).isEqualByComparingTo("5.50");
      // Conferência: com x passa; com x − 0,01 não.
      Result pass = GradeCalculator.evaluate(DEFAULT, List.of(a("1", "7"), a("2", "5.50")), null);
      Result fail = GradeCalculator.evaluate(DEFAULT, List.of(a("1", "7"), a("2", "5.49")), null);
      assertThat(pass.standing()).isEqualTo(Standing.PASSED);
      assertThat(fail.standing()).isEqualTo(Standing.FAILED);
    }

    @Test
    void requiredScoreIsConsistentWithRoundingAcrossManyInputs() {
      // Para várias notas já lançadas, a necessária aprova e 0,01 a menos reprova.
      for (int cents = 0; cents <= 1000; cents += 37) {
        BigDecimal first = BigDecimal.valueOf(cents, 2);
        Result r =
            GradeCalculator.evaluate(
                DEFAULT, List.of(a("3", first.toPlainString()), a("7", null)), null);
        BigDecimal x = r.requiredScore();
        if (x.signum() == 0 || x.compareTo(bd("10")) > 0) {
          continue;
        }
        Result ok =
            GradeCalculator.evaluate(
                DEFAULT, List.of(a("3", first.toPlainString()), a("7", x.toPlainString())), null);
        Result below =
            GradeCalculator.evaluate(
                DEFAULT,
                List.of(
                    a("3", first.toPlainString()), a("7", x.subtract(bd("0.01")).toPlainString())),
                null);
        assertThat(ok.standing()).as("first=%s x=%s", first, x).isEqualTo(Standing.PASSED);
        assertThat(below.standing()).as("first=%s x=%s", first, x).isEqualTo(Standing.FAILED);
      }
    }

    @Test
    void passingGradeAboveScaleIsImpossible() {
      Rule rule = new Rule(Method.WEIGHTED, bd("10"), bd("11"), FinalExamRule.disabled());
      Result r = GradeCalculator.evaluate(rule, List.of(a("1", null)), null);
      assertThat(r.standing()).isEqualTo(Standing.IMPOSSIBLE);
      assertThat(r.requiredScore()).isEqualByComparingTo("11.00");
    }

    @Test
    void zeroPassingGradeIsSecuredFromTheStart() {
      Rule rule = new Rule(Method.WEIGHTED, bd("10"), bd("0"), FinalExamRule.disabled());
      Result r = GradeCalculator.evaluate(rule, List.of(a("1", null)), null);
      assertThat(r.standing()).isEqualTo(Standing.SECURED);
      assertThat(r.requiredScore()).isEqualByComparingTo("0");
    }

    @Test
    void examScoreIsIgnoredWhenNotEligible() {
      Rule rule =
          new Rule(
              Method.ARITHMETIC,
              bd("10"),
              bd("6"),
              new FinalExamRule(true, bd("4"), bd("50"), bd("5")));
      // Média 7: aprovado direto, o exame não muda nada.
      Result r = GradeCalculator.evaluate(rule, List.of(a("1", "7")), bd("0"));
      assertThat(r.standing()).isEqualTo(Standing.PASSED);
      assertThat(r.finalGrade()).isEqualByComparingTo("7");
      assertThat(r.examRequired()).isNull();
    }

    @Test
    void examThatReplacesTheAverage() {
      // Peso 0 da média: o exame vale sozinho.
      FinalExamRule exam = new FinalExamRule(true, bd("0"), bd("0"), bd("5"));
      assertThat(GradeCalculator.examRequired(exam, bd("2"))).isEqualByComparingTo("5.00");
      assertThat(GradeCalculator.examResult(exam, bd("2"), bd("5"))).isEqualByComparingTo("5.00");
    }

    @Test
    void examRequiredNeverNegative() {
      FinalExamRule exam = new FinalExamRule(true, bd("0"), bd("90"), bd("5"));
      assertThat(GradeCalculator.examRequired(exam, bd("5.9"))).isEqualByComparingTo("0");
    }

    @Test
    void invalidInputsAreRejected() {
      assertThatThrownBy(
              () -> GradeCalculator.evaluate(DEFAULT, List.of(new Assessment(bd("0"), null)), null))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> GradeCalculator.evaluate(DEFAULT, List.of(a("1", "-1")), null))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> new FinalExamRule(true, bd("4"), bd("100"), bd("5")))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(
              () -> new Rule(Method.WEIGHTED, bd("0"), bd("6"), FinalExamRule.disabled()))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void arithmeticAcceptsMissingWeights() {
      Rule rule = new Rule(Method.ARITHMETIC, bd("10"), bd("6"), FinalExamRule.disabled());
      Result r =
          GradeCalculator.evaluate(
              rule, List.of(new Assessment(null, bd("8")), new Assessment(null, bd("6"))), null);
      assertThat(r.finalGrade()).isEqualByComparingTo("7");
    }
  }

  // -------------------------------------------------------------------------
  // faltas
  // -------------------------------------------------------------------------

  @Nested
  class Attendance {

    @Test
    void unknownWithoutBase() {
      var a = GradeCalculator.attendance(null, 3, bd("25"));
      assertThat(a.status()).isEqualTo(AttendanceStatus.UNKNOWN);
      assertThat(a.allowed()).isNull();
      assertThat(a.used()).isEqualTo(3);
      assertThat(GradeCalculator.attendance(0, 0, bd("25")).status())
          .isEqualTo(AttendanceStatus.UNKNOWN);
    }

    @Test
    void limitIsFloorOfPercentage() {
      // 30 aulas × 25% = 7,5 → pode faltar 7.
      var a = GradeCalculator.attendance(30, 0, bd("25"));
      assertThat(a.allowed()).isEqualTo(7);
      assertThat(a.remaining()).isEqualTo(7);
      assertThat(a.attendancePct()).isEqualByComparingTo("100.0");
      assertThat(a.status()).isEqualTo(AttendanceStatus.OK);
    }

    @Test
    void warningFromThreeQuartersAndAtTheLimit() {
      // 60 × 25% = 15. Aviso a partir de 11,25 → 12 faltas.
      assertThat(GradeCalculator.attendance(60, 11, bd("25")).status())
          .isEqualTo(AttendanceStatus.OK);
      assertThat(GradeCalculator.attendance(60, 12, bd("25")).status())
          .isEqualTo(AttendanceStatus.WARNING);
      var atLimit = GradeCalculator.attendance(60, 15, bd("25"));
      assertThat(atLimit.status()).isEqualTo(AttendanceStatus.WARNING);
      assertThat(atLimit.remaining()).isZero();
      assertThat(atLimit.attendancePct()).isEqualByComparingTo("75.0");
    }

    @Test
    void overTheLimit() {
      var a = GradeCalculator.attendance(60, 16, bd("25"));
      assertThat(a.status()).isEqualTo(AttendanceStatus.OVER);
      assertThat(a.remaining()).isEqualTo(-1);
      assertThat(a.attendancePct()).isEqualByComparingTo("73.3");
    }

    @Test
    void zeroLimitMeansAnyAbsenceIsOver() {
      assertThat(GradeCalculator.attendance(40, 0, bd("0")).status())
          .isEqualTo(AttendanceStatus.OK);
      assertThat(GradeCalculator.attendance(40, 1, bd("0")).status())
          .isEqualTo(AttendanceStatus.OVER);
    }

    @Test
    void attendanceNeverBelowZero() {
      var a = GradeCalculator.attendance(10, 15, bd("25"));
      assertThat(a.attendancePct()).isEqualByComparingTo("0.0");
      assertThat(a.remaining()).isEqualTo(-13);
    }

    @Test
    void negativeUsedIsRejected() {
      assertThatThrownBy(() -> GradeCalculator.attendance(10, -1, bd("25")))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  // -------------------------------------------------------------------------
  // CR
  // -------------------------------------------------------------------------

  @Nested
  class Gpa {

    @Test
    void weightedByCredits() {
      // (8×4 + 6×2) / 6 = 7,333… → 7,33
      BigDecimal gpa =
          GradeCalculator.gpa(
              List.of(new GpaItem(bd("8"), bd("10"), 4), new GpaItem(bd("6"), bd("10"), 2)),
              GpaScale.TEN);
      assertThat(gpa).isEqualByComparingTo("7.33");
    }

    @Test
    void differentCreditsChangeTheResult() {
      List<GpaItem> heavyOnGood =
          List.of(new GpaItem(bd("9"), bd("10"), 6), new GpaItem(bd("5"), bd("10"), 2));
      List<GpaItem> heavyOnBad =
          List.of(new GpaItem(bd("9"), bd("10"), 2), new GpaItem(bd("5"), bd("10"), 6));
      assertThat(GradeCalculator.gpa(heavyOnGood, GpaScale.TEN)).isEqualByComparingTo("8.00");
      assertThat(GradeCalculator.gpa(heavyOnBad, GpaScale.TEN)).isEqualByComparingTo("6.00");
    }

    @Test
    void convertsEachSubjectScaleLinearly() {
      // 75/100 → 7,5 na escala 10 e 3,0 na 4.0; 8/10 → 8 e 3,2.
      List<GpaItem> items =
          List.of(new GpaItem(bd("75"), bd("100"), 2), new GpaItem(bd("8"), bd("10"), 2));
      assertThat(GradeCalculator.gpa(items, GpaScale.TEN)).isEqualByComparingTo("7.75");
      assertThat(GradeCalculator.gpa(items, GpaScale.FOUR)).isEqualByComparingTo("3.10");
    }

    @Test
    void zeroWeightIsIgnoredAndEmptyIsNull() {
      assertThat(GradeCalculator.gpa(List.of(), GpaScale.TEN)).isNull();
      assertThat(GradeCalculator.gpa(List.of(new GpaItem(bd("9"), bd("10"), 0)), GpaScale.TEN))
          .isNull();
      assertThat(
              GradeCalculator.gpa(
                  List.of(new GpaItem(bd("9"), bd("10"), 0), new GpaItem(bd("5"), bd("10"), 3)),
                  GpaScale.TEN))
          .isEqualByComparingTo("5.00");
    }

    @Test
    void repeatingDecimalsRoundOnlyAtTheEnd() {
      // Escala 3: 2/3 de 10 = 6,666…; com três matérias iguais continua 6,67 (não 6,66 ou 6,69).
      List<GpaItem> items =
          List.of(
              new GpaItem(bd("2"), bd("3"), 1),
              new GpaItem(bd("2"), bd("3"), 1),
              new GpaItem(bd("2"), bd("3"), 1));
      assertThat(GradeCalculator.gpa(items, GpaScale.TEN)).isEqualByComparingTo("6.67");
    }
  }
}
