package com.stackstudy.roadmap;

import com.stackstudy.roadmap.RoadmapEnums.ProgressStatus;
import com.stackstudy.roadmap.RoadmapProgressCalculator.NodeProgress;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Progresso dos roadmaps. Toda consulta leva o {@code user_id} do usuário logado (nunca vem do
 * request).
 */
@Repository
public class RoadmapRepository {

  /** Linha de {@code user_roadmaps}. */
  public record Follow(
      UUID roadmapId,
      Instant startedAt,
      UUID currentNodeId,
      Integer weeklyGoalHours,
      boolean primary) {}

  /** Progresso completo de um nó. */
  public record ProgressRow(
      UUID nodeId,
      ProgressStatus status,
      BigDecimal manualHours,
      int focusSeconds,
      BigDecimal hoursSpent,
      Instant completedAt) {

    public NodeProgress toProgress() {
      return new NodeProgress(status, hoursSpent);
    }
  }

  private final JdbcTemplate jdbc;
  private final EntityManager em;

  public RoadmapRepository(JdbcTemplate jdbc, EntityManager em) {
    this.jdbc = jdbc;
    this.em = em;
  }

  // seguir ---------------------------------------------------------------------

  public List<Follow> follows(UUID userId) {
    return jdbc.query(
        "SELECT roadmap_id, started_at, current_node_id, weekly_goal_hours, is_primary"
            + " FROM user_roadmaps WHERE user_id = ? ORDER BY is_primary DESC, started_at",
        (rs, i) ->
            new Follow(
                rs.getObject("roadmap_id", UUID.class),
                rs.getTimestamp("started_at").toInstant(),
                rs.getObject("current_node_id", UUID.class),
                (Integer) rs.getObject("weekly_goal_hours"),
                rs.getBoolean("is_primary")),
        userId);
  }

  public Optional<Follow> follow(UUID userId, UUID roadmapId) {
    return follows(userId).stream().filter(f -> f.roadmapId().equals(roadmapId)).findFirst();
  }

  /** Começa a seguir (idempotente). Devolve true se a linha foi criada agora. */
  public boolean insertFollow(UUID userId, UUID roadmapId, Instant now, UUID currentNodeId) {
    return jdbc.update(
            "INSERT INTO user_roadmaps (user_id, roadmap_id, started_at, current_node_id)"
                + " VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
            userId,
            roadmapId,
            Timestamp.from(now),
            currentNodeId)
        > 0;
  }

  /** Marca este como principal (desmarca os outros antes, por causa do índice único parcial). */
  public void makePrimary(UUID userId, UUID roadmapId) {
    jdbc.update(
        "UPDATE user_roadmaps SET is_primary = FALSE, updated_at = now()"
            + " WHERE user_id = ? AND is_primary AND roadmap_id <> ?",
        userId,
        roadmapId);
    jdbc.update(
        "UPDATE user_roadmaps SET is_primary = TRUE, updated_at = now()"
            + " WHERE user_id = ? AND roadmap_id = ?",
        userId,
        roadmapId);
  }

  public boolean deleteFollow(UUID userId, UUID roadmapId) {
    return jdbc.update(
            "DELETE FROM user_roadmaps WHERE user_id = ? AND roadmap_id = ?", userId, roadmapId)
        > 0;
  }

  public void setCurrentNode(UUID userId, UUID roadmapId, UUID nodeId) {
    jdbc.update(
        "UPDATE user_roadmaps SET current_node_id = ?, updated_at = now()"
            + " WHERE user_id = ? AND roadmap_id = ?",
        nodeId,
        userId,
        roadmapId);
  }

  public void setGoal(UUID userId, UUID roadmapId, int hours) {
    jdbc.update(
        "UPDATE user_roadmaps SET weekly_goal_hours = ?, updated_at = now()"
            + " WHERE user_id = ? AND roadmap_id = ?",
        hours,
        userId,
        roadmapId);
  }

  // progresso ------------------------------------------------------------------

  /** Progresso do usuário nos nós de um roadmap (join até a etapa; uma consulta). */
  public Map<UUID, ProgressRow> progress(UUID userId, UUID roadmapId) {
    Map<UUID, ProgressRow> out = new HashMap<>();
    jdbc.query(
        "SELECT p.node_id, p.status, p.manual_hours, p.focus_seconds, p.hours_spent, p.completed_at"
            + " FROM user_node_progress p JOIN roadmap_nodes n ON n.id = p.node_id"
            + " JOIN roadmap_stages s ON s.id = n.stage_id"
            + " WHERE p.user_id = ? AND s.roadmap_id = ?",
        rs -> {
          ProgressRow row = mapProgress(rs);
          out.put(row.nodeId(), row);
        },
        userId,
        roadmapId);
    return out;
  }

  /** Progresso do usuário em todos os roadmaps (para a lista), por nó. */
  public Map<UUID, NodeProgress> allProgress(UUID userId) {
    Map<UUID, NodeProgress> out = new HashMap<>();
    jdbc.query(
        "SELECT node_id, status, hours_spent FROM user_node_progress WHERE user_id = ?",
        rs -> {
          out.put(
              rs.getObject("node_id", UUID.class),
              new NodeProgress(
                  ProgressStatus.valueOf(rs.getString("status")), rs.getBigDecimal("hours_spent")));
        },
        userId);
    return out;
  }

  /**
   * Grava status e (se vier) as horas manuais. Concluir guarda a primeira data de conclusão; voltar
   * para a fazer/em andamento limpa a data.
   */
  public void upsertProgress(
      UUID userId, UUID nodeId, ProgressStatus status, BigDecimal manualHours, Instant now) {
    jdbc.update(
        "INSERT INTO user_node_progress (user_id, node_id, status, manual_hours, completed_at)"
            + " VALUES (?, ?, ?, COALESCE(?, 0), ?)"
            + " ON CONFLICT (user_id, node_id) DO UPDATE SET status = EXCLUDED.status,"
            + " manual_hours = COALESCE(?, user_node_progress.manual_hours),"
            + " completed_at = CASE WHEN EXCLUDED.status = 'DONE'"
            + " THEN COALESCE(user_node_progress.completed_at, EXCLUDED.completed_at) END,"
            + " updated_at = now()",
        userId,
        nodeId,
        status.name(),
        manualHours,
        status == ProgressStatus.DONE ? Timestamp.from(now) : null,
        manualHours);
  }

  /**
   * Recalcula as horas de Deep Work do nó a partir das sessões encerradas ligadas a ele (fonte da
   * verdade: {@code study_sessions}). Foco num tópico a fazer o põe "em andamento".
   */
  public void recomputeFocus(UUID userId, UUID nodeId) {
    jdbc.update(
        "INSERT INTO user_node_progress (user_id, node_id, status, focus_seconds)"
            + " SELECT ?, ?, CASE WHEN t.total > 0 THEN 'DOING' ELSE 'TODO' END, t.total FROM ("
            + "   SELECT COALESCE(SUM(effective_seconds), 0)::int AS total FROM study_sessions"
            + "   WHERE user_id = ? AND roadmap_node_id = ? AND ended_at IS NOT NULL) t"
            + " ON CONFLICT (user_id, node_id) DO UPDATE SET focus_seconds = EXCLUDED.focus_seconds,"
            + " status = CASE WHEN user_node_progress.status = 'TODO' AND EXCLUDED.focus_seconds > 0"
            + " THEN 'DOING' ELSE user_node_progress.status END, updated_at = now()",
        userId,
        nodeId,
        userId,
        nodeId);
  }

  // sugestões ------------------------------------------------------------------

  public UUID insertSuggestion(UUID userId, String title, String area, String details) {
    return jdbc.queryForObject(
        "INSERT INTO roadmap_suggestions (user_id, title, area, details) VALUES (?, ?, ?, ?)"
            + " RETURNING id",
        UUID.class,
        userId,
        title,
        area,
        details);
  }

  // Cockpit ----------------------------------------------------------------------

  /**
   * Linha do roadmap principal + progresso, numa consulta (Hibernate: entra na conta do Cockpit).
   */
  public record PrimaryRow(
      UUID roadmapId,
      UUID currentNodeId,
      Integer weeklyGoalHours,
      Map<UUID, NodeProgress> progress) {}

  public Optional<PrimaryRow> primaryWithProgress(UUID userId) {
    @SuppressWarnings("unchecked")
    List<Object[]> rows =
        em.createNativeQuery(
                "SELECT ur.roadmap_id, ur.current_node_id, ur.weekly_goal_hours,"
                    + " p.node_id, p.status, p.hours_spent"
                    + " FROM user_roadmaps ur"
                    + " LEFT JOIN user_node_progress p ON p.user_id = ur.user_id"
                    + " WHERE ur.user_id = :u AND ur.is_primary")
            .setParameter("u", userId)
            .getResultList();
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    Object[] first = rows.get(0);
    Map<UUID, NodeProgress> progress = new HashMap<>();
    for (Object[] r : rows) {
      if (r[3] != null) {
        progress.put(
            (UUID) r[3],
            new NodeProgress(ProgressStatus.valueOf((String) r[4]), (BigDecimal) r[5]));
      }
    }
    return Optional.of(
        new PrimaryRow(
            (UUID) first[0],
            (UUID) first[1],
            first[2] == null ? null : ((Number) first[2]).intValue(),
            progress));
  }

  private static ProgressRow mapProgress(java.sql.ResultSet rs) throws java.sql.SQLException {
    Timestamp completed = rs.getTimestamp("completed_at");
    return new ProgressRow(
        rs.getObject("node_id", UUID.class),
        ProgressStatus.valueOf(rs.getString("status")),
        rs.getBigDecimal("manual_hours"),
        rs.getInt("focus_seconds"),
        rs.getBigDecimal("hours_spent"),
        completed == null ? null : completed.toInstant());
  }
}
