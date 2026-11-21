package com.stackstudy.room;

import com.stackstudy.room.RoomEnums.RoomMode;
import com.stackstudy.room.dto.RoomRequests;
import com.stackstudy.room.dto.RoomResponses.Presence;
import com.stackstudy.room.dto.RoomResponses.PublicRoom;
import com.stackstudy.room.dto.RoomResponses.Ranking;
import com.stackstudy.room.dto.RoomResponses.RoomDetail;
import com.stackstudy.room.dto.RoomResponses.RoomItem;
import com.stackstudy.room.dto.RoomResponses.SessionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
@Tag(name = "Study rooms")
public class RoomController {

  private static final String UTC = "UTC";

  private final RoomService rooms;
  private final RoomSessionService sessions;

  public RoomController(RoomService rooms, RoomSessionService sessions) {
    this.rooms = rooms;
    this.sessions = sessions;
  }

  // salas ----------------------------------------------------------------------------

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a room (private by default); the creator is the owner")
  public RoomDetail create(
      @Valid @RequestBody RoomRequests.Create body, @RequestParam(defaultValue = UTC) String tz) {
    return rooms.create(body, tz);
  }

  @GetMapping("/mine")
  @Operation(summary = "Rooms I am a member of")
  public List<RoomItem> mine() {
    return rooms.mine();
  }

  @GetMapping("/public")
  @Operation(summary = "Public rooms: name, counts and goal only (members are never listed)")
  public List<PublicRoom> publicRooms(@RequestParam(required = false) String q) {
    return rooms.publicRooms(q);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Room detail (members only): settings, my stats and my session")
  public RoomDetail detail(@PathVariable UUID id, @RequestParam(defaultValue = UTC) String tz) {
    return rooms.detail(id, tz);
  }

  @PostMapping("/join")
  @Operation(summary = "Join by invite code (rate limited)")
  public RoomDetail join(
      @Valid @RequestBody RoomRequests.JoinByCode body,
      @RequestParam(defaultValue = UTC) String tz) {
    return rooms.joinByCode(body, tz);
  }

  @PostMapping("/{id}/join")
  @Operation(summary = "Join a public room")
  public RoomDetail joinPublic(
      @PathVariable UUID id,
      @Valid @RequestBody RoomRequests.JoinPublic body,
      @RequestParam(defaultValue = UTC) String tz) {
    return rooms.joinPublic(id, body, tz);
  }

  @PostMapping("/{id}/leave")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Leave the room (optionally deleting my history in it)")
  public void leave(@PathVariable UUID id, @RequestBody(required = false) RoomRequests.Leave body) {
    rooms.leave(id, body);
  }

  @DeleteMapping("/{id}/history")
  @Operation(summary = "Delete my finished sessions in this room")
  public Map<String, Integer> deleteHistory(@PathVariable UUID id) {
    return Map.of("deleted", rooms.deleteHistory(id));
  }

  @PatchMapping("/{id}")
  @Operation(summary = "Update the room (owner)")
  public RoomDetail update(
      @PathVariable UUID id,
      @Valid @RequestBody RoomRequests.Update body,
      @RequestParam(defaultValue = UTC) String tz) {
    return rooms.update(id, body, tz);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete the room (owner); sessions stay in Deep Work history")
  public void delete(@PathVariable UUID id) {
    rooms.delete(id);
  }

  @PostMapping("/{id}/invite-code")
  @Operation(summary = "Generate a new invite code (owner); the old one stops working")
  public Map<String, String> regenerate(@PathVariable UUID id) {
    return Map.of("inviteCode", rooms.regenerateCode(id));
  }

  @DeleteMapping("/{id}/members/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Remove a member (owner); they cannot rejoin with the same code")
  public void removeMember(@PathVariable UUID id, @PathVariable UUID userId) {
    rooms.removeMember(id, userId);
  }

  @PatchMapping("/{id}/members/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Mute or unmute a member's reactions (owner)")
  public void muteMember(
      @PathVariable UUID id,
      @PathVariable UUID userId,
      @Valid @RequestBody RoomRequests.MemberUpdate body) {
    rooms.setMuted(id, userId, body.muted());
  }

  @PatchMapping("/{id}/me")
  @Operation(summary = "My nickname and privacy in this room")
  public RoomDetail updateMe(
      @PathVariable UUID id,
      @Valid @RequestBody RoomRequests.UpdateMe body,
      @RequestParam(defaultValue = UTC) String tz) {
    return rooms.updateMe(id, body, tz);
  }

  // sessão (o servidor conta o tempo; nenhuma duração vem do cliente) ------------------

  @PostMapping("/{id}/session/start")
  @Operation(summary = "Start my session in the room (one active session per user)")
  public SessionView start(
      @PathVariable UUID id, @RequestBody(required = false) RoomRequests.Start body) {
    return sessions.start(id, body == null ? new RoomRequests.Start(null, null, null) : body);
  }

  @PostMapping("/{id}/session/heartbeat")
  @Operation(summary = "Heartbeat (every 30 s); in-memory, no database access")
  public SessionView heartbeat(@PathVariable UUID id) {
    return sessions.heartbeat(id);
  }

  @PostMapping("/{id}/session/pause")
  @Operation(summary = "Pause (paused time does not count)")
  public SessionView pause(@PathVariable UUID id) {
    return sessions.pause(id);
  }

  @PostMapping("/{id}/session/resume")
  @Operation(summary = "Resume")
  public SessionView resume(@PathVariable UUID id) {
    return sessions.resume(id);
  }

  @PostMapping("/{id}/session/confirm-presence")
  @Operation(summary = "Answer \"still studying?\" (free mode, every 60 min)")
  public SessionView confirm(@PathVariable UUID id) {
    return sessions.confirmPresence(id);
  }

  @PostMapping("/{id}/session/stop")
  @Operation(summary = "Stop and save (counted by the server)")
  public SessionView stop(@PathVariable UUID id) {
    return sessions.stop(id);
  }

  // presença, ranking e reações -------------------------------------------------------

  @GetMapping("/{id}/presence")
  @Operation(summary = "Who is in the room now (members only; in-memory)")
  public Presence presence(@PathVariable UUID id) {
    return sessions.presence(id);
  }

  @GetMapping("/{id}/ranking")
  @Operation(summary = "Ranking by time (free mode) or completed cycles (Pomodoro)")
  public Ranking ranking(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "week") String period,
      @RequestParam(required = false) RoomMode mode,
      @RequestParam(defaultValue = UTC) String tz) {
    return sessions.ranking(id, period, mode, tz);
  }

  @PostMapping("/{id}/reactions")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Send a predefined reaction to a member (no free text; rate limited)")
  public void react(@PathVariable UUID id, @Valid @RequestBody RoomRequests.SendReaction body) {
    sessions.react(id, body);
  }

  @PostMapping("/{id}/report")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Report the room or a member (closed list of reasons; rate limited)")
  public void report(@PathVariable UUID id, @Valid @RequestBody RoomRequests.Report body) {
    rooms.report(id, body);
  }
}
