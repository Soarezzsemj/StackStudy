package com.stackstudy.account;

import com.stackstudy.common.security.CurrentUser;
import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Exportar meus dados" (LGPD, portabilidade): todas as tabelas do usuário em JSON, numa transação
 * só leitura. Consultas com escopo no usuário (direto ou pelo semestre); uma por tabela, sem N+1.
 *
 * <p>Fica de fora só o que é segredo de autenticação: hash da senha e hashes dos refresh tokens.
 * Tabela nova com dado do usuário: acrescente em {@link #QUERIES} (o teste de exportação confere
 * que nenhuma tabela com {@code user_id} ficou esquecida).
 */
@Service
public class DataExportService {

  /** Versão do formato do arquivo. */
  static final int FORMAT_VERSION = 1;

  /** Seção do JSON → consulta (sempre filtrada por {@code ?} = id do usuário). */
  static final Map<String, String> QUERIES = new LinkedHashMap<>();

  static {
    QUERIES.put(
        "profile",
        "SELECT id, name, email, locale, time_zone, course, institution, current_period, bio,"
            + " avatar_color, role, last_seen_at, nickname, study_area, focus_rhythm,"
            + " deadline_reminders, rooms_visible, terms_version, privacy_version, consent_at,"
            + " age_confirmed, age_confirmed_min, email_verified_at, google_sub IS NOT NULL AS"
            + " google_linked, created_at, updated_at FROM users WHERE id = ?");
    QUERIES.put(
        "sessions",
        "SELECT id, user_agent, created_at, last_used_at, expires_at, revoked_at"
            + " FROM auth_sessions WHERE user_id = ? ORDER BY created_at");
    QUERIES.put("semesters", "SELECT * FROM semesters WHERE user_id = ? ORDER BY start_date");
    QUERIES.put(
        "subjects",
        "SELECT s.* FROM subjects s JOIN semesters se ON se.id = s.semester_id"
            + " WHERE se.user_id = ? ORDER BY s.created_at");
    QUERIES.put("tasks", "SELECT * FROM tasks WHERE user_id = ? ORDER BY created_at");
    QUERIES.put(
        "studySessions", "SELECT * FROM study_sessions WHERE user_id = ? ORDER BY started_at");
    QUERIES.put(
        "assessments",
        "SELECT a.* FROM assessments a JOIN subjects s ON s.id = a.subject_id"
            + " JOIN semesters se ON se.id = s.semester_id WHERE se.user_id = ?"
            + " ORDER BY a.subject_id, a.position");
    QUERIES.put(
        "absences",
        "SELECT a.* FROM absences a JOIN subjects s ON s.id = a.subject_id"
            + " JOIN semesters se ON se.id = s.semester_id WHERE se.user_id = ? ORDER BY a.date");
    QUERIES.put("decks", "SELECT * FROM decks WHERE user_id = ? ORDER BY created_at");
    QUERIES.put("cards", "SELECT * FROM cards WHERE user_id = ? ORDER BY created_at");
    QUERIES.put("cardReviews", "SELECT * FROM card_reviews WHERE user_id = ? ORDER BY reviewed_at");
    QUERIES.put("deepWorkSettings", "SELECT * FROM deep_work_settings WHERE user_id = ?");
    QUERIES.put("gradeSettings", "SELECT * FROM grade_settings WHERE user_id = ?");
    QUERIES.put("flashcardSettings", "SELECT * FROM flashcard_settings WHERE user_id = ?");
    // Roadmaps (Fase 9): quais segue, progresso por tópico e sugestões enviadas.
    QUERIES.put(
        "roadmapsFollowed", "SELECT * FROM user_roadmaps WHERE user_id = ? ORDER BY started_at");
    QUERIES.put(
        "roadmapProgress",
        "SELECT * FROM user_node_progress WHERE user_id = ? ORDER BY created_at");
    QUERIES.put(
        "roadmapSuggestions",
        "SELECT * FROM roadmap_suggestions WHERE user_id = ? ORDER BY created_at");
    // Salas de estudo (Fase 10): salas criadas, onde é membro (apelido e privacidade), denúncias
    // feitas e bloqueios. As sessões da sala já saem em studySessions (source ROOM).
    QUERIES.put(
        "studyRoomsOwned",
        "SELECT id, name, visibility, max_members, weekly_goal_hours, default_mode, created_at"
            + " FROM study_rooms WHERE owner_id = ? ORDER BY created_at");
    QUERIES.put(
        "studyRoomMemberships",
        "SELECT * FROM study_room_members WHERE user_id = ? ORDER BY joined_at");
    QUERIES.put(
        "roomReportsSent",
        "SELECT id, room_name, reported_nickname, reason, detail, created_at FROM room_reports"
            + " WHERE reporter_id = ? ORDER BY created_at");
    QUERIES.put(
        "userBlocks", "SELECT blocked_id, created_at FROM user_blocks WHERE blocker_id = ?");
    // Cadastro (Fase 11): links de verificação de e-mail (sem o hash do token).
    QUERIES.put(
        "emailVerifications",
        "SELECT id, expires_at, used_at, created_at FROM email_verification_tokens"
            + " WHERE user_id = ? ORDER BY created_at");
    // Métricas de operação (Fase 8): dias com atividade e, para admins, acessos ao painel.
    QUERIES.put(
        "activityDays", "SELECT day FROM user_activity_daily WHERE user_id = ? ORDER BY day");
    QUERIES.put(
        "adminAccessLog",
        "SELECT endpoint, allowed, accessed_at FROM admin_access_log WHERE user_id = ?"
            + " ORDER BY accessed_at");
  }

  private final JdbcTemplate jdbc;
  private final CurrentUser currentUser;
  private final Clock clock;

  public DataExportService(JdbcTemplate jdbc, CurrentUser currentUser, Clock clock) {
    this.jdbc = jdbc;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> export() {
    UUID userId = currentUser.id();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("format", "stackstudy-export");
    out.put("version", FORMAT_VERSION);
    out.put("exportedAt", clock.instant());
    for (Map.Entry<String, String> q : QUERIES.entrySet()) {
      List<Map<String, Object>> rows = new ArrayList<>();
      for (Map<String, Object> row : jdbc.queryForList(q.getValue(), userId)) {
        rows.add(clean(row));
      }
      out.put(q.getKey(), "profile".equals(q.getKey()) && !rows.isEmpty() ? rows.get(0) : rows);
    }
    return out;
  }

  /** Coluna snake_case → camelCase e tipos do JDBC → tipos que o Jackson serializa bem. */
  private static Map<String, Object> clean(Map<String, Object> row) {
    Map<String, Object> out = new LinkedHashMap<>();
    row.forEach(
        (column, value) -> {
          // Defesa extra: segredo de autenticação nunca sai, mesmo se alguém usar SELECT *.
          if (column.endsWith("password_hash") || column.endsWith("token_hash")) {
            return;
          }
          out.put(camel(column), convert(value));
        });
    return out;
  }

  private static Object convert(Object value) {
    if (value instanceof Timestamp ts) {
      return ts.toInstant();
    }
    if (value instanceof java.sql.Date d) {
      return d.toLocalDate();
    }
    if (value instanceof Array array) {
      try {
        return Arrays.asList((Object[]) array.getArray());
      } catch (SQLException ex) {
        throw new IllegalStateException("Could not read array column", ex);
      }
    }
    return value;
  }

  static String camel(String column) {
    StringBuilder sb = new StringBuilder(column.length());
    boolean upper = false;
    for (char c : column.toCharArray()) {
      if (c == '_') {
        upper = true;
      } else {
        sb.append(upper ? Character.toUpperCase(c) : c);
        upper = false;
      }
    }
    return sb.toString();
  }
}
