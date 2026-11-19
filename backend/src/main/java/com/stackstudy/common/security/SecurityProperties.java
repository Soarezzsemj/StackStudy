package com.stackstudy.common.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configurações de segurança ({@code stackstudy.security.*}), todas ajustáveis por variável de
 * ambiente. Se forem inválidas, a aplicação não sobe.
 */
@Validated
@ConfigurationProperties(prefix = "stackstudy.security")
public record SecurityProperties(
    @Valid @NotNull Jwt jwt,
    @Valid @NotNull RefreshCookie refreshCookie,
    @Valid @NotNull Cors cors) {

  /** Tamanho mínimo do segredo do JWT, em bytes (HMAC-SHA256 pede 256 bits). */
  public static final int MIN_SECRET_BYTES = 32;

  public record Jwt(
      @NotBlank(message = "JWT_SECRET must be set") String secret,
      @NotBlank String issuer,
      @NotNull Duration accessTokenTtl,
      @NotNull Duration refreshTokenTtl) {

    @AssertTrue(message = "JWT_SECRET must have at least 32 bytes")
    public boolean isSecretStrong() {
      // Segredo ausente já cai no @NotBlank; aqui só conferimos o tamanho.
      return secret == null
          || secret.isBlank()
          || secret.getBytes(StandardCharsets.UTF_8).length >= MIN_SECRET_BYTES;
    }
  }

  public record RefreshCookie(
      @NotBlank String name, @NotBlank String path, boolean secure, @NotBlank String sameSite) {}

  public record Cors(List<String> allowedOrigins) {
    public Cors {
      // Ignora itens vazios (ex.: ALLOWED_ORIGINS="" ou "a.com, ").
      allowedOrigins =
          allowedOrigins == null
              ? List.of()
              : allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
  }
}
