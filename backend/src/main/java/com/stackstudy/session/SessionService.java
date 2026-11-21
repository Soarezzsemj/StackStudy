package com.stackstudy.session;

import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.error.ErrorCode;
import com.stackstudy.common.security.CurrentUser;
import com.stackstudy.common.web.PageResponse;
import com.stackstudy.roadmap.RoadmapService;
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
  private final RoadmapService roadmaps;
  private final RoomSessionBridge rooms;
  private final CurrentUser currentUser;
  private final Clock clock;

  public SessionService(
      StudySessionRepository sessions,
      SubjectRepository subjects,
      TaskRepository tasks,
      DeepWorkSettingsService settings,
      RoadmapService roadmaps,
      RoomSessionBridge rooms,
      CurrentUser currentUser,
      Clock clock) {
    this.sessions = sessions;
    this.subjects = subjects;
    this.tasks = tasks;
    this.settings = settings;
    this.roadmaps = roadmaps;
    this.rooms = rooms;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  /**
   * Sessão ativa (do Deep Work ou de uma sala). Sessão de sala parada há tempo demais é encerrada
   * aqui (TIMEOUT), sem job em segundo plano.
   */
  @Transactional
  public Optional<Session> active() {
    Instant now = clock.instant();
    UUID userId = currentUser.id();
    return activeSession(userId)
        .map(
            s ->
                s.isRoomSession()
                    ? Session.from(
                        s, now, rooms.liveSeconds(userId, s.getId(), s.getCountedSeconds()))
                    : Session.from(s, now));
  }

  /** Ativa, encerrando antes a de sala que expirou. */
  private Optional<StudySession> activeSession(UUID userId) {
    Optional<StudySession> s = sessions.findByUserIdAndEndedAtIsNull(userId);
    if (s.isPresent() && s.get().isRoomSession() && rooms.closeIfStale(userId, s.get().getId())) {
      return Optional.empty();
    }
    return s;
  }

  @Transactional
  public Session start(SessionRequests.Start request) {
    UUID userId = currentUser.id();
    if (activeSession(userId).isPresent()) {
      throw alreadyActive();
    }
    checkReferences(userId, request.subjectId(), request.taskId());
    checkNode(userId, request.roadmapNodeId());
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
    session.linkRoadmapNode(request.roadmapNodeId());
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
    sessions.flush();
    roadmaps.refreshFocus(session.getUserId(), session.getRoadmapNodeId());
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
    checkNode(userId, request.roadmapNodeId());
    checkNotInFuture(request);
    StudySession session =
        StudySession.manual(
            userId,
            request.subjectId(),
            request.taskId(),
            request.startedAt(),
            request.durationMinutes());
    session.describe(blankToNull(request.note()), request.focusRating());
    session.linkRoadmapNode(request.roadmapNodeId());
    Session saved = Session.from(sessions.saveAndFlush(session), clock.instant());
    roadmaps.refreshFocus(userId, request.roadmapNodeId());
    return saved;
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
    checkNode(userId, request.roadmapNodeId());
    checkNotInFuture(request);
    UUID previousNode = session.getRoadmapNodeId();
    if (request.roadmapNodeId() != null) {
      // Sem o campo, a ligação continua (o diálogo de edição não conhece o tópico).
      session.linkRoadmapNode(request.roadmapNodeId());
    }
    session.edit(
        request.subjectId(), request.taskId(), request.startedAt(), request.durationMinutes());
    session.describe(blankToNull(request.note()), request.focusRating());
    Session saved = Session.from(sessions.saveAndFlush(session), clock.instant());
    roadmaps.refreshFocus(userId, previousNode, session.getRoadmapNodeId());
    return saved;
  }

  @Transactional
  public void delete(UUID id) {
    StudySession session = findOwned(id);
    if (session.isActive() && session.isRoomSession()) {
      throw new ApiException(ErrorCode.SESSION_IN_ROOM, "This session belongs to a study room");
    }
    UUID node = session.getRoadmapNodeId();
    sessions.delete(session);
    sessions.flush();
    roadmaps.refreshFocus(session.getUserId(), node);
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

  /** Tópico de roadmap ligado à sessão: precisa existir e estar aberto (regra do Premium). */
  private void checkNode(UUID userId, UUID nodeId) {
    if (nodeId != null) {
      roadmaps.requireFocusNode(userId, nodeId);
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
    if (session.isRoomSession()) {
      // O tempo da sala é do servidor da sala: pausar/parar só por lá.
      throw new ApiException(ErrorCode.SESSION_IN_ROOM, "This session belongs to a study room");
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
