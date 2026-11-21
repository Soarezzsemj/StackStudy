package com.stackstudy.session;

import com.stackstudy.common.persistence.BaseEntity;
import com.stackstudy.session.SessionEnums.Mode;
import com.stackstudy.session.SessionEnums.Source;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Sessão de estudo. Ativa enquanto {@code endedAt} é nulo (no máximo uma por usuário, índice
 * parcial no banco). O tempo efetivo é sempre calculado por {@link SessionClock}.
 */
@Entity
@Table(name = "study_sessions")
public class StudySession extends BaseEntity {

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "subject_id", nullable = false)
  private UUID subjectId;

  @Column(name = "task_id")
  private UUID taskId;

  // Tópico de roadmap (opcional): as horas somam no progresso dele.
  @Column(name = "roadmap_node_id")
  private UUID roadmapNodeId;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Mode mode;

  @Column(name = "planned_minutes")
  private Integer plannedMinutes;

  @Column(name = "focus_rating")
  private Short focusRating;

  @Column(length = 500)
  private String note;

  @Column(name = "paused_at")
  private Instant pausedAt;

  @Column(name = "paused_seconds", nullable = false)
  private int pausedSeconds;

  @Column(name = "effective_seconds")
  private Integer effectiveSeconds;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Source source = Source.TIMER;

  protected StudySession() {}

  /** Sessão cronometrada que começa agora. */
  public static StudySession start(
      UUID userId, UUID subjectId, UUID taskId, Mode mode, Integer plannedMinutes, Instant now) {
    StudySession s = new StudySession();
    s.userId = userId;
    s.subjectId = subjectId;
    s.taskId = taskId;
    s.mode = mode;
    s.plannedMinutes = plannedMinutes;
    s.startedAt = now;
    s.source = Source.TIMER;
    return s;
  }

  /** Sessão passada registrada à mão (já encerrada). */
  public static StudySession manual(
      UUID userId, UUID subjectId, UUID taskId, Instant startedAt, int durationMinutes) {
    StudySession s = new StudySession();
    s.userId = userId;
    s.mode = Mode.FLOW;
    s.source = Source.MANUAL;
    s.edit(subjectId, taskId, startedAt, durationMinutes);
    return s;
  }

  public UUID getRoadmapNodeId() {
    return roadmapNodeId;
  }

  public void linkRoadmapNode(UUID nodeId) {
    this.roadmapNodeId = nodeId;
  }

  public boolean isActive() {
    return endedAt == null;
  }

  public boolean isPaused() {
    return pausedAt != null;
  }

  public void pause(Instant now) {
    if (pausedAt == null) {
      pausedAt = now;
    }
  }

  public void resume(Instant now) {
    if (pausedAt != null) {
      pausedSeconds += (int) SessionClock.pauseSeconds(pausedAt, now);
      pausedAt = null;
    }
  }

  /** Encerra e grava o tempo efetivo (uma pausa em andamento termina junto). */
  public void complete(Instant now, String note, Short focusRating) {
    effectiveSeconds =
        (int)
            SessionClock.effectiveSeconds(
                startedAt, pausedSeconds, pausedAt, now, mode, plannedMinutes);
    resume(now);
    endedAt = now;
    this.note = note;
    this.focusRating = focusRating;
  }

  /** Edição de sessão encerrada: período passa a ser início + duração, sem pausas. */
  public void edit(UUID subjectId, UUID taskId, Instant startedAt, int durationMinutes) {
    this.subjectId = subjectId;
    this.taskId = taskId;
    this.startedAt = startedAt;
    this.endedAt = startedAt.plusSeconds(durationMinutes * 60L);
    this.effectiveSeconds = durationMinutes * 60;
    this.pausedSeconds = 0;
    this.pausedAt = null;
  }

  public void describe(String note, Short focusRating) {
    this.note = note;
    this.focusRating = focusRating;
  }

  /** Segundos efetivos agora (ativa) ou o valor gravado (encerrada). */
  public long effectiveSecondsAt(Instant now) {
    if (effectiveSeconds != null) {
      return effectiveSeconds;
    }
    return SessionClock.elapsedSeconds(startedAt, pausedSeconds, pausedAt, now);
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getSubjectId() {
    return subjectId;
  }

  public UUID getTaskId() {
    return taskId;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getEndedAt() {
    return endedAt;
  }

  public Mode getMode() {
    return mode;
  }

  public Integer getPlannedMinutes() {
    return plannedMinutes;
  }

  public Short getFocusRating() {
    return focusRating;
  }

  public String getNote() {
    return note;
  }

  public Instant getPausedAt() {
    return pausedAt;
  }

  public int getPausedSeconds() {
    return pausedSeconds;
  }

  public Integer getEffectiveSeconds() {
    return effectiveSeconds;
  }

  public Source getSource() {
    return source;
  }
}
