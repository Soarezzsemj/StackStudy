package com.stackstudy.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackstudy.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * EXPLAIN das consultas mais pesadas (as do Cockpit, da fila de flashcards e das estatísticas do
 * Deep Work) com volume realista: 300 usuários × ~40 linhas por tabela. Confere que cada uma usa o
 * índice certo e não varre a tabela inteira. Os planos ficam no log (e resumidos em
 * docs/performance.md).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueryPlanIntegrationTest extends IntegrationTest {

  private static final String TAG = "plan-" + UUID.randomUUID().toString().substring(0, 8);

  @Autowired JdbcTemplate jdbc;

  UUID user;

  @BeforeAll
  void seed() {
    jdbc.update(
        "INSERT INTO users (name, email, password_hash) SELECT 'u' || g, ? || '-' || g ||"
            + " '@example.com', 'h' FROM generate_series(1, 300) g",
        TAG);
    jdbc.update(
        "INSERT INTO semesters (user_id, name, start_date, end_date) SELECT id, 's', DATE"
            + " '2026-02-01', DATE '2026-12-01' FROM users WHERE email LIKE ? || '-%'",
        TAG);
    jdbc.update(
        "INSERT INTO subjects (semester_id, name, color) SELECT se.id, 'm', '#6aa7ff' FROM"
            + " semesters se JOIN users u ON u.id = se.user_id WHERE u.email LIKE ? || '-%'",
        TAG);
    jdbc.update(
        "INSERT INTO tasks (user_id, subject_id, title, type, status, priority, due_date) SELECT"
            + " se.user_id, s.id, 't', 'OTHER', CASE WHEN g % 3 = 0 THEN 'DONE' ELSE 'TODO' END,"
            + " 'MEDIUM', now() + (g - 20) * interval '1 day' FROM subjects s JOIN semesters se ON"
            + " se.id = s.semester_id JOIN users u ON u.id = se.user_id, generate_series(1, 40) g"
            + " WHERE u.email LIKE ? || '-%'",
        TAG);
    jdbc.update(
        "INSERT INTO study_sessions (user_id, subject_id, started_at, ended_at, mode,"
            + " effective_seconds, source) SELECT se.user_id, s.id, now() - g * interval '1 day',"
            + " now() - g * interval '1 day' + interval '25 minutes', 'FLOW', 1500, 'MANUAL' FROM"
            + " subjects s JOIN semesters se ON se.id = s.semester_id JOIN users u ON u.id ="
            + " se.user_id, generate_series(1, 40) g WHERE u.email LIKE ? || '-%'",
        TAG);
    jdbc.update(
        "INSERT INTO decks (user_id, name) SELECT id, 'd' FROM users WHERE email LIKE ? || '-%'",
        TAG);
    jdbc.update(
        "INSERT INTO cards (user_id, deck_id, front, back, due_on, last_reviewed_at) SELECT d.user_id, d.id, 'f',"
            + " 'b', CASE WHEN g % 2 = 0 THEN CURRENT_DATE + g - 20 END,"
            + " CASE WHEN g % 2 = 0 THEN now() END FROM decks d JOIN users u"
            + " ON u.id = d.user_id, generate_series(1, 40) g WHERE u.email LIKE ? || '-%'",
        TAG);
    jdbc.update(
        "INSERT INTO card_reviews (user_id, card_id, rating, quality, reviewed_at, prev_interval_days,"
            + " new_interval_days) SELECT c.user_id, c.id, 'GOOD', 4, now() - interval '1 hour', 0, 1"
            + " FROM cards c JOIN users u ON u.id = c.user_id WHERE u.email LIKE ? || '-%' AND"
            + " c.due_on IS NOT NULL",
        TAG);
    jdbc.update(
        "INSERT INTO auth_sessions (user_id, last_used_at, expires_at) SELECT id, now(), now() +"
            + " interval '14 days' FROM users, generate_series(1, 3) WHERE email LIKE ? || '-%'",
        TAG);
    for (String table :
        List.of(
            "users",
            "semesters",
            "subjects",
            "tasks",
            "study_sessions",
            "decks",
            "cards",
            "card_reviews",
            "auth_sessions")) {
      jdbc.execute("ANALYZE " + table);
    }
    user =
        jdbc.queryForObject(
            "SELECT id FROM users WHERE email = ? || '-150@example.com'", UUID.class, TAG);
  }

  private String plan(String sql) {
    String text = String.join("\n", jdbc.queryForList("EXPLAIN (COSTS OFF) " + sql, String.class));
    System.out.println("---- " + sql + "\n" + text);
    return text;
  }

  private void assertUsesIndex(String sql, String table, String index) {
    String p = plan(sql.replace(":u", "'" + user + "'"));
    assertThat(p).as(sql).doesNotContain("Seq Scan on " + table).contains(index);
  }

  @Test
  void cockpitOpenTasksUseTheStatusDueIndex() {
    assertUsesIndex(
        "SELECT * FROM tasks t WHERE t.user_id = :u AND t.status IN ('TODO', 'DOING') AND"
            + " t.due_date < now() + interval '7 days' ORDER BY t.due_date LIMIT 40",
        "tasks",
        "idx_tasks_user_status_due");
  }

  @Test
  void focusPerDayUsesTheUserStartedIndex() {
    assertUsesIndex(
        "SELECT CAST(s.started_at AT TIME ZONE 'UTC' AS date) AS day, SUM(s.effective_seconds)"
            + " FROM study_sessions s WHERE s.user_id = :u AND s.ended_at IS NOT NULL AND"
            + " s.started_at >= now() - interval '84 days' AND s.started_at < now() GROUP BY day",
        "study_sessions",
        "idx_study_sessions_user_started");
  }

  @Test
  void deckCountsUseAnIndexOnCardsByUser() {
    // Antes da V8 isto varria a tabela inteira: os dois índices de cards são parciais e nenhum
    // serve para "todas as cartas do usuário".
    assertUsesIndex(
        "SELECT c.deck_id, COUNT(*), COUNT(*) FILTER (WHERE c.due_on IS NULL), COUNT(*) FILTER"
            + " (WHERE c.due_on <= CURRENT_DATE) FROM cards c WHERE c.user_id = :u GROUP BY"
            + " c.deck_id",
        "cards",
        "idx_cards_user_deck");
  }

  @Test
  void dueQueueUsesThePartialDueIndex() {
    assertUsesIndex(
        "SELECT * FROM cards c WHERE c.user_id = :u AND c.due_on IS NOT NULL AND c.due_on <="
            + " CURRENT_DATE ORDER BY c.due_on LIMIT 200",
        "cards",
        "idx_cards_user_due");
  }

  @Test
  void reviewsTodayUseTheUserReviewedIndex() {
    assertUsesIndex(
        "SELECT COUNT(*) FROM card_reviews r WHERE r.user_id = :u AND r.reviewed_at >="
            + " CURRENT_DATE",
        "card_reviews",
        "idx_card_reviews_user_reviewed");
  }

  @Test
  void activeSessionsUseTheUserIndex() {
    assertUsesIndex(
        "SELECT * FROM auth_sessions s WHERE s.user_id = :u AND s.revoked_at IS NULL AND"
            + " s.expires_at > now()",
        "auth_sessions",
        "idx_auth_sessions_user_id");
  }
}
