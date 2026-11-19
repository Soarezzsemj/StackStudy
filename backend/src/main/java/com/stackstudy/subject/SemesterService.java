package com.stackstudy.subject;

import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.security.CurrentUser;
import com.stackstudy.subject.dto.CreateSemesterRequest;
import com.stackstudy.subject.dto.SemesterResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Na Fase 0 só tem criar, buscar e listar. A Fase 1 adiciona editar, arquivar e apagar. */
@Service
public class SemesterService {

  private final SemesterRepository semesters;
  private final CurrentUser currentUser;

  public SemesterService(SemesterRepository semesters, CurrentUser currentUser) {
    this.semesters = semesters;
    this.currentUser = currentUser;
  }

  @Transactional
  public SemesterResponse create(CreateSemesterRequest request) {
    Semester saved =
        semesters.save(
            new Semester(
                currentUser.id(), request.name().trim(), request.startDate(), request.endDate()));
    return SemesterResponse.from(saved);
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

  /** Busca um semestre do usuário logado. Se for de outro usuário, dá 404. */
  Semester findOwned(UUID id) {
    return semesters
        .findByIdAndUserId(id, currentUser.id())
        .orElseThrow(() -> ApiException.notFound("Semester"));
  }
}
