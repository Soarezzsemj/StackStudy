package com.stackstudy.room;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuração das salas ({@code stackstudy.rooms.*}).
 *
 * @param maxOwnedRooms salas ativas que uma pessoa pode criar
 * @param maxMemberships salas em que uma pessoa pode estar ao mesmo tempo
 */
@Validated
@ConfigurationProperties(prefix = "stackstudy.rooms")
public record RoomProperties(
    @Min(1) int maxOwnedRooms,
    @Min(1) int maxMemberships,
    @NotNull LiveSession.Timing timing,
    @Valid @NotNull Limits limits) {

  public record Limit(@Min(1) int limit, @NotNull Duration window) {}

  public record Limits(
      @Valid @NotNull Limit create,
      @Valid @NotNull Limit join,
      @Valid @NotNull Limit report,
      @Valid @NotNull Limit reaction,
      @Valid @NotNull Limit reactionPair) {}
}
