package com.stackstudy.task.dto;

import com.stackstudy.task.TaskEnums.Status;
import jakarta.validation.constraints.NotNull;

/** Troca só o status (ex.: marcar como feita com um clique). */
public record TaskStatusRequest(@NotNull Status status) {}
