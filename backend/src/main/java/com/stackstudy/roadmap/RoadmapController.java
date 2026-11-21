package com.stackstudy.roadmap;

import com.stackstudy.common.error.RateLimitedException;
import com.stackstudy.common.security.RateLimitProperties;
import com.stackstudy.common.security.RateLimiter;
import com.stackstudy.roadmap.dto.RoadmapRequests;
import com.stackstudy.roadmap.dto.RoadmapResponses.RoadmapDetail;
import com.stackstudy.roadmap.dto.RoadmapResponses.RoadmapList;
import com.stackstudy.roadmap.dto.RoadmapResponses.SuggestionCreated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/roadmaps")
@Tag(name = "Roadmaps")
public class RoadmapController {

  private final RoadmapService roadmaps;
  private final RoadmapProperties properties;
  private final RateLimiter limiter;
  private final RateLimitProperties rateLimits;

  public RoadmapController(
      RoadmapService roadmaps,
      RoadmapProperties properties,
      RateLimiter limiter,
      RateLimitProperties rateLimits) {
    this.roadmaps = roadmaps;
    this.properties = properties;
    this.limiter = limiter;
    this.rateLimits = rateLimits;
  }

  @GetMapping
  @Operation(summary = "List active roadmaps (filter by area, search by text)")
  public RoadmapList list(
      @RequestParam(required = false) String area, @RequestParam(required = false) String q) {
    return roadmaps.list(area, q);
  }

  /**
   * Público (cadastro): roadmap mais compatível com a área e o curso, ou 204. Rate limit por IP.
   */
  @GetMapping("/suggest")
  @Operation(summary = "Suggest the best roadmap for an area and course (public, rate limited)")
  public ResponseEntity<RoadmapService.RoadmapSuggestion> suggest(
      @RequestParam(required = false) @Size(max = 20) String area,
      @RequestParam(required = false) @Size(max = 120) String course,
      HttpServletRequest request) {
    if (rateLimits.enabled()) {
      RoadmapProperties.Limit rule = properties.suggest();
      RateLimiter.Decision d =
          limiter.acquire(
              "roadmap-suggest:" + request.getRemoteAddr(), rule.limit(), rule.window());
      if (!d.allowed()) {
        throw new RateLimitedException(d.retryAfterSeconds());
      }
    }
    return roadmaps
        .suggest(area, course)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @GetMapping("/{slug}")
  @Operation(summary = "Full roadmap with stages, nodes, resources, lock state and progress")
  public RoadmapDetail detail(@PathVariable String slug) {
    return roadmaps.detail(slug);
  }

  @PostMapping("/{slug}/follow")
  @Operation(summary = "Follow a roadmap (idempotent; primary=true makes it the main one)")
  public RoadmapDetail follow(
      @PathVariable String slug, @RequestBody(required = false) RoadmapRequests.Follow body) {
    return roadmaps.follow(slug, body);
  }

  @DeleteMapping("/{slug}/follow")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Stop following a roadmap (progress is kept)")
  public void unfollow(@PathVariable String slug) {
    roadmaps.unfollow(slug);
  }

  @PutMapping("/{slug}/nodes/{nodeId}/progress")
  @Operation(summary = "Set a node's status and manual hours (locked stages are rejected)")
  public RoadmapDetail progress(
      @PathVariable String slug,
      @PathVariable UUID nodeId,
      @Valid @RequestBody RoadmapRequests.Progress body) {
    return roadmaps.updateProgress(slug, nodeId, body);
  }

  @PutMapping("/{slug}/goal")
  @Operation(summary = "Set the weekly goal in hours (1-80)")
  public RoadmapDetail goal(
      @PathVariable String slug, @Valid @RequestBody RoadmapRequests.Goal body) {
    return roadmaps.updateGoal(slug, body);
  }

  @PostMapping("/suggestions")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Suggest a new roadmap (plain text, rate limited; stored for the admin)")
  public SuggestionCreated suggest(@Valid @RequestBody RoadmapRequests.Suggestion body) {
    return new SuggestionCreated(roadmaps.suggest(body));
  }
}
