package com.stackstudy.subject;

import com.stackstudy.subject.dto.SemesterRequest;
import com.stackstudy.subject.dto.SemesterResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/semesters")
@Tag(name = "Semesters")
public class SemesterController {

  private final SemesterService service;

  public SemesterController(SemesterService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a semester (the first one becomes current)")
  public SemesterResponse create(@Valid @RequestBody SemesterRequest request) {
    return service.create(request);
  }

  @GetMapping
  @Operation(summary = "List the user's semesters, newest first (archived included)")
  public List<SemesterResponse> list() {
    return service.list();
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get a semester")
  public SemesterResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Update name and period")
  public SemesterResponse update(
      @PathVariable UUID id, @Valid @RequestBody SemesterRequest request) {
    return service.update(id, request);
  }

  @PostMapping("/{id}/archive")
  @Operation(summary = "Archive (if it was current, the newest active semester becomes current)")
  public SemesterResponse archive(@PathVariable UUID id) {
    return service.archive(id);
  }

  @PostMapping("/{id}/unarchive")
  @Operation(summary = "Unarchive")
  public SemesterResponse unarchive(@PathVariable UUID id) {
    return service.unarchive(id);
  }

  @PostMapping("/{id}/current")
  @Operation(summary = "Make this the current semester")
  public SemesterResponse makeCurrent(@PathVariable UUID id) {
    return service.makeCurrent(id);
  }
}
