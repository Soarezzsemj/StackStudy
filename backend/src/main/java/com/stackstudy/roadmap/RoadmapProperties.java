package com.stackstudy.roadmap;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração dos roadmaps ({@code stackstudy.roadmaps.*}).
 *
 * @param premiumEnabled {@code ROADMAPS_PREMIUM_ENABLED}: false (padrão) libera tudo, inclusive o
 *     marcado como PREMIUM
 * @param suggestions sugestões de roadmap por usuário
 * @param suggest {@code GET /api/roadmaps/suggest} (público) por IP
 */
@Validated
@ConfigurationProperties(prefix = "stackstudy.roadmaps")
public record RoadmapProperties(
    boolean premiumEnabled, @Valid @NotNull Limit suggestions, @Valid @NotNull Limit suggest) {

  /** {@code limit} pedidos a cada {@code window}. */
  public record Limit(@Min(1) int limit, @NotNull Duration window) {}
}
