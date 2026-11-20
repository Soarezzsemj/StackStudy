package com.stackstudy.subject;

import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.security.CurrentUser;
import com.stackstudy.subject.dto.SubjectRequest;
import com.stackstudy.subject.dto.SubjectResponse;
import com.stackstudy.task.TaskRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubjectService {

  private final SubjectRepository subjects;
  private final SemesterService semesterService;
  private final TaskRepository tasks;
  private final CurrentUser currentUser;

  public SubjectService(
      SubjectRepository subjects,
      SemesterService semesterService,
      TaskRepository tasks,
      CurrentUser currentUser) {
    this.subjects = subjects;
    this.semesterService = semesterService;
    this.tasks = tasks;
    this.currentUser = currentUser;
  }

  /** Lista as matérias do usuário; com {@code semesterId}, só as daquele semestre. */
  @Transactional(readOnly = true)
  public List<SubjectResponse> list(UUID semesterId) {
    List<Subject> found;
    if (semesterId != null) {
      // Valida o dono do semestre (404 se for de outro usuário).
      semesterService.findOwned(semesterId);
      found = subjects.findAllBySemesterIdOrderByCreatedAtAscIdAsc(semesterId);
    } else {
      found = subjects.findAllOwned(currentUser.id());
    }
    Map<UUID, Long> pending = pendingCounts(found);
    return found.stream()
        .map(s -> SubjectResponse.from(s, pending.getOrDefault(s.getId(), 0L)))
        .toList();
  }

  @Transactional(readOnly = true)
  public SubjectResponse get(UUID id) {
    Subject subject = findOwned(id);
    return SubjectResponse.from(subject, pendingCounts(List.of(subject)).getOrDefault(id, 0L));
  }

  @Transactional
  public SubjectResponse create(SubjectRequest request) {
    semesterService.findOwned(request.semesterId());
    String color =
        request.color() != null
            ? SubjectColors.normalize(request.color())
            : SubjectColors.forIndex(subjects.countBySemesterId(request.semesterId()));
    Subject subject = new Subject(request.semesterId(), request.name().trim(), color);
    apply(subject, request, color);
    return SubjectResponse.from(subjects.save(subject), 0);
  }

  @Transactional
  public SubjectResponse update(UUID id, SubjectRequest request) {
    Subject subject = findOwned(id);
    if (!subject.getSemesterId().equals(request.semesterId())) {
      semesterService.findOwned(request.semesterId());
    }
    String color =
        request.color() != null ? SubjectColors.normalize(request.color()) : subject.getColor();
    apply(subject, request, color);
    return SubjectResponse.from(subject, pendingCounts(List.of(subject)).getOrDefault(id, 0L));
  }

  /** Apaga a matéria; as tarefas dela vão junto (ON DELETE CASCADE). */
  @Transactional
  public void delete(UUID id) {
    subjects.delete(findOwned(id));
  }

  /** Busca uma matéria do usuário logado. Se for de outro usuário, dá 404. */
  Subject findOwned(UUID id) {
    return subjects
        .findOwned(id, currentUser.id())
        .orElseThrow(() -> ApiException.notFound("Subject"));
  }

  private static void apply(Subject subject, SubjectRequest request, String color) {
    subject.update(
        request.semesterId(),
        request.name().trim(),
        blankToNull(request.code()),
        blankToNull(request.professor()),
        request.credits() == null ? 0 : request.credits(),
        request.workloadHours(),
        color,
        request.status() == null ? SubjectStatus.IN_PROGRESS : request.status());
  }

  private Map<UUID, Long> pendingCounts(List<Subject> list) {
    if (list.isEmpty()) {
      return Map.of();
    }
    List<UUID> ids = list.stream().map(Subject::getId).toList();
    return tasks.countPendingBySubject(currentUser.id(), ids).stream()
        .collect(
            Collectors.toMap(
                TaskRepository.SubjectTaskCount::getSubjectId,
                TaskRepository.SubjectTaskCount::getTotal));
  }

  private static String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
