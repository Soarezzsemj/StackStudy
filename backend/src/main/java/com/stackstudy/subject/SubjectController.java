package com.stackstudy.subject;

import com.stackstudy.subject.dto.SubjectRequest;
import com.stackstudy.subject.dto.SubjectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/subjects")
@Tag(name = "Subjects")
public class SubjectController {

  private final SubjectService service;

  public SubjectController(SubjectService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "List subjects (optionally of one semester), with pending task counts")
  public List<SubjectResponse> list(@RequestParam(required = false) UUID semesterId) {
    return service.list(semesterId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a subject (color defaults to the next palette color)")
  public SubjectResponse create(@Valid @RequestBody SubjectRequest request) {
    return service.create(request);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get a subject")
  public SubjectResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Update a subject")
  public SubjectResponse update(@PathVariable UUID id, @Valid @RequestBody SubjectRequest request) {
    return service.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a subject and its tasks")
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
