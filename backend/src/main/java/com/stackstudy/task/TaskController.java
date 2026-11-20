package com.stackstudy.task;

import com.stackstudy.common.web.PageResponse;
import com.stackstudy.task.TaskEnums.Status;
import com.stackstudy.task.TaskEnums.Type;
import com.stackstudy.task.dto.TaskRequest;
import com.stackstudy.task.dto.TaskResponse;
import com.stackstudy.task.dto.TaskStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks")
public class TaskController {

  static final int MAX_PAGE_SIZE = 200;

  private final TaskService service;

  public TaskController(TaskService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "List tasks with filters, ordered by due date (no due date last)")
  public PageResponse<TaskResponse> list(
      @RequestParam(required = false) UUID subjectId,
      @Parameter(description = "true: only tasks without a subject") @RequestParam(required = false)
          Boolean withoutSubject,
      @Parameter(description = "Repeat or comma-separate: type=EXAM&type=PROJECT")
          @RequestParam(required = false)
          List<Type> type,
      @Parameter(description = "Repeat or comma-separate: status=TODO,DOING")
          @RequestParam(required = false)
          List<Status> status,
      @Parameter(description = "Text in title or description")
          @RequestParam(required = false)
          @Size(max = 100)
          String q,
      @RequestParam(required = false) Instant dueFrom,
      @RequestParam(required = false) Instant dueTo,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
    return service.list(
        new TaskFilter(subjectId, withoutSubject, type, status, q, dueFrom, dueTo), page, size);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a task")
  public TaskResponse create(@Valid @RequestBody TaskRequest request) {
    return service.create(request);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get a task")
  public TaskResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Replace a task")
  public TaskResponse update(@PathVariable UUID id, @Valid @RequestBody TaskRequest request) {
    return service.update(id, request);
  }

  @PatchMapping("/{id}/status")
  @Operation(summary = "Change only the status (e.g. mark as done)")
  public TaskResponse changeStatus(
      @PathVariable UUID id, @Valid @RequestBody TaskStatusRequest request) {
    return service.changeStatus(id, request.status());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a task")
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
