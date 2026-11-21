package com.stackstudy.roadmap;

import com.stackstudy.roadmap.dto.RoadmapRequests;
import com.stackstudy.roadmap.dto.RoadmapResponses.RoadmapDetail;
import com.stackstudy.roadmap.dto.RoadmapResponses.RoadmapList;
import com.stackstudy.roadmap.dto.RoadmapResponses.SuggestionCreated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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

  public RoadmapController(RoadmapService roadmaps) {
    this.roadmaps = roadmaps;
  }

  @GetMapping
  @Operation(summary = "List active roadmaps (filter by area, search by text)")
  public RoadmapList list(
      @RequestParam(required = false) String area, @RequestParam(required = false) String q) {
    return roadmaps.list(area, q);
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
