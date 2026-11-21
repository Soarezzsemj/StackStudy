package com.stackstudy.auth;

import com.stackstudy.auth.email.EmailVerificationService;
import com.stackstudy.auth.google.GoogleAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Público: o que a tela de cadastro precisa saber (flags e versões), sem nenhum segredo. */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth")
@SecurityRequirements
public class SignupConfigController {

  /**
   * @param minAge idade mínima configurada (padrão 18)
   */
  public record SignupConfig(
      boolean googleEnabled,
      boolean emailVerificationEnabled,
      boolean profilePhotoEnabled,
      Integer minAge,
      String termsVersion,
      String privacyVersion) {}

  private final SignupProperties properties;
  private final GoogleAuthService google;
  private final EmailVerificationService verification;

  public SignupConfigController(
      SignupProperties properties,
      GoogleAuthService google,
      EmailVerificationService verification) {
    this.properties = properties;
    this.google = google;
    this.verification = verification;
  }

  @GetMapping("/config")
  @Operation(summary = "Sign-up flags and terms versions (public)")
  public SignupConfig config() {
    return new SignupConfig(
        google.enabled(),
        verification.enabled(),
        properties.profilePhotoEnabled(),
        properties.minAge(),
        properties.termsVersion(),
        properties.privacyVersion());
  }
}
