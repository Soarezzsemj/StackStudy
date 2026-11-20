package com.stackstudy.subject;

import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.error.ErrorCode;
import com.stackstudy.common.security.CurrentUser;
import com.stackstudy.subject.dto.SemesterRequest;
import com.stackstudy.subject.dto.SemesterResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Semestres do usuário logado. Regras do "atual": o primeiro semestre criado vira atual; arquivar o
 * atual promove o mais recente não arquivado; semestre arquivado não pode ser o atual.
 */
@Service
public class SemesterService {

  private final SemesterRepository semesters;
  private final CurrentUser currentUser;

  public SemesterService(SemesterRepository semesters, CurrentUser currentUser) {
    this.semesters = semesters;
    this.currentUser = currentUser;
  }

  @Transactional
  public SemesterResponse create(SemesterRequest request) {
    UUID userId = currentUser.id();
    Semester semester =
        new Semester(userId, request.name().trim(), request.startDate(), request.endDate());
    if (semesters.findByUserIdAndCurrentTrue(userId).isEmpty()) {
      semester.setCurrent(true);
    }
    return SemesterResponse.from(semesters.save(semester));
  }

  @Transactional(readOnly = true)
  public List<SemesterResponse> list() {
    return semesters.findAllByUserIdOrderByStartDateDesc(currentUser.id()).stream()
        .map(SemesterResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public SemesterResponse get(UUID id) {
    return SemesterResponse.from(findOwned(id));
  }

  @Transactional
  public SemesterResponse update(UUID id, SemesterRequest request) {
    Semester semester = findOwned(id);
    semester.update(request.name().trim(), request.startDate(), request.endDate());
    return SemesterResponse.from(semester);
  }

  @Transactional
  public SemesterResponse archive(UUID id) {
    Semester semester = findOwned(id);
    if (semester.isArchived()) {
      return SemesterResponse.from(semester);
    }
    boolean wasCurrent = semester.isCurrent();
    semester.setArchived(true);
    semesters.saveAndFlush(semester);
    if (wasCurrent) {
      semesters
          .findFirstByUserIdAndArchivedFalseAndIdNotOrderByStartDateDesc(
              semester.getUserId(), semester.getId())
          .ifPresent(next -> next.setCurrent(true));
    }
    return SemesterResponse.from(semester);
  }

  @Transactional
  public SemesterResponse unarchive(UUID id) {
    Semester semester = findOwned(id);
    semester.setArchived(false);
    if (semesters.findByUserIdAndCurrentTrue(semester.getUserId()).isEmpty()) {
      semester.setCurrent(true);
    }
    return SemesterResponse.from(semester);
  }

  @Transactional
  public SemesterResponse makeCurrent(UUID id) {
    Semester semester = findOwned(id);
    if (semester.isArchived()) {
      throw new ApiException(ErrorCode.SEMESTER_ARCHIVED, "Archived semester cannot be current");
    }
    if (semester.isCurrent()) {
      return SemesterResponse.from(semester);
    }
    // Desmarca o atual e grava antes de marcar o novo, por causa do índice único parcial.
    semesters
        .findByUserIdAndCurrentTrue(semester.getUserId())
        .ifPresent(
            previous -> {
              previous.setCurrent(false);
              semesters.saveAndFlush(previous);
            });
    semester.setCurrent(true);
    return SemesterResponse.from(semester);
  }

  /** Busca um semestre do usuário logado. Se for de outro usuário, dá 404. */
  Semester findOwned(UUID id) {
    return semesters
        .findByIdAndUserId(id, currentUser.id())
        .orElseThrow(() -> ApiException.notFound("Semester"));
  }
}
