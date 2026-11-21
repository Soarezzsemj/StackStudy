package com.stackstudy.cockpit;

import com.stackstudy.cockpit.dto.CockpitResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cockpit")
@Tag(name = "Cockpit")
public class CockpitController {

  private final CockpitService cockpit;

  public CockpitController(CockpitService cockpit) {
    this.cockpit = cockpit;
  }

  @GetMapping
  @Operation(
      summary =
          "Home screen in one call: focus, streak, deadlines, flashcards due, subjects at risk,"
              + " next exam, mini heatmap and onboarding")
  public CockpitResponse get(@RequestParam(required = false) String tz) {
    return cockpit.get(tz);
  }
}
