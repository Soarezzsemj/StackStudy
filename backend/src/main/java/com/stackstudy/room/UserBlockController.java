package com.stackstudy.room;

import com.stackstudy.room.dto.RoomResponses.BlockedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Bloqueio entre usuários: nenhum dos dois vê a presença nem recebe reações do outro. */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Study rooms")
public class UserBlockController {

  private final RoomService rooms;

  public UserBlockController(RoomService rooms) {
    this.rooms = rooms;
  }

  @GetMapping("/blocks")
  @Operation(summary = "Users I blocked (with a nickname from a shared room)")
  public List<BlockedUser> blocks() {
    return rooms.blocks();
  }

  @PostMapping("/{id}/block")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Block a user")
  public void block(@PathVariable UUID id) {
    rooms.block(id);
  }

  @DeleteMapping("/{id}/block")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Unblock a user")
  public void unblock(@PathVariable UUID id) {
    rooms.unblock(id);
  }
}
