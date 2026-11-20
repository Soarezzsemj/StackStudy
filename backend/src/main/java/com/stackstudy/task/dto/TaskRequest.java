package com.stackstudy.task.dto;

import com.stackstudy.task.TaskEnums.Priority;
import com.stackstudy.task.TaskEnums.Status;
import com.stackstudy.task.TaskEnums.Type;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Criar ou editar tarefa (PUT troca tudo). Padrões: tipo OTHER, prioridade MEDIUM, status TODO.
 *
 * @param subjectId opcional; precisa ser uma matéria do usuário
 * @param dueAt prazo (ISO-8601 UTC); opcional
 * @param dueAllDay {@code true} quando o prazo é só a data, sem hora
 */
public record TaskRequest(
    @NotBlank @Size(max = 200) String title,
    @Size(max = 5000) String description,
    UUID subjectId,
    Type type,
    Priority priority,
    Status status,
    Instant dueAt,
    Boolean dueAllDay) {}
