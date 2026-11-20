package com.stackstudy.session;

import com.stackstudy.common.web.PageResponse;
import com.stackstudy.session.dto.SessionRequests;
import com.stackstudy.session.dto.SessionResponses.Session;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
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
@RequestMapping("/api/deepwork/sessions")
@Tag(name = "Deep Work sessions")
public class SessionController {

  private final SessionService service;

  public SessionController(SessionService service) {
    this.service = service;
  }

  @GetMapping("/active")
  @Operation(summary = "The active session (204 if there is none)")
  public ResponseEntity<Session> active() {
    return service.active().map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Start a timed session (409 SESSION_ALREADY_ACTIVE if one is running)")
  public Session start(@Valid @RequestBody SessionRequests.Start request) {
    return service.start(request);
  }

  @PostMapping("/{id}/pause")
  @Operation(summary = "Pause the active session (idempotent)")
  public Session pause(@PathVariable UUID id) {
    return service.pause(id);
  }

  @PostMapping("/{id}/resume")
  @Operation(summary = "Resume the active session (idempotent)")
  public Session resume(@PathVariable UUID id) {
    return service.resume(id);
  }

  @PostMapping("/{id}/complete")
  @Operation(summary = "Complete the active session and store its effective time")
  public Session complete(
      @PathVariable UUID id, @Valid @RequestBody(required = false) SessionRequests.Complete body) {
    return service.complete(id, body);
  }

  @PostMapping("/{id}/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Discard the active session")
  public void cancel(@PathVariable UUID id) {
    service.cancel(id);
  }

  @PostMapping("/manual")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Log a past session (subject, start, duration)")
  public Session manual(@Valid @RequestBody SessionRequests.Manual request) {
    return service.createManual(request);
  }

  @GetMapping
  @Operation(summary = "Finished sessions, newest first")
  public PageResponse<Session> history(
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.history(from, to, page, size);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Edit a finished session")
  public Session update(@PathVariable UUID id, @Valid @RequestBody SessionRequests.Manual request) {
    return service.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a session")
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
