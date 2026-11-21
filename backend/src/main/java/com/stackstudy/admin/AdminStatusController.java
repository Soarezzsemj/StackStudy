package com.stackstudy.admin;

import com.stackstudy.admin.dto.AdminResponses.ErrorRow;
import com.stackstudy.admin.dto.AdminResponses.Health;
import com.stackstudy.admin.dto.AdminResponses.Overview;
import com.stackstudy.admin.dto.AdminResponses.Signup;
import com.stackstudy.room.RoomService;
import com.stackstudy.room.dto.RoomResponses.ReportView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Painel de status (só leitura). Papel ADMIN conferido no banco a cada requisição pelo {@link
 * AdminAccessInterceptor}; cada acesso fica em {@code admin_access_log}.
 */
@RestController
@RequestMapping("/api/admin/status")
@Tag(name = "Admin")
public class AdminStatusController {

  private final AdminStatusService status;
  private final RoomService rooms;

  public AdminStatusController(AdminStatusService status, RoomService rooms) {
    this.status = status;
    this.rooms = rooms;
  }

  @GetMapping("/overview")
  @Operation(summary = "Users, activity, retention, funnel and feature usage (aggregated)")
  public Overview overview(@RequestParam(defaultValue = "7d") String range) {
    return status.overview(range);
  }

  @GetMapping("/health")
  @Operation(summary = "What the app can measure itself: database, JVM, build and starts")
  public Health health() {
    return status.health();
  }

  @GetMapping("/signups")
  @Operation(summary = "Latest 20 sign-ups with masked email")
  public List<Signup> signups() {
    return status.signups();
  }

  @GetMapping("/errors")
  @Operation(summary = "Error responses of the last 24 h, by route pattern and status")
  public List<ErrorRow> errors() {
    return status.errors();
  }

  @GetMapping("/reports")
  @Operation(summary = "Latest 50 study room reports (read-only; no emails)")
  public List<ReportView> reports() {
    return rooms.reportsForAdmin();
  }
}
