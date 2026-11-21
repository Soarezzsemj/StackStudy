package com.stackstudy.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cadastro ({@code stackstudy.signup.*}).
 *
 * @param termsVersion versão dos Termos de uso aceita no cadastro ({@code TERMS_VERSION})
 * @param privacyVersion versão da Política de Privacidade ({@code PRIVACY_VERSION})
 * @param minAge idade mínima ({@code MIN_AGE}); sem valor = a caixa pede só "a idade mínima", sem
 *     número. A idade e a regra para menores de 18 dependem de decisão jurídica.
 * @param emailVerificationEnabled {@code EMAIL_VERIFICATION_ENABLED} (padrão false)
 * @param profilePhotoEnabled {@code PROFILE_PHOTO_ENABLED} (padrão false: não há armazenamento de
 *     imagem no plano grátis; o avatar são as iniciais)
 * @param verificationTtl validade do link de verificação de e-mail
 */
@Validated
@ConfigurationProperties(prefix = "stackstudy.signup")
public record SignupProperties(
    @NotBlank String termsVersion,
    @NotBlank String privacyVersion,
    @Min(1) @Max(120) Integer minAge,
    boolean emailVerificationEnabled,
    boolean profilePhotoEnabled,
    @NotNull Duration verificationTtl,
    @NotBlank String appUrl,
    @Valid @NotNull Google google) {

  /**
   * Login com Google (OIDC, fluxo de código com PKCE no servidor). Desligado por padrão; sem client
   * id/secret no repositório.
   */
  public record Google(boolean enabled, String clientId, String clientSecret, String redirectUri) {

    public boolean configured() {
      return notBlank(clientId) && notBlank(clientSecret) && notBlank(redirectUri);
    }

    private static boolean notBlank(String s) {
      return s != null && !s.isBlank();
    }
  }
}
