package com.stackstudy.task;

import com.stackstudy.common.persistence.BaseEntity;
import com.stackstudy.task.TaskEnums.Priority;
import com.stackstudy.task.TaskEnums.Status;
import com.stackstudy.task.TaskEnums.Type;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Tarefa. A matéria é opcional, por isso o dono fica na própria linha ({@code user_id}). Colunas
 * ainda sem uso (peso, minutos estimados, posição) não são mapeadas.
 */
@Entity
@Table(name = "tasks")
public class Task extends BaseEntity {

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "subject_id")
  private UUID subjectId;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(columnDefinition = "text")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Type type = Type.OTHER;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Priority priority = Priority.MEDIUM;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Status status = Status.TODO;

  /** Prazo como instante. Sem hora, o frontend manda o fim do dia local e marca {@code allDay}. */
  @Column(name = "due_date")
  private Instant dueAt;

  @Column(name = "due_all_day", nullable = false)
  private boolean dueAllDay;

  @Column(name = "completed_at")
  private Instant completedAt;

  protected Task() {}

  public Task(UUID userId, String title) {
    this.userId = userId;
    this.title = title;
  }

  public void update(
      UUID subjectId,
      String title,
      String description,
      Type type,
      Priority priority,
      Instant dueAt,
      boolean dueAllDay) {
    this.subjectId = subjectId;
    this.title = title;
    this.description = description;
    this.type = type;
    this.priority = priority;
    this.dueAt = dueAt;
    // Sem prazo não existe "dia inteiro".
    this.dueAllDay = dueAt != null && dueAllDay;
  }

  /** Troca o status e mantém {@code completedAt} coerente (só preenchido quando feita). */
  public void changeStatus(Status newStatus, Instant now) {
    if (newStatus == Status.DONE && this.status != Status.DONE) {
      this.completedAt = now;
    } else if (newStatus != Status.DONE) {
      this.completedAt = null;
    }
    this.status = newStatus;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getSubjectId() {
    return subjectId;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public Type getType() {
    return type;
  }

  public Priority getPriority() {
    return priority;
  }

  public Status getStatus() {
    return status;
  }

  public Instant getDueAt() {
    return dueAt;
  }

  public boolean isDueAllDay() {
    return dueAllDay;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }
}
