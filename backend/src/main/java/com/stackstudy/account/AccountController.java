package com.stackstudy.account;

import com.stackstudy.account.dto.AccountRequests;
import com.stackstudy.account.dto.AccountResponses.Preferences;
import com.stackstudy.account.dto.AccountResponses.SessionView;
import com.stackstudy.auth.AuthService;
import com.stackstudy.auth.RefreshCookieFactory;
import com.stackstudy.auth.dto.AuthResponse;
import com.stackstudy.auth.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Perfil, preferências e conta do usuário logado (tudo sob {@code /api/me}). */
@RestController
@RequestMapping("/api/me")
@Tag(name = "Account")
public class AccountController {

  private final AccountService account;
  private final PreferencesService preferences;
  private final DataExportService export;
  private final RefreshCookieFactory cookies;

  public AccountController(
      AccountService account,
      PreferencesService preferences,
      DataExportService export,
      RefreshCookieFactory cookies) {
    this.account = account;
    this.preferences = preferences;
    this.export = export;
    this.cookies = cookies;
  }

  @PutMapping("/profile")
  @Operation(summary = "Update the profile (display name, course, institution, bio, avatar)")
  public UserResponse updateProfile(@Valid @RequestBody AccountRequests.Profile request) {
    return account.updateProfile(request);
  }

  @GetMapping("/preferences")
  @Operation(summary = "All preferences (language, time zone and the defaults of each module)")
  public Preferences preferences() {
    return preferences.get();
  }

  @PatchMapping("/preferences")
  @Operation(summary = "Change preferences; omitted fields are kept")
  public Preferences updatePreferences(
      @Valid @RequestBody AccountRequests.PreferencesPatch request) {
    return preferences.update(request);
  }

  @PutMapping("/email")
  @Operation(summary = "Change the email (requires the current password)")
  public UserResponse changeEmail(@Valid @RequestBody AccountRequests.ChangeEmail request) {
    return account.changeEmail(request);
  }

  @PutMapping("/password")
  @Operation(
      summary = "Change the password; ends every other session and rotates this one's cookie")
  public ResponseEntity<AuthResponse> changePassword(
      @Valid @RequestBody AccountRequests.ChangePassword request) {
    AuthService.AuthResult result = account.changePassword(request);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookies.create(result.refreshToken()).toString())
        .body(result.body());
  }

  @GetMapping("/sessions")
  @Operation(summary = "Active sessions (devices)")
  public List<SessionView> sessions() {
    return account.sessions();
  }

  @DeleteMapping("/sessions/{id}")
  @Operation(summary = "End one session")
  public ResponseEntity<Void> endSession(@PathVariable UUID id) {
    boolean current = account.endSession(id);
    return current ? noContentClearingCookie() : ResponseEntity.noContent().build();
  }

  @DeleteMapping("/sessions/others")
  @Operation(summary = "End every session except this one")
  public ResponseEntity<Void> endOtherSessions() {
    account.endOtherSessions();
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/sessions")
  @Operation(summary = "Sign out of every device, including this one")
  public ResponseEntity<Void> endAllSessions() {
    account.endAllSessions();
    return noContentClearingCookie();
  }

  @GetMapping("/export")
  @Operation(summary = "Export all my data as JSON")
  public ResponseEntity<Map<String, Object>> export() {
    String name = "stackstudy-export-" + LocalDate.now(ZoneOffset.UTC) + ".json";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
        .body(export.export());
  }

  @DeleteMapping
  @Operation(summary = "Delete my account and all its data (confirm with email and password)")
  public ResponseEntity<Void> deleteAccount(
      @Valid @RequestBody AccountRequests.DeleteAccount request) {
    account.deleteAccount(request);
    return noContentClearingCookie();
  }

  private ResponseEntity<Void> noContentClearingCookie() {
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
        .build();
  }
}
