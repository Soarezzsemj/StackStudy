package com.stackstudy.grade;

import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.error.ErrorCode;
import com.stackstudy.common.security.CurrentUser;
import com.stackstudy.grade.dto.GradeRequests;
import com.stackstudy.grade.dto.GradeResponses.AbsenceView;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registro de faltas por data. Uma linha por matéria e dia ({@code count} = aulas perdidas). */
@Service
public class AbsenceService {

  private final AbsenceRepository absences;
  private final GradeService gradeService;
  private final CurrentUser currentUser;

  public AbsenceService(
      AbsenceRepository absences, GradeService gradeService, CurrentUser currentUser) {
    this.absences = absences;
    this.gradeService = gradeService;
    this.currentUser = currentUser;
  }

  @Transactional(readOnly = true)
  public List<AbsenceView> list(UUID subjectId) {
    gradeService.findOwned(subjectId);
    return absences.findAllBySubjectIdOrderByDateDesc(subjectId).stream()
        .map(AbsenceView::from)
        .toList();
  }

  @Transactional
  public AbsenceView create(UUID subjectId, GradeRequests.AbsenceInput input) {
    gradeService.findOwned(subjectId);
    if (absences.existsBySubjectIdAndDate(subjectId, input.date())) {
      throw alreadyRegistered();
    }
    return AbsenceView.from(absences.save(new Absence(subjectId, input.date(), input.count())));
  }

  @Transactional
  public AbsenceView update(UUID id, GradeRequests.AbsenceInput input) {
    Absence absence = findOwned(id);
    if (absences.existsBySubjectIdAndDateAndIdNot(absence.getSubjectId(), input.date(), id)) {
      throw alreadyRegistered();
    }
    absence.update(input.date(), input.count());
    return AbsenceView.from(absence);
  }

  @Transactional
  public void delete(UUID id) {
    absences.delete(findOwned(id));
  }

  private Absence findOwned(UUID id) {
    return absences
        .findOwned(id, currentUser.id())
        .orElseThrow(() -> ApiException.notFound("Absence"));
  }

  private static ApiException alreadyRegistered() {
    return new ApiException(
        ErrorCode.ABSENCE_ALREADY_REGISTERED, "An absence is already registered on this date");
  }
}
