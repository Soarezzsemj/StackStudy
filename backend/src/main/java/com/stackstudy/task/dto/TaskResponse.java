package com.stackstudy.task.dto;

import com.stackstudy.task.Task;
import com.stackstudy.task.TaskEnums.Priority;
import com.stackstudy.task.TaskEnums.Status;
import com.stackstudy.task.TaskEnums.Type;
import java.time.Instant;
import java.util.UUID;

public record TaskResponse(
    UUID id,
    UUID subjectId,
    String title,
    String description,
    Type type,
    Priority priority,
    Status status,
    Instant dueAt,
    boolean dueAllDay,
    Instant completedAt,
    Instant createdAt,
    Instant updatedAt) {

  public static TaskResponse from(Task t) {
    return new TaskResponse(
        t.getId(),
        t.getSubjectId(),
        t.getTitle(),
        t.getDescription(),
        t.getType(),
        t.getPriority(),
        t.getStatus(),
        t.getDueAt(),
        t.isDueAllDay(),
        t.getCompletedAt(),
        t.getCreatedAt(),
        t.getUpdatedAt());
  }
}
