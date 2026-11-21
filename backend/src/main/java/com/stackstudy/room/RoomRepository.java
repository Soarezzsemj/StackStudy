package com.stackstudy.room;

import com.stackstudy.room.RoomEnums.EndedBy;
import com.stackstudy.room.RoomEnums.MemberRole;
import com.stackstudy.room.RoomEnums.RoomMode;
import com.stackstudy.room.RoomEnums.Visibility;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Salas, membros, moderação e as linhas de sessão da sala (em {@code study_sessions}). Toda
 * consulta de membro ou de sessão leva o usuário logado ou a sala já conferida no service.
 */
@Repository
public class RoomRepository {

  public record RoomRow(
      UUID id,
      String name,
      String inviteCode,
      UUID ownerId,
      Visibility visibility,
      int maxMembers,
      Integer weeklyGoalHours,
      RoomMode defaultMode,
      RoomPomodoro pomodoro,
      Instant cycleStartedAt,
      boolean active) {}

  public record MemberRow(
      UUID userId,
      MemberRole role,
      String nickname,
      boolean showSubject,
      boolean hideFromRanking,
      boolean muted,
      boolean banned,
      Instant joinedAt) {}

  /** Item de "minhas salas" ou da lista pública. */
  public record RoomListRow(
      UUID id,
      String name,
      Visibility visibility,
      int maxMembers,
      Integer weeklyGoalHours,
      RoomMode defaultMode,
      int memberCount,
      MemberRole role) {}

  /** Sessão ativa da sala guardada no banco (para reconstruir a memória ou encerrar). */
  public record ActiveRow(
      UUID id,
      UUID userId,
      UUID roomId,
      RoomMode mode,
      Instant startedAt,
      int countedSeconds,
      int cycles,
      Instant lastHeartbeatAt,
      boolean paused,
      String subjectName) {}

  /** Totais encerrados de um usuário numa sala. */
  public record UserTotals(long today, long week, long longest) {}

  /** Linha do ranking (só sessões encerradas; as ativas vêm da memória). */
  public record RankRow(UUID userId, long seconds, long cycles) {}

  private static final String ROOM_COLUMNS =
      "id, name, invite_code, owner_id, visibility, max_members, weekly_goal_hours, default_mode,"
          + " focus_minutes, short_break_minutes, long_break_minutes, long_break_every,"
          + " cycle_started_at, active";

  private final JdbcTemplate jdbc;

  public RoomRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // salas ----------------------------------------------------------------------------

  public Optional<RoomRow> room(UUID id) {
    return jdbc
        .query(
            "SELECT " + ROOM_COLUMNS + " FROM study_rooms WHERE id = ?",
            RoomRepository::mapRoom,
            id)
        .stream()
        .findFirst();
  }

  public Optional<UUID> roomIdByCode(String code) {
    return jdbc
        .query(
            "SELECT id FROM study_rooms WHERE invite_code = ? AND active",
            (rs, i) -> rs.getObject(1, UUID.class),
            code)
        .stream()
        .findFirst();
  }

  public List<MemberRow> members(UUID roomId) {
    return jdbc.query(
        "SELECT user_id, role, nickname, show_subject, hide_from_ranking, muted, banned, joined_at"
            + " FROM study_room_members WHERE room_id = ? ORDER BY joined_at",
        (rs, i) ->
            new MemberRow(
                rs.getObject("user_id", UUID.class),
                MemberRole.valueOf(rs.getString("role")),
                rs.getString("nickname"),
                rs.getBoolean("show_subject"),
                rs.getBoolean("hide_from_ranking"),
                rs.getBoolean("muted"),
                rs.getBoolean("banned"),
                rs.getTimestamp("joined_at").toInstant()),
        roomId);
  }

  public UUID insertRoom(
      String name,
      String code,
      UUID ownerId,
      Visibility visibility,
      int maxMembers,
      Integer weeklyGoalHours,
      RoomMode defaultMode,
      Instant cycleStartedAt) {
    return jdbc.queryForObject(
        "INSERT INTO study_rooms (name, invite_code, owner_id, visibility, max_members,"
            + " weekly_goal_hours, default_mode, cycle_started_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
        UUID.class,
        name,
        code,
        ownerId,
        visibility.name(),
        maxMembers,
        weeklyGoalHours,
        defaultMode.name(),
        Timestamp.from(cycleStartedAt));
  }

  public void updateRoom(RoomRow r) {
    jdbc.update(
        "UPDATE study_rooms SET name = ?, visibility = ?, max_members = ?, weekly_goal_hours = ?,"
            + " default_mode = ?, focus_minutes = ?, short_break_minutes = ?,"
            + " long_break_minutes = ?, long_break_every = ?, cycle_started_at = ?,"
            + " updated_at = now() WHERE id = ?",
        r.name(),
        r.visibility().name(),
        r.maxMembers(),
        r.weeklyGoalHours(),
        r.defaultMode().name(),
        r.pomodoro().focusMinutes(),
        r.pomodoro().shortBreakMinutes(),
        r.pomodoro().longBreakMinutes(),
        r.pomodoro().every(),
        Timestamp.from(r.cycleStartedAt()),
        r.id());
  }

  public void setInviteCode(UUID roomId, String code) {
    jdbc.update(
        "UPDATE study_rooms SET invite_code = ?, updated_at = now() WHERE id = ?", code, roomId);
  }

  public void deleteRoom(UUID roomId) {
    jdbc.update("DELETE FROM study_rooms WHERE id = ?", roomId);
  }

  public int countOwned(UUID userId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM study_rooms WHERE owner_id = ? AND active", Integer.class, userId);
  }

  public int countMemberships(UUID userId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM study_room_members m JOIN study_rooms r ON r.id = m.room_id"
            + " WHERE m.user_id = ? AND NOT m.banned AND r.active",
        Integer.class,
        userId);
  }

  public List<RoomListRow> myRooms(UUID userId) {
    return jdbc.query(
        "SELECT r.id, r.name, r.visibility, r.max_members, r.weekly_goal_hours, r.default_mode,"
            + " (SELECT count(*) FROM study_room_members x WHERE x.room_id = r.id AND NOT x.banned)"
            + " AS member_count, m.role"
            + " FROM study_room_members m JOIN study_rooms r ON r.id = m.room_id"
            + " WHERE m.user_id = ? AND NOT m.banned AND r.active ORDER BY m.joined_at",
        RoomRepository::mapListRow,
        userId);
  }

  /** Salas públicas: só nome e números (nunca os membros). */
  public List<RoomListRow> publicRooms(String q, int limit) {
    String like = q == null || q.isBlank() ? null : "%" + escapeLike(q.trim()) + "%";
    return jdbc.query(
        "SELECT r.id, r.name, r.visibility, r.max_members, r.weekly_goal_hours, r.default_mode,"
            + " (SELECT count(*) FROM study_room_members x WHERE x.room_id = r.id AND NOT x.banned)"
            + " AS member_count, NULL AS role"
            + " FROM study_rooms r WHERE r.active AND r.visibility = 'PUBLIC'"
            + " AND (CAST(? AS text) IS NULL OR r.name ILIKE CAST(? AS text) ESCAPE '\\')"
            + " ORDER BY r.created_at DESC LIMIT ?",
        RoomRepository::mapListRow,
        like,
        like,
        limit);
  }

  // membros --------------------------------------------------------------------------

  public void insertMember(UUID roomId, UUID userId, MemberRole role, String nickname) {
    jdbc.update(
        "INSERT INTO study_room_members (room_id, user_id, role, nickname, hide_from_ranking)"
            + " SELECT ?, u.id, ?, ?, NOT u.rooms_visible FROM users u WHERE u.id = ?",
        roomId,
        role.name(),
        nickname,
        userId);
  }

  public void updateMe(
      UUID roomId, UUID userId, String nickname, boolean showSubject, boolean hideFromRanking) {
    jdbc.update(
        "UPDATE study_room_members SET nickname = ?, show_subject = ?, hide_from_ranking = ?,"
            + " updated_at = now() WHERE room_id = ? AND user_id = ?",
        nickname,
        showSubject,
        hideFromRanking,
        roomId,
        userId);
  }

  public void setMuted(UUID roomId, UUID userId, boolean muted) {
    jdbc.update(
        "UPDATE study_room_members SET muted = ?, updated_at = now() WHERE room_id = ? AND user_id = ?",
        muted,
        roomId,
        userId);
  }

  /** Removido pelo dono: a linha fica marcada, para não voltar com o mesmo convite. */
  public void ban(UUID roomId, UUID userId) {
    jdbc.update(
        "UPDATE study_room_members SET banned = TRUE, updated_at = now()"
            + " WHERE room_id = ? AND user_id = ?",
        roomId,
        userId);
  }

  public void deleteMember(UUID roomId, UUID userId) {
    jdbc.update("DELETE FROM study_room_members WHERE room_id = ? AND user_id = ?", roomId, userId);
  }

  // moderação ------------------------------------------------------------------------

  public void insertReport(
      UUID reporterId,
      UUID roomId,
      UUID reportedUserId,
      String roomName,
      String reportedNickname,
      String reason,
      String detail) {
    jdbc.update(
        "INSERT INTO room_reports (reporter_id, room_id, reported_user_id, room_name,"
            + " reported_nickname, reason, detail) VALUES (?, ?, ?, ?, ?, ?, ?)",
        reporterId,
        roomId,
        reportedUserId,
        roomName,
        reportedNickname,
        reason,
        detail);
  }

  public boolean userExists(UUID userId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM users WHERE id = ?)", Boolean.class, userId));
  }

  public void block(UUID blocker, UUID blocked) {
    jdbc.update(
        "INSERT INTO user_blocks (blocker_id, blocked_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
        blocker,
        blocked);
  }

  public void unblock(UUID blocker, UUID blocked) {
    jdbc.update(
        "DELETE FROM user_blocks WHERE blocker_id = ? AND blocked_id = ?", blocker, blocked);
  }

  /** Quem a pessoa bloqueou ou quem a bloqueou (a regra vale para os dois lados). */
  public Set<UUID> blockRelations(UUID userId) {
    return new HashSet<>(
        jdbc.query(
            "SELECT blocked_id FROM user_blocks WHERE blocker_id = ?"
                + " UNION SELECT blocker_id FROM user_blocks WHERE blocked_id = ?",
            (rs, i) -> rs.getObject(1, UUID.class),
            userId,
            userId));
  }

  /** Quem eu bloqueei, com um apelido de alguma sala em comum (para desbloquear). */
  public List<Object[]> myBlocks(UUID userId) {
    return jdbc.query(
        "SELECT b.blocked_id, (SELECT m.nickname FROM study_room_members m"
            + " WHERE m.user_id = b.blocked_id ORDER BY m.joined_at DESC LIMIT 1)"
            + " FROM user_blocks b WHERE b.blocker_id = ? ORDER BY b.created_at",
        (rs, i) -> new Object[] {rs.getObject(1, UUID.class), rs.getString(2)},
        userId);
  }

  // sessões da sala ------------------------------------------------------------------

  public UUID insertSession(
      UUID userId, UUID roomId, UUID subjectId, RoomMode mode, Instant startedAt) {
    return jdbc.queryForObject(
        "INSERT INTO study_sessions (user_id, subject_id, room_id, mode, source, started_at,"
            + " last_heartbeat_at) VALUES (?, ?, ?, ?, 'ROOM', ?, ?) RETURNING id",
        UUID.class,
        userId,
        subjectId,
        roomId,
        mode == RoomMode.POMODORO ? "POMODORO" : "FLOW",
        Timestamp.from(startedAt),
        Timestamp.from(startedAt));
  }

  /** Salvamento periódico (a cada 30 min) e ao pausar/retomar. */
  public void saveProgress(
      UUID sessionId, long counted, int cycles, Instant lastHeartbeat, Instant pausedAt) {
    jdbc.update(
        "UPDATE study_sessions SET counted_seconds = ?, cycles_completed = ?,"
            + " last_heartbeat_at = ?, paused_at = ?, updated_at = now()"
            + " WHERE id = ? AND ended_at IS NULL",
        (int) counted,
        cycles,
        Timestamp.from(lastHeartbeat),
        pausedAt == null ? null : Timestamp.from(pausedAt),
        sessionId);
  }

  /** Encerra: o contado vira o tempo efetivo (é ele que entra no Deep Work e no ranking). */
  public boolean close(UUID sessionId, long counted, int cycles, Instant endedAt, EndedBy by) {
    return jdbc.update(
            "UPDATE study_sessions SET ended_at = GREATEST(?, started_at), effective_seconds = ?,"
                + " counted_seconds = ?, cycles_completed = ?, last_heartbeat_at = ?,"
                + " paused_at = NULL, ended_by = ?, updated_at = now()"
                + " WHERE id = ? AND ended_at IS NULL",
            Timestamp.from(endedAt),
            (int) counted,
            (int) counted,
            cycles,
            Timestamp.from(endedAt),
            by.name(),
            sessionId)
        > 0;
  }

  public Optional<ActiveRow> activeOf(UUID userId) {
    return jdbc
        .query(
            "SELECT s.id, s.user_id, s.room_id, s.mode, s.started_at, s.counted_seconds,"
                + " s.cycles_completed, s.last_heartbeat_at, s.paused_at IS NOT NULL AS paused,"
                + " sub.name AS subject_name"
                + " FROM study_sessions s LEFT JOIN subjects sub ON sub.id = s.subject_id"
                + " WHERE s.user_id = ? AND s.ended_at IS NULL AND s.source = 'ROOM'",
            RoomRepository::mapActive,
            userId)
        .stream()
        .findFirst();
  }

  /** Sessão ativa do Deep Work (fora de sala): impede começar a da sala ao mesmo tempo. */
  public boolean hasActiveDeepWork(UUID userId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM study_sessions WHERE user_id = ? AND ended_at IS NULL"
                + " AND source <> 'ROOM')",
            Boolean.class,
            userId));
  }

  public List<ActiveRow> activeInRoom(UUID roomId) {
    return jdbc.query(
        "SELECT s.id, s.user_id, s.room_id, s.mode, s.started_at, s.counted_seconds,"
            + " s.cycles_completed, s.last_heartbeat_at, s.paused_at IS NOT NULL AS paused,"
            + " NULL AS subject_name FROM study_sessions s"
            + " WHERE s.room_id = ? AND s.ended_at IS NULL",
        RoomRepository::mapActive,
        roomId);
  }

  public String subjectName(UUID userId, UUID subjectId) {
    return jdbc
        .query(
            "SELECT s.name FROM subjects s JOIN semesters se ON se.id = s.semester_id"
                + " WHERE s.id = ? AND se.user_id = ?",
            (rs, i) -> rs.getString(1),
            subjectId,
            userId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  /** Hoje, semana e maior sessão (encerradas) do usuário nesta sala. */
  public UserTotals totals(UUID userId, UUID roomId, Instant dayStart, Instant weekStart) {
    return jdbc.queryForObject(
        "SELECT COALESCE(SUM(effective_seconds) FILTER (WHERE started_at >= ?), 0),"
            + " COALESCE(SUM(effective_seconds) FILTER (WHERE started_at >= ?), 0),"
            + " COALESCE(MAX(effective_seconds), 0)"
            + " FROM study_sessions WHERE user_id = ? AND room_id = ? AND ended_at IS NOT NULL",
        (rs, i) -> new UserTotals(rs.getLong(1), rs.getLong(2), rs.getLong(3)),
        Timestamp.from(dayStart),
        Timestamp.from(weekStart),
        userId,
        roomId);
  }

  /** Soma da sala na semana (sessões encerradas de todos), para a meta da sala. */
  public long roomSeconds(UUID roomId, Instant from) {
    Long v =
        jdbc.queryForObject(
            "SELECT COALESCE(SUM(effective_seconds), 0) FROM study_sessions"
                + " WHERE room_id = ? AND ended_at IS NOT NULL AND started_at >= ?",
            Long.class,
            roomId,
            Timestamp.from(from));
    return v == null ? 0 : v;
  }

  public List<RankRow> ranking(UUID roomId, RoomMode mode, Instant from) {
    return jdbc.query(
        "SELECT user_id, COALESCE(SUM(effective_seconds), 0), COALESCE(SUM(cycles_completed), 0)"
            + " FROM study_sessions WHERE room_id = ? AND ended_at IS NOT NULL"
            + " AND started_at >= ? AND mode = ? GROUP BY user_id",
        (rs, i) -> new RankRow(rs.getObject(1, UUID.class), rs.getLong(2), rs.getLong(3)),
        roomId,
        Timestamp.from(from),
        mode == RoomMode.POMODORO ? "POMODORO" : "FLOW");
  }

  /** LGPD: apaga o histórico (encerrado) do usuário nesta sala. */
  public int deleteHistory(UUID userId, UUID roomId) {
    return jdbc.update(
        "DELETE FROM study_sessions WHERE user_id = ? AND room_id = ? AND ended_at IS NOT NULL",
        userId,
        roomId);
  }

  // mapeamento -----------------------------------------------------------------------

  private static RoomRow mapRoom(ResultSet rs, int i) throws SQLException {
    return new RoomRow(
        rs.getObject("id", UUID.class),
        rs.getString("name"),
        rs.getString("invite_code"),
        rs.getObject("owner_id", UUID.class),
        Visibility.valueOf(rs.getString("visibility")),
        rs.getInt("max_members"),
        (Integer) rs.getObject("weekly_goal_hours"),
        RoomMode.valueOf(rs.getString("default_mode")),
        new RoomPomodoro(
            rs.getInt("focus_minutes"),
            rs.getInt("short_break_minutes"),
            rs.getInt("long_break_minutes"),
            rs.getInt("long_break_every")),
        rs.getTimestamp("cycle_started_at").toInstant(),
        rs.getBoolean("active"));
  }

  private static RoomListRow mapListRow(ResultSet rs, int i) throws SQLException {
    String role = rs.getString("role");
    return new RoomListRow(
        rs.getObject("id", UUID.class),
        rs.getString("name"),
        Visibility.valueOf(rs.getString("visibility")),
        rs.getInt("max_members"),
        (Integer) rs.getObject("weekly_goal_hours"),
        RoomMode.valueOf(rs.getString("default_mode")),
        rs.getInt("member_count"),
        role == null ? null : MemberRole.valueOf(role));
  }

  private static ActiveRow mapActive(ResultSet rs, int i) throws SQLException {
    Timestamp hb = rs.getTimestamp("last_heartbeat_at");
    Instant started = rs.getTimestamp("started_at").toInstant();
    return new ActiveRow(
        rs.getObject("id", UUID.class),
        rs.getObject("user_id", UUID.class),
        rs.getObject("room_id", UUID.class),
        "POMODORO".equals(rs.getString("mode")) ? RoomMode.POMODORO : RoomMode.FREE,
        started,
        rs.getInt("counted_seconds"),
        rs.getInt("cycles_completed"),
        hb == null ? started : hb.toInstant(),
        rs.getBoolean("paused"),
        rs.getString("subject_name"));
  }

  static String escapeLike(String s) {
    return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
