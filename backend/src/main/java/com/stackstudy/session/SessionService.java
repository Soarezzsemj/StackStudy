package com.stackstudy.session;

import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.error.ErrorCode;
import com.stackstudy.common.security.CurrentUser;
import com.stackstudy.common.web.PageResponse;
import com.stackstudy.session.SessionEnums.Mode;
import com.stackstudy.session.dto.SessionRequests;
import com.stackstudy.session.dto.SessionResponses.Session;
import com.stackstudy.subject.SubjectRepository;
import com.stackstudy.task.TaskRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ciclo de vida das sessões: iniciar, pausar, retomar, concluir, cancelar, registro manual. */
@Service
public class SessionService {

  /** Tolerância para relógio do cliente um pouco adiantado no registro manual. */
  private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

  private final StudySessionRepository sessions;
  private final SubjectRepository subjects;
  private final TaskRepository tasks;
  private final DeepWorkSettingsService settings;
  private final CurrentUser currentUser;
  private final Clock clock;

  public SessionService(
      StudySessionRepository sessions,
      SubjectRepository subjects,
      TaskRepository tasks,
      DeepWorkSettingsService settings,
      CurrentUser currentUser,
      Clock clock) {
    this.sessions = sessions;
    this.subjects = subjects;
    this.tasks = tasks;
    this.settings = settings;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Optional<Session> active() {
    Instant now = clock.instant();
    return sessions.findByUserIdAndEndedAtIsNull(currentUser.id()).map(s -> Session.from(s, now));
  }

  @Transactional
  public Session start(SessionRequests.Start request) {
    UUID userId = currentUser.id();
    if (sessions.findByUserIdAndEndedAtIsNull(userId).isPresent()) {
      throw alreadyActive();
    }
    checkReferences(userId, request.subjectId(), request.taskId());
    Integer planned = null;
    if (request.mode() == Mode.POMODORO) {
      planned =
          request.plannedMinutes() != null
              ? request.plannedMinutes()
              : settings.forUser(userId).getFocusMinutes();
    }
    Instant now = clock.instant();
    StudySession session =
        StudySession.start(
            userId, request.subjectId(), request.taskId(), request.mode(), planned, now);
    try {
      // Corrida entre duas abas: o índice único parcial barra a segunda sessão ativa.
      return Session.from(sessions.saveAndFlush(session), now);
    } catch (DataIntegrityViolationException e) {
      throw alreadyActive();
    }
  }

  @Transactional
  public Session pause(UUID id) {
    StudySession session = findActive(id);
    Instant now = clock.instant();
    session.pause(now);
    return Session.from(session, now);
  }

  @Transactional
  public Session resume(UUID id) {
    StudySession session = findActive(id);
    Instant now = clock.instant();
    session.resume(now);
    return Session.from(session, now);
  }

  @Transactional
  public Session complete(UUID id, SessionRequests.Complete request) {
    StudySession session = findActive(id);
    Instant now = clock.instant();
    session.complete(
        now,
        request == null ? null : blankToNull(request.note()),
        request == null ? null : request.focusRating());
    return Session.from(session, now);
  }

  /** Descarta a sessão ativa (não conta no histórico). */
  @Transactional
  public void cancel(UUID id) {
    sessions.delete(findActive(id));
  }

  @Transactional
  public Session createManual(SessionRequests.Manual request) {
    UUID userId = currentUser.id();
    checkReferences(userId, request.subjectId(), request.taskId());
    checkNotInFuture(request);
    StudySession session =
        StudySession.manual(
            userId,
            request.subjectId(),
            request.taskId(),
            request.startedAt(),
            request.durationMinutes());
    session.describe(blankToNull(request.note()), request.focusRating());
    return Session.from(sessions.save(session), clock.instant());
  }

  /** Edita uma sessão encerrada (cronometrada ou manual). A ativa não pode ser editada. */
  @Transactional
  public Session update(UUID id, SessionRequests.Manual request) {
    UUID userId = currentUser.id();
    StudySession session = findOwned(id);
    if (session.isActive()) {
      throw new ApiException(ErrorCode.SESSION_STILL_ACTIVE, "Active session cannot be edited");
    }
    checkReferences(userId, request.subjectId(), request.taskId());
    checkNotInFuture(request);
    session.edit(
        request.subjectId(), request.taskId(), request.startedAt(), request.durationMinutes());
    session.describe(blankToNull(request.note()), request.focusRating());
    return Session.from(sessions.saveAndFlush(session), clock.instant());
  }

  @Transactional
  public void delete(UUID id) {
    sessions.delete(findOwned(id));
  }

  @Transactional(readOnly = true)
  public PageResponse<Session> history(Instant from, Instant to, int page, int size) {
    Instant now = clock.instant();
    return PageResponse.from(
        sessions.findFinished(
            currentUser.id(),
            from == null ? Instant.EPOCH : from,
            to == null ? now.plus(Duration.ofDays(1)) : to,
            PageRequest.of(
                page, size, Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id")))),
        s -> Session.from(s, now));
  }

  private void checkReferences(UUID userId, UUID subjectId, UUID taskId) {
    if (!subjects.existsOwned(subjectId, userId)) {
      throw ApiException.notFound("Subject");
    }
    if (taskId != null && tasks.findByIdAndUserId(taskId, userId).isEmpty()) {
      throw ApiException.notFound("Task");
    }
  }

  private void checkNotInFuture(SessionRequests.Manual request) {
    Instant end = request.startedAt().plusSeconds(request.durationMinutes() * 60L);
    if (end.isAfter(clock.instant().plus(CLOCK_SKEW))) {
      throw ApiException.invalidField("startedAt", "session cannot end in the future");
    }
  }

  private StudySession findOwned(UUID id) {
    return sessions
        .findByIdAndUserId(id, currentUser.id())
        .orElseThrow(() -> ApiException.notFound("Session"));
  }

  private StudySession findActive(UUID id) {
    StudySession session = findOwned(id);
    if (!session.isActive()) {
      throw new ApiException(ErrorCode.SESSION_NOT_ACTIVE, "Session is not active");
    }
    return session;
  }

  private static ApiException alreadyActive() {
    return new ApiException(ErrorCode.SESSION_ALREADY_ACTIVE, "There is already an active session");
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
