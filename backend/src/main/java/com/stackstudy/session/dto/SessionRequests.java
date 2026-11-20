package com.stackstudy.session.dto;

import com.stackstudy.session.SessionEnums.Mode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Corpos de requisição das sessões (records pequenos agrupados). */
public final class SessionRequests {

  private SessionRequests() {}

  /**
   * Inicia uma sessão cronometrada.
   *
   * @param plannedMinutes só no Pomodoro; sem valor, usa o foco das preferências
   */
  public record Start(
      @NotNull UUID subjectId,
      UUID taskId,
      @NotNull Mode mode,
      @Min(1) @Max(180) Integer plannedMinutes) {}

  /** Conclui a sessão ativa, com a "commit message" e a nota de foco opcionais. */
  public record Complete(@Size(max = 500) String note, @Min(1) @Max(5) Short focusRating) {}

  /** Registro manual de uma sessão passada, ou edição de uma sessão encerrada. */
  public record Manual(
      @NotNull UUID subjectId,
      UUID taskId,
      @NotNull Instant startedAt,
      @NotNull @Min(1) @Max(720) Integer durationMinutes,
      @Size(max = 500) String note,
      @Min(1) @Max(5) Short focusRating) {}

  /** Preferências do pomodoro e meta semanal. */
  public record Settings(
      @NotNull @Min(1) @Max(180) Integer focusMinutes,
      @NotNull @Min(1) @Max(60) Integer shortBreakMinutes,
      @NotNull @Min(1) @Max(120) Integer longBreakMinutes,
      @NotNull @Min(1) @Max(12) Integer longBreakEvery,
      @NotNull @Min(0) @Max(10080) Integer weeklyGoalMinutes,
      @NotNull Boolean soundEnabled,
      @NotNull Boolean notificationsEnabled) {}
}
