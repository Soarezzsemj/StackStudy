package com.stackstudy.session.dto;

import com.stackstudy.session.DeepWorkSettings;
import com.stackstudy.session.SessionEnums.Mode;
import com.stackstudy.session.SessionEnums.Source;
import com.stackstudy.session.StudySession;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Respostas do Deep Work (records pequenos agrupados). */
public final class SessionResponses {

  private SessionResponses() {}

  /**
   * Sessão. {@code serverTime} deixa o frontend corrigir a diferença de relógio e calcular o timer
   * a partir dos timestamps.
   *
   * @param effectiveSeconds até agora (ativa) ou o valor final (encerrada)
   */
  public record Session(
      UUID id,
      UUID subjectId,
      UUID taskId,
      UUID roadmapNodeId,
      Mode mode,
      Source source,
      Integer plannedMinutes,
      Instant startedAt,
      Instant endedAt,
      Instant pausedAt,
      int pausedSeconds,
      long effectiveSeconds,
      boolean active,
      String note,
      Short focusRating,
      Instant serverTime) {

    public static Session from(StudySession s, Instant now) {
      return new Session(
          s.getId(),
          s.getSubjectId(),
          s.getTaskId(),
          s.getRoadmapNodeId(),
          s.getMode(),
          s.getSource(),
          s.getPlannedMinutes(),
          s.getStartedAt(),
          s.getEndedAt(),
          s.getPausedAt(),
          s.getPausedSeconds(),
          s.effectiveSecondsAt(now),
          s.isActive(),
          s.getNote(),
          s.getFocusRating(),
          now);
    }
  }

  public record Settings(
      int focusMinutes,
      int shortBreakMinutes,
      int longBreakMinutes,
      int longBreakEvery,
      int weeklyGoalMinutes,
      boolean soundEnabled,
      boolean notificationsEnabled) {

    public static Settings from(DeepWorkSettings s) {
      return new Settings(
          s.getFocusMinutes(),
          s.getShortBreakMinutes(),
          s.getLongBreakMinutes(),
          s.getLongBreakEvery(),
          s.getWeeklyGoalMinutes(),
          s.isSoundEnabled(),
          s.isNotificationsEnabled());
    }
  }

  public record DayTotal(LocalDate date, long seconds) {}

  /** Totais por dia local; dias sem estudo não aparecem. */
  public record Heatmap(LocalDate from, LocalDate to, String timeZone, List<DayTotal> days) {}

  public record SubjectTotal(UUID subjectId, String name, String color, long seconds) {}

  /**
   * Estatísticas no fuso do usuário. Semana = segunda a domingo; média diária = últimos 30 dias
   * (incluindo hoje) dividido por 30.
   */
  public record Stats(
      String timeZone,
      LocalDate today,
      long todaySeconds,
      long weekSeconds,
      long monthSeconds,
      long last30DaysSeconds,
      long dailyAverageSeconds,
      int currentStreak,
      int longestStreak,
      long weeklyGoalSeconds,
      List<SubjectTotal> weekBySubject,
      List<SubjectTotal> monthBySubject) {}
}
