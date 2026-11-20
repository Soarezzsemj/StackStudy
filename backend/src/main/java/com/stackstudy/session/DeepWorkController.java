package com.stackstudy.session;

import com.stackstudy.session.dto.SessionRequests;
import com.stackstudy.session.dto.SessionResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deepwork")
@Tag(name = "Deep Work stats")
public class DeepWorkController {

  private static final String TZ_DOC = "IANA time zone used to group days, e.g. America/Sao_Paulo";

  private final DeepWorkStatsService stats;
  private final DeepWorkSettingsService settings;

  public DeepWorkController(DeepWorkStatsService stats, DeepWorkSettingsService settings) {
    this.stats = stats;
    this.settings = settings;
  }

  @GetMapping("/heatmap")
  @Operation(summary = "Effective seconds per local day (default: last 12 months)")
  public SessionResponses.Heatmap heatmap(
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @Parameter(description = TZ_DOC) @RequestParam(defaultValue = "UTC") String tz) {
    return stats.heatmap(from, to, tz);
  }

  @GetMapping("/stats")
  @Operation(summary = "Today/week/month totals, by subject, streaks, daily average and goal")
  public SessionResponses.Stats stats(
      @Parameter(description = TZ_DOC) @RequestParam(defaultValue = "UTC") String tz) {
    return stats.stats(tz);
  }

  @GetMapping("/settings")
  @Operation(summary = "Pomodoro durations, weekly goal and alert preferences")
  public SessionResponses.Settings getSettings() {
    return settings.get();
  }

  @PutMapping("/settings")
  @Operation(summary = "Update pomodoro durations, weekly goal and alert preferences")
  public SessionResponses.Settings updateSettings(
      @Valid @RequestBody SessionRequests.Settings request) {
    return settings.update(request);
  }
}
