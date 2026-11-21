package com.stackstudy.grade;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackstudy.grade.GradeCalculator.FinalExamRule;
import com.stackstudy.grade.GradeCalculator.Result;
import com.stackstudy.grade.GradeCalculator.Rule;
import com.stackstudy.grade.GradeCalculator.Standing;
import com.stackstudy.grade.GradeCalculator.SumOptions;
import com.stackstudy.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Regressão da V6 (soma de pontos): matérias e avaliações criadas na V5, em média ponderada e
 * aritmética, calculam exatamente igual depois da migration. Roda num schema separado, como a
 * migration vai rodar no Neon por cima de dados reais.
 */
class GradeMigrationV6IntegrationTest extends IntegrationTest {

  private static final String SCHEMA = "migration_v6_check";
  private static final String USER = "00000000-0000-0000-0000-0000000000a1";
  private static final String SEMESTER = "00000000-0000-0000-0000-0000000000b1";
  private static final String INHERITED = "00000000-0000-0000-0000-0000000000c1";
  private static final String ARITHMETIC = "00000000-0000-0000-0000-0000000000c2";
  private static final String WEIGHTED_EXAM = "00000000-0000-0000-0000-0000000000c3";

  @Test
  void existingSubjectsCalculateExactlyAsBefore() {
    DriverManagerDataSource ds =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    JdbcTemplate jdbc = new JdbcTemplate(ds);
    jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    ds.setSchema(SCHEMA);
    flyway(ds).target("5").load().migrate();

    jdbc.execute(
        "INSERT INTO users (id, name, email, password_hash) VALUES ('"
            + USER
            + "', 'A', 'a@x', 'h')");
    jdbc.execute(
        "INSERT INTO semesters (id, user_id, name, start_date, end_date) VALUES ('"
            + SEMESTER
            + "', '"
            + USER
            + "', 's', '2026-02-01', '2026-07-01')");
    // Preferência gravada na V5: ponderada, mínima 6, exame ligado (padrões nas outras colunas).
    jdbc.execute(
        "INSERT INTO grade_settings (user_id, grade_method, passing_grade, final_exam_enabled)"
            + " VALUES ('"
            + USER
            + "', 'WEIGHTED', 6, TRUE)");
    jdbc.execute(
        "INSERT INTO subjects (id, semester_id, name, color, grade_method) VALUES "
            + "('"
            + INHERITED
            + "', '"
            + SEMESTER
            + "', 'herda', '#6aa7ff', NULL),"
            + "('"
            + ARITHMETIC
            + "', '"
            + SEMESTER
            + "', 'aritmética', '#f2b861', 'ARITHMETIC'),"
            + "('"
            + WEIGHTED_EXAM
            + "', '"
            + SEMESTER
            + "', 'exame', '#bd6cf7', 'WEIGHTED')");
    insertAssessments(jdbc, INHERITED, new String[][] {{"40", "7"}, {"60", "5.5"}});
    insertAssessments(jdbc, ARITHMETIC, new String[][] {{"1", "5.99"}, {"1", "6"}, {"1", null}});
    insertAssessments(
        jdbc, WEIGHTED_EXAM, new String[][] {{"30", "3"}, {"30", "2.5"}, {"40", null}});

    Map<String, Result> before = new LinkedHashMap<>();
    for (String id : List.of(INHERITED, ARITHMETIC, WEIGHTED_EXAM)) {
      before.put(id, evaluateV5(jdbc, id));
    }

    flyway(ds).load().migrate();

    // Nada mudou nas colunas antigas; as novas nascem neutras.
    assertThat(jdbc.queryForList("SELECT grade_method FROM subjects ORDER BY id", String.class))
        .containsExactly(null, "ARITHMETIC", "WEIGHTED");
    assertThat(jdbc.queryForObject("SELECT grade_method FROM grade_settings", String.class))
        .isEqualTo("WEIGHTED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM subjects WHERE round_before_compare IS NOT NULL"
                    + " OR rounding_scale IS NOT NULL OR points_cap IS NOT NULL",
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM assessments WHERE bonus OR max_points IS NOT NULL",
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject("SELECT round_before_compare FROM grade_settings", Boolean.class))
        .isFalse();

    for (String id : List.of(INHERITED, ARITHMETIC, WEIGHTED_EXAM)) {
      assertThat(evaluateV6(jdbc, id)).as(id).isEqualTo(before.get(id));
    }
    // Conferência dos valores (os mesmos dos casos compartilhados).
    assertThat(before.get(INHERITED).standing()).isEqualTo(Standing.PASSED);
    assertThat(before.get(INHERITED).finalGrade()).isEqualByComparingTo("6.10");
    assertThat(before.get(ARITHMETIC).requiredScore()).isEqualByComparingTo("6.00");
    assertThat(before.get(WEIGHTED_EXAM).standing()).isEqualTo(Standing.EXAM);

    jdbc.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
  }

  private static void insertAssessments(JdbcTemplate jdbc, String subjectId, String[][] items) {
    for (int i = 0; i < items.length; i++) {
      jdbc.update(
          "INSERT INTO assessments (subject_id, name, weight, score, position) VALUES (?::uuid, ?,"
              + " ?, ?, ?)",
          subjectId,
          "A" + i,
          new BigDecimal(items[i][0]),
          items[i][1] == null ? null : new BigDecimal(items[i][1]),
          i);
    }
  }

  /** Como a V5 calculava: só peso e nota, regra da matéria ou da preferência. */
  private static Result evaluateV5(JdbcTemplate jdbc, String subjectId) {
    Map<String, Object> s = settingsAndSubject(jdbc, subjectId);
    List<GradeCalculator.Assessment> items =
        jdbc.query(
            "SELECT weight, score FROM assessments WHERE subject_id = ?::uuid ORDER BY position",
            (rs, n) -> new GradeCalculator.Assessment(rs.getBigDecimal(1), rs.getBigDecimal(2)),
            subjectId);
    Rule rule =
        new Rule(
            GradingRule.valueOf((String) s.get("method")),
            (BigDecimal) s.get("scale"),
            (BigDecimal) s.get("passing"),
            exam(s));
    return GradeCalculator.evaluate(rule, items, null);
  }

  /** Como a V6 calcula: com as colunas novas (que nas médias não mudam nada). */
  private static Result evaluateV6(JdbcTemplate jdbc, String subjectId) {
    Map<String, Object> s = settingsAndSubject(jdbc, subjectId);
    List<GradeCalculator.Assessment> items =
        jdbc.query(
            "SELECT weight, score, max_points, bonus FROM assessments WHERE subject_id = ?::uuid"
                + " ORDER BY position",
            (rs, n) ->
                new GradeCalculator.Assessment(
                    rs.getBigDecimal(1),
                    rs.getBigDecimal(2),
                    rs.getBigDecimal(3),
                    rs.getBoolean(4)),
            subjectId);
    Map<String, Object> extra =
        jdbc.queryForMap(
            "SELECT COALESCE(s.round_before_compare, g.round_before_compare) AS round,"
                + " COALESCE(s.rounding_scale, g.rounding_scale) AS digits, s.points_cap AS cap"
                + " FROM subjects s CROSS JOIN grade_settings g WHERE s.id = ?::uuid",
            subjectId);
    Rule rule =
        new Rule(
            GradingRule.valueOf((String) s.get("method")),
            (BigDecimal) s.get("scale"),
            (BigDecimal) s.get("passing"),
            exam(s),
            new SumOptions(
                (Boolean) extra.get("round"),
                ((Number) extra.get("digits")).intValue(),
                (BigDecimal) extra.get("cap")));
    return GradeCalculator.evaluate(rule, items, null);
  }

  private static Map<String, Object> settingsAndSubject(JdbcTemplate jdbc, String subjectId) {
    return jdbc.queryForMap(
        "SELECT COALESCE(s.grade_method, g.grade_method) AS method,"
            + " COALESCE(s.grade_scale_max, g.grade_scale_max) AS scale,"
            + " COALESCE(s.passing_grade, g.passing_grade) AS passing,"
            + " COALESCE(s.final_exam_enabled, g.final_exam_enabled) AS exam_on,"
            + " g.final_exam_min_average AS exam_min, g.final_exam_average_weight AS exam_weight,"
            + " g.final_exam_passing_grade AS exam_pass"
            + " FROM subjects s CROSS JOIN grade_settings g WHERE s.id = ?::uuid",
        subjectId);
  }

  private static FinalExamRule exam(Map<String, Object> s) {
    return Boolean.TRUE.equals(s.get("exam_on"))
        ? new FinalExamRule(
            true,
            (BigDecimal) s.get("exam_min"),
            (BigDecimal) s.get("exam_weight"),
            (BigDecimal) s.get("exam_pass"))
        : FinalExamRule.disabled();
  }

  private static FluentConfiguration flyway(DriverManagerDataSource ds) {
    return Flyway.configure().dataSource(ds).schemas(SCHEMA).locations("classpath:db/migration");
  }
}
