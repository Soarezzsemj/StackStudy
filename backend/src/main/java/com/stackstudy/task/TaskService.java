package com.stackstudy.task;

import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.security.CurrentUser;
import com.stackstudy.common.web.PageResponse;
import com.stackstudy.subject.SubjectRepository;
import com.stackstudy.task.TaskEnums.Priority;
import com.stackstudy.task.TaskEnums.Status;
import com.stackstudy.task.TaskEnums.Type;
import com.stackstudy.task.dto.TaskRequest;
import com.stackstudy.task.dto.TaskResponse;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

  /**
   * Prazo mais próximo primeiro; no Postgres, ASC já põe os nulos (sem prazo) no fim. Desempate por
   * criação e id para a paginação ser estável.
   */
  static final Sort ORDER =
      Sort.by(Sort.Order.asc("dueAt"), Sort.Order.asc("createdAt"), Sort.Order.asc("id"));

  private final TaskRepository tasks;
  private final SubjectRepository subjects;
  private final CurrentUser currentUser;
  private final Clock clock;

  public TaskService(
      TaskRepository tasks, SubjectRepository subjects, CurrentUser currentUser, Clock clock) {
    this.tasks = tasks;
    this.subjects = subjects;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public PageResponse<TaskResponse> list(TaskFilter filter, int page, int size) {
    return PageResponse.from(
        tasks.findAll(
            TaskSpecs.ownedBy(currentUser.id(), filter), PageRequest.of(page, size, ORDER)),
        TaskResponse::from);
  }

  @Transactional(readOnly = true)
  public TaskResponse get(UUID id) {
    return TaskResponse.from(findOwned(id));
  }

  @Transactional
  public TaskResponse create(TaskRequest request) {
    Task task = new Task(currentUser.id(), request.title().trim());
    apply(task, request);
    return TaskResponse.from(tasks.save(task));
  }

  @Transactional
  public TaskResponse update(UUID id, TaskRequest request) {
    Task task = findOwned(id);
    apply(task, request);
    // Flush para o updatedAt da resposta já sair atualizado.
    return TaskResponse.from(tasks.saveAndFlush(task));
  }

  @Transactional
  public TaskResponse changeStatus(UUID id, Status status) {
    Task task = findOwned(id);
    task.changeStatus(status, clock.instant());
    return TaskResponse.from(tasks.saveAndFlush(task));
  }

  @Transactional
  public void delete(UUID id) {
    tasks.delete(findOwned(id));
  }

  private void apply(Task task, TaskRequest request) {
    UUID subjectId = request.subjectId();
    if (subjectId != null && !subjects.existsOwned(subjectId, currentUser.id())) {
      // Matéria de outro usuário parece não existir.
      throw ApiException.notFound("Subject");
    }
    task.update(
        subjectId,
        request.title().trim(),
        blankToNull(request.description()),
        request.type() == null ? Type.OTHER : request.type(),
        request.priority() == null ? Priority.MEDIUM : request.priority(),
        request.dueAt(),
        Boolean.TRUE.equals(request.dueAllDay()));
    task.changeStatus(request.status() == null ? Status.TODO : request.status(), clock.instant());
  }

  private Task findOwned(UUID id) {
    return tasks
        .findByIdAndUserId(id, currentUser.id())
        .orElseThrow(() -> ApiException.notFound("Task"));
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
