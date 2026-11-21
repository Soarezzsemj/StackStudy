package com.stackstudy.room;

import com.stackstudy.common.error.ApiError;
import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.error.ErrorCode;
import com.stackstudy.common.error.RateLimitedException;
import com.stackstudy.common.security.CurrentUser;
import com.stackstudy.common.security.RateLimitProperties;
import com.stackstudy.common.security.RateLimiter;
import com.stackstudy.room.RoomDirectory.Room;
import com.stackstudy.room.RoomEnums.EndedBy;
import com.stackstudy.room.RoomEnums.PomodoroPhase;
import com.stackstudy.room.RoomEnums.PresenceState;
import com.stackstudy.room.RoomEnums.RoomMode;
import com.stackstudy.room.RoomRepository.ActiveRow;
import com.stackstudy.room.RoomRepository.MemberRow;
import com.stackstudy.room.RoomRepository.RankRow;
import com.stackstudy.room.dto.RoomRequests;
import com.stackstudy.room.dto.RoomResponses.MemberPresence;
import com.stackstudy.room.dto.RoomResponses.Phase;
import com.stackstudy.room.dto.RoomResponses.Presence;
import com.stackstudy.room.dto.RoomResponses.RankEntry;
import com.stackstudy.room.dto.RoomResponses.Ranking;
import com.stackstudy.room.dto.RoomResponses.ReactionView;
import com.stackstudy.room.dto.RoomResponses.SessionView;
import com.stackstudy.session.DeepWorkStatsService;
import com.stackstudy.session.RoomSessionBridge;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sessões das salas: o servidor conta o tempo (por {@link LiveSession}, em memória) e só grava no
 * banco ao começar, a cada 30 min, ao pausar/retomar/parar e ao encerrar por TIMEOUT. O heartbeat
 * não abre transação nem consulta o banco (cache de sala e memória da sessão), salvo no salvamento
 * periódico ou quando a memória precisa ser reconstruída depois de reiniciar.
 *
 * <p>Não há job em segundo plano: sessões paradas são encerradas quando alguém lê a presença da
 * sala ou quando o dono volta (Deep Work ou sala), sempre a partir do último heartbeat válido.
 */
@Service
public class RoomSessionService implements RoomSessionBridge {

  private static final Logger log = LoggerFactory.getLogger(RoomSessionService.class);

  private final RoomDirectory directory;
  private final RoomPresence presence;
  private final RoomRepository repository;
  private final RoomProperties properties;
  private final RateLimiter limiter;
  private final RateLimitProperties rateLimits;
  private final CurrentUser currentUser;
  private final Clock clock;

  public RoomSessionService(
      RoomDirectory directory,
      RoomPresence presence,
      RoomRepository repository,
      RoomProperties properties,
      RateLimiter limiter,
      RateLimitProperties rateLimits,
      CurrentUser currentUser,
      Clock clock) {
    this.directory = directory;
    this.presence = presence;
    this.repository = repository;
    this.properties = properties;
    this.limiter = limiter;
    this.rateLimits = rateLimits;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  // começar / parar ------------------------------------------------------------------

  @Transactional
  public SessionView start(UUID roomId, RoomRequests.Start request) {
    UUID userId = currentUser.id();
    Room room = requireMember(roomId, userId);
    RoomMode mode = request.mode() != null ? request.mode() : room.row().defaultMode();
    boolean takeover = Boolean.TRUE.equals(request.takeover());
    String subjectName = null;
    if (request.subjectId() != null) {
      subjectName = repository.subjectName(userId, request.subjectId());
      if (subjectName == null) {
        throw ApiException.notFound("Subject");
      }
    }
    Instant now = clock.instant();

    // Uma sessão ativa por pessoa: Deep Work recusa; sala (outra aba/aparelho) assume só se pedir.
    if (repository.hasActiveDeepWork(userId)) {
      throw new ApiException(
          ErrorCode.SESSION_ALREADY_ACTIVE, "A Deep Work session is active; finish it first");
    }
    Optional<ActiveRow> open = repository.activeOf(userId);
    if (open.isPresent() && !closeIfStale(userId, open.get().id())) {
      if (!takeover) {
        throw new ApiException(
            ErrorCode.ROOM_SESSION_ACTIVE,
            "A room session is already open (another tab or device)",
            List.of(new ApiError.FieldProblem("roomId", String.valueOf(open.get().roomId()))));
      }
      endSession(userId, open.get().id(), EndedBy.USER, now);
    }

    UUID id;
    try {
      id = repository.insertSession(userId, roomId, request.subjectId(), mode, now);
    } catch (DataIntegrityViolationException e) {
      // Corrida com outra aba ou com o Deep Work: o índice único parcial barra a segunda.
      throw new ApiException(ErrorCode.SESSION_ALREADY_ACTIVE, "Another session is active");
    }
    LiveSession live =
        new LiveSession(
            id,
            userId,
            roomId,
            mode,
            now,
            subjectName,
            properties.timing(),
            room.row().pomodoro(),
            room.row().cycleStartedAt(),
            now);
    presence.put(live);
    return view(live, room, now);
  }

  /** Sinal de vida (a cada 30 s). Sem transação e, no caminho normal, sem banco. */
  public SessionView heartbeat(UUID roomId) {
    UUID userId = currentUser.id();
    Instant now = clock.instant();
    LiveSession live = liveFor(userId, roomId, now);
    synchronized (live) {
      boolean wasPaused = live.paused();
      live.heartbeat(now);
      if (live.dueForSave(now) || live.paused() != wasPaused) {
        save(live, now);
      }
    }
    return view(live, room(roomId), now);
  }

  public SessionView pause(UUID roomId) {
    return change(roomId, LiveSession::pause);
  }

  public SessionView resume(UUID roomId) {
    return change(roomId, LiveSession::resume);
  }

  /** "Ainda estou estudando". */
  public SessionView confirmPresence(UUID roomId) {
    return change(roomId, LiveSession::confirm);
  }

  /** Parar e salvar: encerra com o tempo contado pelo servidor (o cliente não manda duração). */
  public SessionView stop(UUID roomId) {
    UUID userId = currentUser.id();
    Instant now = clock.instant();
    LiveSession live = liveFor(userId, roomId, now);
    synchronized (live) {
      live.stop(now);
      repository.close(live.sessionId(), live.countedSeconds(), live.cycles(), now, EndedBy.USER);
      presence.remove(userId, live.sessionId());
    }
    return new SessionView(
        live.sessionId(),
        roomId,
        live.mode(),
        PresenceState.OFFLINE,
        live.startedAt(),
        live.countedSeconds(),
        false,
        live.cycles(),
        null,
        null,
        now);
  }

  private interface Change {
    void apply(LiveSession live, Instant now);
  }

  private SessionView change(UUID roomId, Change change) {
    UUID userId = currentUser.id();
    Instant now = clock.instant();
    LiveSession live = liveFor(userId, roomId, now);
    synchronized (live) {
      change.apply(live, now);
      save(live, now);
    }
    return view(live, room(roomId), now);
  }

  // presença ---------------------------------------------------------------------------

  /** Quem está na sala agora. Só membros; bloqueios escondem as duas partes. */
  public Presence presence(UUID roomId) {
    UUID me = currentUser.id();
    Room room = requireMember(roomId, me);
    Instant now = clock.instant();
    reapRoom(roomId, now);
    Set<UUID> blocked = directory.blockRelations(me);
    Map<UUID, LiveSession> live = new HashMap<>();
    for (LiveSession s : presence.inRoom(roomId)) {
      live.put(s.userId(), s);
    }
    List<MemberPresence> members = new ArrayList<>();
    int studying = 0;
    for (MemberRow m : room.activeMembers()) {
      if (blocked.contains(m.userId())) {
        continue;
      }
      LiveSession s = live.get(m.userId());
      PresenceState state = s == null ? PresenceState.OFFLINE : s.state(now);
      if (state == PresenceState.STUDYING) {
        studying++;
      }
      members.add(
          new MemberPresence(
              m.userId(),
              m.nickname(),
              m.role(),
              m.userId().equals(me),
              m.muted(),
              state,
              s == null ? null : s.mode(),
              s == null ? null : s.startedAt(),
              s == null ? 0 : s.secondsAt(now),
              s == null ? 0 : s.cycles(),
              s != null && m.showSubject() ? s.subjectName() : null));
    }
    members.sort(
        Comparator.comparingInt((MemberPresence p) -> order(p.state()))
            .thenComparing(MemberPresence::nickname, String.CASE_INSENSITIVE_ORDER));
    List<ReactionView> reactions =
        presence.drain(roomId, me, now).stream()
            .filter(e -> !blocked.contains(e.fromUserId()))
            .map(e -> new ReactionView(e.fromNickname(), e.kind(), e.at()))
            .toList();
    return new Presence(studying, phase(room, now), members, reactions, now);
  }

  /** Quantos estão estudando agora numa sala (para listas; sem banco). */
  public int studyingNow(UUID roomId) {
    Instant now = clock.instant();
    return (int)
        presence.inRoom(roomId).stream()
            .filter(s -> s.state(now) == PresenceState.STUDYING)
            .count();
  }

  // ranking ----------------------------------------------------------------------------

  public Ranking ranking(UUID roomId, String period, RoomMode mode, String tz) {
    UUID me = currentUser.id();
    Room room = requireMember(roomId, me);
    ZoneId zone = DeepWorkStatsService.parseZone(tz);
    Instant now = clock.instant();
    Instant from =
        switch (period == null ? "week" : period) {
          case "today" -> dayStart(zone, now);
          case "week" -> weekStart(zone, now);
          default -> throw ApiException.invalidField("period", "must be today or week");
        };
    RoomMode m = mode == null ? room.row().defaultMode() : mode;
    Map<UUID, long[]> totals = new HashMap<>();
    for (RankRow r : repository.ranking(roomId, m, from)) {
      totals.put(r.userId(), new long[] {r.seconds(), r.cycles()});
    }
    // Sessões em andamento entram com o valor da memória.
    for (LiveSession s : presence.inRoom(roomId)) {
      if (s.mode() == m && !s.startedAt().isBefore(from)) {
        long[] t = totals.computeIfAbsent(s.userId(), k -> new long[2]);
        t[0] += s.secondsAt(now);
        t[1] += s.cycles();
      }
    }
    Set<UUID> blocked = directory.blockRelations(me);
    record Row(MemberRow member, long seconds, long cycles) {}
    List<Row> rows = new ArrayList<>();
    for (MemberRow member : room.activeMembers()) {
      long[] t = totals.get(member.userId());
      if (t == null || member.hideFromRanking() || blocked.contains(member.userId())) {
        continue;
      }
      if ((m == RoomMode.FREE && t[0] > 0) || (m == RoomMode.POMODORO && (t[1] > 0 || t[0] > 0))) {
        rows.add(new Row(member, t[0], t[1]));
      }
    }
    Comparator<Row> order =
        m == RoomMode.POMODORO
            ? Comparator.comparingLong(Row::cycles)
                .reversed()
                .thenComparing(Comparator.comparingLong(Row::seconds).reversed())
            : Comparator.comparingLong(Row::seconds).reversed();
    rows.sort(order.thenComparing(r -> r.member().nickname(), String.CASE_INSENSITIVE_ORDER));
    List<RankEntry> entries = new ArrayList<>();
    for (int i = 0; i < rows.size() && i < 50; i++) {
      Row r = rows.get(i);
      entries.add(
          new RankEntry(
              i + 1,
              r.member().nickname(),
              r.member().userId().equals(me),
              r.seconds(),
              r.cycles()));
    }
    return new Ranking(period == null ? "week" : period, m, entries);
  }

  // reações ----------------------------------------------------------------------------

  /** Reação pré-definida a um membro (sem texto livre), com limite de frequência. */
  public void react(UUID roomId, RoomRequests.SendReaction request) {
    UUID me = currentUser.id();
    Room room = requireMember(roomId, me);
    MemberRow sender = room.member(me).orElseThrow();
    if (sender.muted()) {
      throw new ApiException(ErrorCode.FORBIDDEN, "Muted by the room owner");
    }
    if (request.toUserId().equals(me)) {
      throw ApiException.invalidField("toUserId", "cannot react to yourself");
    }
    // Fora da sala ou com bloqueio entre as partes: 404 (não revela o bloqueio).
    if (room.member(request.toUserId()).isEmpty()
        || directory.blockedBetween(me, request.toUserId())) {
      throw ApiException.notFound("Member");
    }
    if (rateLimits.enabled()) {
      RoomProperties.Limit pair = properties.limits().reactionPair();
      RoomProperties.Limit all = properties.limits().reaction();
      check(
          limiter.acquire(
              "room-reaction:" + me + ":" + request.toUserId(), pair.limit(), pair.window()));
      check(limiter.acquire("room-reaction:" + me, all.limit(), all.window()));
    }
    presence.react(
        roomId,
        request.toUserId(),
        new RoomPresence.ReactionEvent(me, sender.nickname(), request.kind(), clock.instant()));
  }

  // usado pelo RoomService --------------------------------------------------------------

  /** Minha sessão nesta sala (reconstrói a memória se o servidor reiniciou). */
  public Optional<SessionView> mine(UUID userId, Room room) {
    Instant now = clock.instant();
    Optional<LiveSession> live =
        presence.of(userId).filter(s -> s.roomId().equals(room.row().id()));
    if (live.isEmpty()) {
      Optional<ActiveRow> row =
          repository.activeOf(userId).filter(r -> room.row().id().equals(r.roomId()));
      if (row.isEmpty() || closeIfStale(userId, row.get().id())) {
        return Optional.empty();
      }
      live = Optional.of(restore(row.get(), room, now));
    }
    return Optional.of(view(live.get(), room, now));
  }

  /** Sessão em andamento (memória) do usuário nesta sala, para somar nos totais. */
  public Optional<LiveSession> live(UUID userId, UUID roomId) {
    return presence.of(userId).filter(s -> s.roomId().equals(roomId));
  }

  public List<LiveSession> liveInRoom(UUID roomId) {
    return presence.inRoom(roomId);
  }

  /** Encerra a sessão do usuário nesta sala (sair, ser removido, sala apagada). */
  public void endFor(UUID userId, UUID roomId) {
    repository
        .activeOf(userId)
        .filter(r -> roomId.equals(r.roomId()))
        .ifPresent(r -> endSession(userId, r.id(), EndedBy.USER, clock.instant()));
  }

  /** Encerra todas as sessões da sala (apagar a sala). */
  public void endAll(UUID roomId) {
    for (ActiveRow r : repository.activeInRoom(roomId)) {
      endSession(r.userId(), r.id(), EndedBy.USER, clock.instant());
    }
  }

  /** O dono mudou o ciclo: as sessões em andamento passam a usar o novo. */
  public void updateCycle(UUID roomId, RoomPomodoro pomodoro, Instant anchor) {
    for (LiveSession s : presence.inRoom(roomId)) {
      synchronized (s) {
        s.updatePomodoro(pomodoro, anchor);
      }
    }
  }

  // ponte com o Deep Work ------------------------------------------------------------------

  @Override
  public boolean closeIfStale(UUID userId, UUID sessionId) {
    Instant now = clock.instant();
    Optional<LiveSession> live = presence.of(userId).filter(s -> s.sessionId().equals(sessionId));
    if (live.isPresent()) {
      synchronized (live.get()) {
        if (!live.get().timedOut(now)) {
          return false;
        }
        closeTimedOut(live.get());
        return true;
      }
    }
    // Sem memória (servidor reiniciou): só encerra se nem o salvamento periódico é recente.
    Optional<ActiveRow> row = repository.activeOf(userId).filter(r -> r.id().equals(sessionId));
    if (row.isEmpty()) {
      return false;
    }
    Instant limit =
        row.get()
            .lastHeartbeatAt()
            .plus(properties.timing().saveEvery())
            .plus(properties.timing().timeoutAfter());
    if (now.isBefore(limit)) {
      return false;
    }
    repository.close(
        sessionId,
        row.get().countedSeconds(),
        row.get().cycles(),
        row.get().lastHeartbeatAt(),
        EndedBy.TIMEOUT);
    return true;
  }

  @Override
  public long liveSeconds(UUID userId, UUID sessionId, long saved) {
    return presence
        .of(userId)
        .filter(s -> s.sessionId().equals(sessionId))
        .map(s -> s.secondsAt(clock.instant()))
        .orElse(saved);
  }

  /** Desligamento (deploy/reinício): grava o que estava na memória. */
  @PreDestroy
  public void flushAll() {
    Instant now = clock.instant();
    for (LiveSession s : presence.all()) {
      try {
        synchronized (s) {
          save(s, now);
        }
      } catch (RuntimeException e) {
        log.warn("Could not save a room session on shutdown: {}", e.getMessage());
      }
    }
  }

  // internos --------------------------------------------------------------------------------

  private LiveSession liveFor(UUID userId, UUID roomId, Instant now) {
    // Pertencimento primeiro (cache em memória): fora da sala = 404, como em todo endpoint.
    requireMember(roomId, userId);
    Optional<LiveSession> live = presence.of(userId);
    if (live.isPresent()) {
      if (!live.get().roomId().equals(roomId)) {
        throw notActive();
      }
      return live.get();
    }
    // Memória vazia (servidor reiniciou): reconstrói do último salvamento.
    ActiveRow row =
        repository
            .activeOf(userId)
            .filter(r -> roomId.equals(r.roomId()))
            .orElseThrow(this::notActive);
    Optional<Room> room = directory.room(roomId);
    if (room.isEmpty() || room.get().member(userId).isEmpty()) {
      repository.close(
          row.id(), row.countedSeconds(), row.cycles(), row.lastHeartbeatAt(), EndedBy.USER);
      throw notActive();
    }
    return restore(row, room.get(), now);
  }

  private LiveSession restore(ActiveRow row, Room room, Instant now) {
    LiveSession live =
        LiveSession.restore(
            row.id(),
            row.userId(),
            row.roomId(),
            row.mode(),
            row.startedAt(),
            row.subjectName(),
            properties.timing(),
            room.row().pomodoro(),
            room.row().cycleStartedAt(),
            row.countedSeconds(),
            row.cycles(),
            row.paused(),
            now);
    presence.put(live);
    return live;
  }

  private void endSession(UUID userId, UUID sessionId, EndedBy by, Instant now) {
    Optional<LiveSession> live = presence.of(userId).filter(s -> s.sessionId().equals(sessionId));
    if (live.isPresent()) {
      synchronized (live.get()) {
        live.get().stop(now);
        repository.close(sessionId, live.get().countedSeconds(), live.get().cycles(), now, by);
      }
      presence.remove(userId, sessionId);
      return;
    }
    repository
        .activeOf(userId)
        .filter(r -> r.id().equals(sessionId))
        .ifPresent(
            r -> repository.close(r.id(), r.countedSeconds(), r.cycles(), r.lastHeartbeatAt(), by));
  }

  /** TIMEOUT: fica o que foi contado até o último heartbeat válido. */
  private void closeTimedOut(LiveSession live) {
    EndedBy by = live.autoPaused() ? EndedBy.AUTO_PAUSE : EndedBy.TIMEOUT;
    repository.close(
        live.sessionId(), live.countedSeconds(), live.cycles(), live.lastHeartbeat(), by);
    presence.remove(live.userId(), live.sessionId());
  }

  private void reapRoom(UUID roomId, Instant now) {
    for (LiveSession s : presence.inRoom(roomId)) {
      synchronized (s) {
        if (s.timedOut(now)) {
          closeTimedOut(s);
        }
      }
    }
  }

  private void save(LiveSession live, Instant now) {
    repository.saveProgress(
        live.sessionId(),
        live.countedSeconds(),
        live.cycles(),
        live.lastHeartbeat(),
        live.paused() ? now : null);
    live.markSaved(now);
  }

  private SessionView view(LiveSession live, Room room, Instant now) {
    Phase phase = live.mode() == RoomMode.POMODORO ? phase(room, now) : null;
    PresenceState state = live.state(now);
    boolean running =
        state == PresenceState.STUDYING
            && (live.mode() == RoomMode.FREE
                || (phase != null && phase.phase() == PomodoroPhase.FOCUS));
    return new SessionView(
        live.sessionId(),
        live.roomId(),
        live.mode(),
        state,
        live.startedAt(),
        live.secondsAt(now),
        running,
        live.cycles(),
        live.pendingConfirmDeadline(now),
        phase,
        now);
  }

  static Phase phase(Room room, Instant now) {
    RoomPomodoro.Phase p = room.row().pomodoro().phaseAt(room.row().cycleStartedAt(), now);
    return new Phase(p.phase(), p.cycle(), p.of(), p.startedAt(), p.endsAt());
  }

  private Room room(UUID roomId) {
    return directory.room(roomId).orElseThrow(this::notActive);
  }

  /** Membro (não removido) de sala ativa; senão 404, para não revelar a sala. */
  Room requireMember(UUID roomId, UUID userId) {
    Room room = directory.room(roomId).orElseThrow(() -> ApiException.notFound("Room"));
    if (room.member(userId).isEmpty()) {
      throw ApiException.notFound("Room");
    }
    return room;
  }

  static Instant dayStart(ZoneId zone, Instant now) {
    return LocalDate.ofInstant(now, zone).atStartOfDay(zone).toInstant();
  }

  static Instant weekStart(ZoneId zone, Instant now) {
    return LocalDate.ofInstant(now, zone)
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .atStartOfDay(zone)
        .toInstant();
  }

  private static void check(RateLimiter.Decision d) {
    if (!d.allowed()) {
      throw new RateLimitedException(d.retryAfterSeconds());
    }
  }

  private ApiException notActive() {
    return new ApiException(ErrorCode.SESSION_NOT_ACTIVE, "No active session in this room");
  }

  private static int order(PresenceState s) {
    return switch (s) {
      case STUDYING -> 0;
      case PAUSED, AUTO_PAUSED -> 1;
      case AWAY -> 2;
      case OFFLINE -> 3;
    };
  }
}
