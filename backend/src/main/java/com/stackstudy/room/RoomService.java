package com.stackstudy.room;

import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.error.ErrorCode;
import com.stackstudy.common.error.RateLimitedException;
import com.stackstudy.common.security.CurrentUser;
import com.stackstudy.common.security.RateLimitProperties;
import com.stackstudy.common.security.RateLimiter;
import com.stackstudy.room.RoomDirectory.Room;
import com.stackstudy.room.RoomEnums.MemberRole;
import com.stackstudy.room.RoomEnums.RoomMode;
import com.stackstudy.room.RoomEnums.Visibility;
import com.stackstudy.room.RoomRepository.MemberRow;
import com.stackstudy.room.RoomRepository.RoomListRow;
import com.stackstudy.room.RoomRepository.RoomRow;
import com.stackstudy.room.RoomRepository.UserTotals;
import com.stackstudy.room.dto.RoomRequests;
import com.stackstudy.room.dto.RoomResponses.BlockedUser;
import com.stackstudy.room.dto.RoomResponses.Me;
import com.stackstudy.room.dto.RoomResponses.MyStats;
import com.stackstudy.room.dto.RoomResponses.PomodoroConfig;
import com.stackstudy.room.dto.RoomResponses.PublicRoom;
import com.stackstudy.room.dto.RoomResponses.ReportView;
import com.stackstudy.room.dto.RoomResponses.RoomDetail;
import com.stackstudy.room.dto.RoomResponses.RoomItem;
import com.stackstudy.room.dto.RoomResponses.SessionView;
import com.stackstudy.room.dto.RoomResponses.Timing;
import com.stackstudy.session.DeepWorkStatsService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Salas: criar, entrar (código ou pública), sair, editar, moderação e bloqueios. Todo endpoint de
 * sala confere o pertencimento ({@link RoomSessionService#requireMember}); sala de que a pessoa não
 * é membro responde 404 (não revela que existe). Só o dono edita, troca o convite e remove membros.
 */
@Service
public class RoomService {

  private static final int PUBLIC_LIMIT = 30;

  private final RoomRepository repository;
  private final RoomDirectory directory;
  private final RoomSessionService sessions;
  private final RoomProperties properties;
  private final RateLimiter limiter;
  private final RateLimitProperties rateLimits;
  private final CurrentUser currentUser;
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public RoomService(
      RoomRepository repository,
      RoomDirectory directory,
      RoomSessionService sessions,
      RoomProperties properties,
      RateLimiter limiter,
      RateLimitProperties rateLimits,
      CurrentUser currentUser,
      JdbcTemplate jdbc,
      Clock clock) {
    this.repository = repository;
    this.directory = directory;
    this.sessions = sessions;
    this.properties = properties;
    this.limiter = limiter;
    this.rateLimits = rateLimits;
    this.currentUser = currentUser;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  // salas ------------------------------------------------------------------------------

  @Transactional
  public RoomDetail create(RoomRequests.Create request, String tz) {
    UUID userId = currentUser.id();
    if (repository.countOwned(userId) >= properties.maxOwnedRooms()
        || repository.countMemberships(userId) >= properties.maxMemberships()) {
      throw new ApiException(ErrorCode.ROOM_LIMIT_REACHED, "Room limit reached");
    }
    limit("room-create:" + userId, properties.limits().create());
    String code = newCode();
    UUID id =
        repository.insertRoom(
            request.name().trim(),
            code,
            userId,
            request.visibility() == null ? Visibility.PRIVATE : request.visibility(),
            request.maxMembers() == null ? 20 : request.maxMembers(),
            request.weeklyGoalHours(),
            request.defaultMode() == null ? RoomMode.FREE : request.defaultMode(),
            clock.instant());
    repository.insertMember(id, userId, MemberRole.OWNER, request.nickname().trim());
    directory.evict(id);
    return detail(id, tz);
  }

  @Transactional(readOnly = true)
  public List<RoomItem> mine() {
    return repository.myRooms(currentUser.id()).stream()
        .map(
            r ->
                new RoomItem(
                    r.id(),
                    r.name(),
                    r.visibility(),
                    r.role(),
                    r.memberCount(),
                    r.maxMembers(),
                    sessions.studyingNow(r.id()),
                    r.defaultMode(),
                    r.weeklyGoalHours()))
        .toList();
  }

  /** Salas públicas: só nome, contagens e meta (os membros só aparecem para quem entra). */
  @Transactional(readOnly = true)
  public List<PublicRoom> publicRooms(String q) {
    Set<UUID> mine =
        repository.myRooms(currentUser.id()).stream()
            .map(RoomListRow::id)
            .collect(Collectors.toSet());
    return repository.publicRooms(q, PUBLIC_LIMIT).stream()
        .map(
            r ->
                new PublicRoom(
                    r.id(),
                    r.name(),
                    r.memberCount(),
                    r.maxMembers(),
                    sessions.studyingNow(r.id()),
                    r.defaultMode(),
                    r.weeklyGoalHours(),
                    mine.contains(r.id()),
                    r.memberCount() >= r.maxMembers()))
        .toList();
  }

  @Transactional
  public RoomDetail detail(UUID roomId, String tz) {
    UUID me = currentUser.id();
    Room room = sessions.requireMember(roomId, me);
    ZoneId zone = DeepWorkStatsService.parseZone(tz);
    Instant now = clock.instant();
    RoomRow r = room.row();
    MemberRow member = room.member(me).orElseThrow();

    Optional<SessionView> session = sessions.mine(me, room);
    String elsewhere = null;
    if (session.isEmpty()) {
      Optional<UUID> other =
          repository
              .activeOf(me)
              .map(RoomRepository.ActiveRow::roomId)
              .filter(id -> !roomId.equals(id));
      if (other.isPresent()) {
        elsewhere = other.get().toString();
      } else if (repository.hasActiveDeepWork(me)) {
        elsewhere = "DEEP_WORK";
      }
    }

    // Meus totais: encerradas (banco) + a sessão em andamento (memória).
    Instant dayStart = RoomSessionService.dayStart(zone, now);
    Instant weekStart = RoomSessionService.weekStart(zone, now);
    UserTotals t = repository.totals(me, roomId, dayStart, weekStart);
    long live = session.map(SessionView::countedSeconds).orElse(0L);
    Instant liveStart = session.map(SessionView::startedAt).orElse(now);
    MyStats stats =
        new MyStats(
            t.today() + (liveStart.isBefore(dayStart) ? 0 : live),
            t.week() + (liveStart.isBefore(weekStart) ? 0 : live),
            Math.max(t.longest(), live));
    long roomWeek = repository.roomSeconds(roomId, weekStart);
    for (LiveSession s : sessions.liveInRoom(roomId)) {
      if (!s.startedAt().isBefore(weekStart)) {
        roomWeek += s.secondsAt(now);
      }
    }
    LiveSession.Timing timing = properties.timing();
    return new RoomDetail(
        r.id(),
        r.name(),
        r.visibility(),
        member.role() == MemberRole.OWNER,
        member.role() == MemberRole.OWNER ? InviteCodes.format(r.inviteCode()) : null,
        room.activeMembers().size(),
        r.maxMembers(),
        r.weeklyGoalHours(),
        roomWeek,
        r.defaultMode(),
        new PomodoroConfig(
            r.pomodoro().focusMinutes(),
            r.pomodoro().shortBreakMinutes(),
            r.pomodoro().longBreakMinutes(),
            r.pomodoro().every(),
            r.cycleStartedAt()),
        RoomSessionService.phase(room, now),
        new Me(
            member.nickname(),
            member.role(),
            member.showSubject(),
            member.hideFromRanking(),
            member.muted()),
        stats,
        session.orElse(null),
        elsewhere,
        new Timing(
            30,
            (int) timing.absentAfter().toSeconds(),
            (int) timing.saveEvery().toMinutes(),
            (int) timing.confirmEvery().toMinutes(),
            (int) timing.confirmGrace().toMinutes()),
        now);
  }

  // entrar e sair ------------------------------------------------------------------------

  /** Entrar pelo código. Código errado, de sala apagada ou de quem foi removido: mesmo 404. */
  @Transactional
  public RoomDetail joinByCode(RoomRequests.JoinByCode request, String tz) {
    UUID userId = currentUser.id();
    // Conta toda tentativa (certa ou errada): adivinhar códigos fica inviável.
    limit("room-join:" + userId, properties.limits().join());
    String code = InviteCodes.normalize(request.code());
    UUID roomId = code == null ? null : repository.roomIdByCode(code).orElse(null);
    if (roomId == null) {
      throw invalidCode();
    }
    return join(roomId, userId, request.nickname(), tz);
  }

  /** Entrar numa sala PUBLIC pela lista. */
  @Transactional
  public RoomDetail joinPublic(UUID roomId, RoomRequests.JoinPublic request, String tz) {
    UUID userId = currentUser.id();
    limit("room-join:" + userId, properties.limits().join());
    Room room = directory.room(roomId).orElseThrow(() -> ApiException.notFound("Room"));
    if (room.row().visibility() != Visibility.PUBLIC) {
      throw ApiException.notFound("Room");
    }
    return join(roomId, userId, request.nickname(), tz);
  }

  private RoomDetail join(UUID roomId, UUID userId, String nickname, String tz) {
    Room room = directory.room(roomId).orElseThrow(this::invalidCode);
    MemberRow existing = room.members().get(userId);
    if (existing != null) {
      if (existing.banned()) {
        throw invalidCode();
      }
      return detail(roomId, tz);
    }
    if (room.activeMembers().size() >= room.row().maxMembers()) {
      throw new ApiException(ErrorCode.ROOM_FULL, "Room is full");
    }
    if (repository.countMemberships(userId) >= properties.maxMemberships()) {
      throw new ApiException(ErrorCode.ROOM_LIMIT_REACHED, "Room limit reached");
    }
    repository.insertMember(roomId, userId, MemberRole.MEMBER, nickname.trim());
    directory.evict(roomId);
    return detail(roomId, tz);
  }

  /** Sair (o dono não sai: apaga a sala). Pode apagar junto o meu histórico nesta sala (LGPD). */
  @Transactional
  public void leave(UUID roomId, RoomRequests.Leave request) {
    UUID userId = currentUser.id();
    Room room = sessions.requireMember(roomId, userId);
    if (room.row().ownerId().equals(userId)) {
      throw new ApiException(
          ErrorCode.ROOM_OWNER_CANNOT_LEAVE, "The owner deletes the room instead");
    }
    sessions.endFor(userId, roomId);
    repository.deleteMember(roomId, userId);
    if (request != null && Boolean.TRUE.equals(request.deleteHistory())) {
      repository.deleteHistory(userId, roomId);
    }
    directory.evict(roomId);
  }

  /** LGPD: apaga o meu histórico (sessões encerradas) nesta sala; as horas saem do Deep Work. */
  @Transactional
  public int deleteHistory(UUID roomId) {
    UUID userId = currentUser.id();
    sessions.requireMember(roomId, userId);
    return repository.deleteHistory(userId, roomId);
  }

  // dono ---------------------------------------------------------------------------------

  @Transactional
  public RoomDetail update(UUID roomId, RoomRequests.Update request, String tz) {
    Room room = requireOwner(roomId);
    RoomRow r = room.row();
    int maxMembers = request.maxMembers() == null ? r.maxMembers() : request.maxMembers();
    if (maxMembers < room.activeMembers().size()) {
      throw ApiException.invalidField("maxMembers", "must be at least the current member count");
    }
    Integer goal =
        request.weeklyGoalHours() == null
            ? r.weeklyGoalHours()
            : request.weeklyGoalHours() == 0 ? null : request.weeklyGoalHours();
    RoomPomodoro p = r.pomodoro();
    RoomPomodoro pomodoro =
        new RoomPomodoro(
            or(request.focusMinutes(), p.focusMinutes()),
            or(request.shortBreakMinutes(), p.shortBreakMinutes()),
            or(request.longBreakMinutes(), p.longBreakMinutes()),
            or(request.longBreakEvery(), p.every()));
    boolean cycleChanged = !pomodoro.equals(p) || Boolean.TRUE.equals(request.restartCycle());
    Instant anchor = cycleChanged ? clock.instant() : r.cycleStartedAt();
    RoomRow updated =
        new RoomRow(
            r.id(),
            request.name() == null ? r.name() : request.name().trim(),
            r.inviteCode(),
            r.ownerId(),
            request.visibility() == null ? r.visibility() : request.visibility(),
            maxMembers,
            goal,
            request.defaultMode() == null ? r.defaultMode() : request.defaultMode(),
            pomodoro,
            anchor,
            true);
    repository.updateRoom(updated);
    directory.evict(roomId);
    if (cycleChanged) {
      sessions.updateCycle(roomId, pomodoro, anchor);
    }
    return detail(roomId, tz);
  }

  /** Novo código: o antigo deixa de funcionar na hora. */
  @Transactional
  public String regenerateCode(UUID roomId) {
    requireOwner(roomId);
    String code = newCode();
    repository.setInviteCode(roomId, code);
    directory.evict(roomId);
    return InviteCodes.format(code);
  }

  @Transactional
  public void delete(UUID roomId) {
    requireOwner(roomId);
    sessions.endAll(roomId);
    repository.deleteRoom(roomId);
    directory.evict(roomId);
  }

  /** Remove o membro (não volta com o mesmo convite) e encerra a sessão dele na sala. */
  @Transactional
  public void removeMember(UUID roomId, UUID userId) {
    Room room = requireOwner(roomId);
    if (userId.equals(currentUser.id())) {
      throw ApiException.invalidField("userId", "the owner cannot remove themselves");
    }
    if (room.member(userId).isEmpty()) {
      throw ApiException.notFound("Member");
    }
    sessions.endFor(userId, roomId);
    repository.ban(roomId, userId);
    directory.evict(roomId);
  }

  /** Silenciar: o membro continua, mas não envia reações. */
  @Transactional
  public void setMuted(UUID roomId, UUID userId, boolean muted) {
    Room room = requireOwner(roomId);
    if (room.member(userId).isEmpty() || userId.equals(currentUser.id())) {
      throw ApiException.notFound("Member");
    }
    repository.setMuted(roomId, userId, muted);
    directory.evict(roomId);
  }

  // minhas preferências na sala --------------------------------------------------------

  @Transactional
  public RoomDetail updateMe(UUID roomId, RoomRequests.UpdateMe request, String tz) {
    UUID userId = currentUser.id();
    Room room = sessions.requireMember(roomId, userId);
    MemberRow m = room.member(userId).orElseThrow();
    repository.updateMe(
        roomId,
        userId,
        request.nickname() == null ? m.nickname() : request.nickname().trim(),
        request.showSubject() == null ? m.showSubject() : request.showSubject(),
        request.hideFromRanking() == null ? m.hideFromRanking() : request.hideFromRanking());
    directory.evict(roomId);
    return detail(roomId, tz);
  }

  // moderação ------------------------------------------------------------------------

  /** Denúncia da sala (ou de um membro dela). Quem não é membro só denuncia sala pública. */
  @Transactional
  public void report(UUID roomId, RoomRequests.Report request) {
    UUID me = currentUser.id();
    Room room = directory.room(roomId).orElseThrow(() -> ApiException.notFound("Room"));
    boolean member = room.member(me).isPresent();
    if (!member && room.row().visibility() != Visibility.PUBLIC) {
      throw ApiException.notFound("Room");
    }
    String detail = plainText("detail", request.detail());
    String nickname = null;
    if (request.userId() != null) {
      if (!member) {
        throw ApiException.notFound("Member");
      }
      MemberRow target =
          room.member(request.userId()).orElseThrow(() -> ApiException.notFound("Member"));
      nickname = target.nickname();
    }
    limit("room-report:" + me, properties.limits().report());
    repository.insertReport(
        me, roomId, request.userId(), room.row().name(), nickname, request.reason().name(), detail);
  }

  @Transactional
  public void block(UUID target) {
    UUID me = currentUser.id();
    if (target.equals(me)) {
      throw ApiException.invalidField("userId", "cannot block yourself");
    }
    if (!repository.userExists(target)) {
      throw ApiException.notFound("User");
    }
    repository.block(me, target);
    directory.evictBlocks(me, target);
  }

  @Transactional
  public void unblock(UUID target) {
    UUID me = currentUser.id();
    repository.unblock(me, target);
    directory.evictBlocks(me, target);
  }

  @Transactional(readOnly = true)
  public List<BlockedUser> blocks() {
    return repository.myBlocks(currentUser.id()).stream()
        .map(r -> new BlockedUser((UUID) r[0], (String) r[1]))
        .toList();
  }

  /** Admin (somente leitura): últimas denúncias, sem e-mail de ninguém. */
  @Transactional(readOnly = true)
  public List<ReportView> reportsForAdmin() {
    return jdbc.query(
        "SELECT id, created_at, reason, detail, room_name, room_id IS NULL AS deleted,"
            + " reported_nickname FROM room_reports ORDER BY created_at DESC LIMIT 50",
        (rs, i) ->
            new ReportView(
                rs.getObject("id", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                RoomEnums.ReportReason.valueOf(rs.getString("reason")),
                rs.getString("detail"),
                rs.getString("room_name"),
                rs.getBoolean("deleted"),
                rs.getString("reported_nickname")));
  }

  // internos ---------------------------------------------------------------------------

  private Room requireOwner(UUID roomId) {
    UUID me = currentUser.id();
    Room room = sessions.requireMember(roomId, me);
    if (!room.row().ownerId().equals(me)) {
      throw new ApiException(ErrorCode.FORBIDDEN, "Only the room owner can do this");
    }
    return room;
  }

  private String newCode() {
    for (int i = 0; i < 5; i++) {
      String code = InviteCodes.generate();
      if (Boolean.FALSE.equals(
          jdbc.queryForObject(
              "SELECT EXISTS (SELECT 1 FROM study_rooms WHERE invite_code = ?)",
              Boolean.class,
              code))) {
        return code;
      }
    }
    throw new ApiException(ErrorCode.CONFLICT, "Could not generate an invite code");
  }

  private void limit(String key, RoomProperties.Limit rule) {
    if (rateLimits.enabled()) {
      RateLimiter.Decision d = limiter.acquire(key, rule.limit(), rule.window());
      if (!d.allowed()) {
        throw new RateLimitedException(d.retryAfterSeconds());
      }
    }
  }

  private ApiException invalidCode() {
    return new ApiException(ErrorCode.INVALID_INVITE_CODE, "Invalid invite code");
  }

  private static int or(Integer value, int fallback) {
    return value == null ? fallback : value;
  }

  /** Texto puro (sem HTML nem controle); vazio = null. */
  static String plainText(String field, String value) {
    if (value == null) {
      return null;
    }
    if (value.indexOf('<') >= 0 || value.indexOf('>') >= 0) {
      throw ApiException.invalidField(field, "must be plain text (no HTML)");
    }
    String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\\n]]", "").trim();
    return cleaned.isEmpty() ? null : cleaned;
  }
}
