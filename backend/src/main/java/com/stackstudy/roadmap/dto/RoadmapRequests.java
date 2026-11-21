package com.stackstudy.roadmap.dto;

import com.stackstudy.roadmap.RoadmapEnums.ProgressStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Corpos de requisição dos roadmaps. */
public final class RoadmapRequests {

  private RoadmapRequests() {}

  /**
   * @param primary true torna este o roadmap principal (o primeiro seguido já vira principal)
   */
  public record Follow(Boolean primary) {}

  /**
   * @param manualHours horas lançadas à mão (0–500, 2 casas); ausente = mantém. As do Deep Work
   *     somam à parte.
   */
  public record Progress(
      @NotNull ProgressStatus status,
      @DecimalMin("0") @DecimalMax("500") @Digits(integer = 3, fraction = 2)
          BigDecimal manualHours) {}

  public record Goal(@NotNull @Min(1) @Max(80) Integer weeklyGoalHours) {}

  /** Texto puro: {@code <} e {@code >} são recusados no service. */
  public record Suggestion(
      @NotBlank @Size(min = 3, max = 120) String title,
      @Size(max = 60) String area,
      @Size(max = 1000) String details) {}
}
