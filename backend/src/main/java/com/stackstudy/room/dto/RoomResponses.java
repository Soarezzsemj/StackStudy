package com.stackstudy.room.dto;

import com.stackstudy.room.RoomEnums.MemberRole;
import com.stackstudy.room.RoomEnums.PomodoroPhase;
import com.stackstudy.room.RoomEnums.PresenceState;
import com.stackstudy.room.RoomEnums.Reaction;
import com.stackstudy.room.RoomEnums.ReportReason;
import com.stackstudy.room.RoomEnums.RoomMode;
import com.stackstudy.room.RoomEnums.Visibility;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Respostas das salas. Nenhuma traz e-mail ou nome: só o apelido escolhido na sala. */
public final class RoomResponses {

  private RoomResponses() {}

  /** "Minhas salas". */
  public record RoomItem(
      UUID id,
      String name,
      Visibility visibility,
      MemberRole role,
      int memberCount,
      int maxMembers,
      int studyingNow,
      RoomMode defaultMode,
      Integer weeklyGoalHours) {}

  /** Sala pública na busca: só nome e números (os membros só aparecem para quem entra). */
  public record PublicRoom(
      UUID id,
      String name,
      int memberCount,
      int maxMembers,
      int studyingNow,
      RoomMode defaultMode,
      Integer weeklyGoalHours,
      boolean member,
      boolean full) {}

  public record PomodoroConfig(
      int focusMinutes,
      int shortBreakMinutes,
      int longBreakMinutes,
      int longBreakEvery,
      Instant cycleStartedAt) {}

  /** Fase atual do ciclo compartilhado (igual para todos). */
  public record Phase(PomodoroPhase phase, int cycle, int of, Instant startedAt, Instant endsAt) {}

  public record Me(
      String nickname,
      MemberRole role,
      boolean showSubject,
      boolean hideFromRanking,
      boolean muted) {}

  /** Meus totais nesta sala (encerradas + a sessão em andamento). */
  public record MyStats(long todaySeconds, long weekSeconds, long longestSeconds) {}

  /** Tempos do modelo, para o app explicar e agendar heartbeats. */
  public record Timing(
      int heartbeatSeconds,
      int absentAfterSeconds,
      int saveEveryMinutes,
      int confirmEveryMinutes,
      int confirmGraceMinutes) {}

  /**
   * Minha sessão na sala.
   *
   * @param countedSeconds contado pelo servidor até {@code serverTime} (inclui o tempo à espera de
   *     confirmação)
   * @param running o tempo está contando agora (estudando; no Pomodoro, em fase de foco)
   * @param confirmDeadline "Ainda está estudando?" em aberto: responder até aqui
   */
  public record SessionView(
      UUID sessionId,
      UUID roomId,
      RoomMode mode,
      PresenceState state,
      Instant startedAt,
      long countedSeconds,
      boolean running,
      int cyclesCompleted,
      Instant confirmDeadline,
      Phase phase,
      Instant serverTime) {}

  /**
   * Sala completa (só para membros).
   *
   * @param inviteCode só para o dono ({@code XXXX-XXXX-XXXX})
   * @param weekSeconds total da sala na semana (todos os membros), para a meta
   * @param session minha sessão, se estiver nesta sala
   * @param activeElsewhere minha sessão ativa está em outra sala (id) ou no Deep Work
   */
  public record RoomDetail(
      UUID id,
      String name,
      Visibility visibility,
      boolean owner,
      String inviteCode,
      int memberCount,
      int maxMembers,
      Integer weeklyGoalHours,
      long weekSeconds,
      RoomMode defaultMode,
      PomodoroConfig pomodoro,
      Phase phase,
      Me me,
      MyStats myStats,
      SessionView session,
      String activeElsewhere,
      Timing timing,
      Instant serverTime) {}

  /**
   * Membro na presença.
   *
   * @param subject só se o membro permitiu e há matéria na sessão
   */
  public record MemberPresence(
      UUID userId,
      String nickname,
      MemberRole role,
      boolean me,
      boolean muted,
      PresenceState state,
      RoomMode mode,
      Instant since,
      long sessionSeconds,
      int cyclesCompleted,
      String subject) {}

  public record ReactionView(String fromNickname, Reaction kind, Instant at) {}

  public record Presence(
      int studyingNow,
      Phase phase,
      List<MemberPresence> members,
      List<ReactionView> reactions,
      Instant serverTime) {}

  public record RankEntry(int position, String nickname, boolean me, long seconds, long cycles) {}

  public record Ranking(String period, RoomMode mode, List<RankEntry> entries) {}

  public record BlockedUser(UUID userId, String nickname) {}

  /** Denúncia para o admin (sem e-mail de ninguém). */
  public record ReportView(
      UUID id,
      Instant createdAt,
      ReportReason reason,
      String detail,
      String roomName,
      boolean roomDeleted,
      String reportedNickname) {}
}
