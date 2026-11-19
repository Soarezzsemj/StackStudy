package com.stackstudy.auth;

import com.stackstudy.auth.dto.UserResponse;
import com.stackstudy.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@Tag(name = "Auth")
public class MeController {

  private final AuthService authService;
  private final CurrentUser currentUser;

  public MeController(AuthService authService, CurrentUser currentUser) {
    this.authService = authService;
    this.currentUser = currentUser;
  }

  @GetMapping
  @Operation(summary = "Profile of the authenticated user")
  public UserResponse me() {
    return authService.me(currentUser.id());
  }
}
