package com.stackstudy.grade;

import com.stackstudy.common.error.ApiError;
import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.error.ErrorCode;
import com.stackstudy.common.security.CurrentUser;
import com.stackstudy.grade.dto.GradeRequests;
import com.stackstudy.grade.dto.GradeResponses;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GradeSettingsService {

  private final GradeSettingsRepository settings;
  private final CurrentUser currentUser;

  public GradeSettingsService(GradeSettingsRepository settings, CurrentUser currentUser) {
    this.settings = settings;
    this.currentUser = currentUser;
  }

  @Transactional(readOnly = true)
  public GradeResponses.Settings get() {
    return GradeResponses.Settings.from(forUser(currentUser.id()));
  }

  @Transactional
  public GradeResponses.Settings update(GradeRequests.Settings r) {
    validate(r);
    UUID userId = currentUser.id();
    GradeSettings entity = settings.findByUserId(userId).orElseGet(() -> new GradeSettings(userId));
    entity.update(
        r.method(),
        r.scaleMax(),
        r.passingGrade(),
        r.maxAbsencePct(),
        r.finalExamEnabled(),
        r.finalExamMinAverage(),
        r.finalExamAverageWeight(),
        r.finalExamPassingGrade(),
        r.gpaScale(),
        r.gpaWeight(),
        Boolean.TRUE.equals(r.roundBeforeCompare()),
        r.roundingScale() == null ? 1 : r.roundingScale());
    return GradeResponses.Settings.from(settings.save(entity));
  }

  /** Preferências gravadas ou, se ainda não houver, os padrões (sem gravar nada). */
  GradeSettings forUser(UUID userId) {
    return settings.findByUserId(userId).orElseGet(() -> new GradeSettings(userId));
  }

  /** Regras entre campos (as mesmas dos CHECK da tabela, com mensagem por campo). */
  private static void validate(GradeRequests.Settings r) {
    List<ApiError.FieldProblem> problems = new ArrayList<>();
    if (r.passingGrade().compareTo(r.scaleMax()) > 0) {
      problems.add(new ApiError.FieldProblem("passingGrade", "must not exceed scaleMax"));
    }
    if (r.finalExamPassingGrade().compareTo(r.scaleMax()) > 0) {
      problems.add(new ApiError.FieldProblem("finalExamPassingGrade", "must not exceed scaleMax"));
    }
    if (r.finalExamMinAverage().compareTo(r.passingGrade()) > 0) {
      problems.add(
          new ApiError.FieldProblem("finalExamMinAverage", "must not exceed passingGrade"));
    }
    if (!problems.isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR, "Request validation failed", problems);
    }
  }
}
