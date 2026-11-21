package com.stackstudy.common.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Limites de tentativas ({@code stackstudy.security.rate-limit.*}). Tudo em memória, por instância
 * (plano grátis: uma instância só); sem Redis nem serviço externo.
 *
 * @param login POST /api/auth/login por IP
 * @param register POST /api/auth/register por IP
 * @param refresh POST /api/auth/refresh por IP
 * @param loginFailures senhas erradas por e-mail (bloqueia o e-mail até a janela passar)
 * @param passwordFailures senha atual errada por usuário em /api/me (e-mail, senha, excluir)
 * @param admin qualquer método em /api/admin/** por IP
 */
@Validated
@ConfigurationProperties(prefix = "stackstudy.security.rate-limit")
public record RateLimitProperties(
    boolean enabled,
    @Valid @NotNull Rule login,
    @Valid @NotNull Rule register,
    @Valid @NotNull Rule refresh,
    @Valid @NotNull Rule loginFailures,
    @Valid @NotNull Rule passwordFailures,
    @Valid @NotNull Rule admin) {

  /** {@code limit} pedidos a cada {@code window}. */
  public record Rule(@Min(1) int limit, @NotNull Duration window) {}
}
