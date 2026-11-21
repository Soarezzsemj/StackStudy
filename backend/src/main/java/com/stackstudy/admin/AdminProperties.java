package com.stackstudy.admin;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do painel ({@code stackstudy.admin.*}).
 *
 * @param emails ADMIN_EMAILS: contas (já existentes) que viram ADMIN na inicialização
 * @param commit commit em produção (o Render injeta RENDER_GIT_COMMIT)
 * @param links painéis dos provedores (o app não mede horas do Render, CU-horas do Neon nem tráfego
 *     da Vercel sem tokens; o painel só mostra o link)
 */
@ConfigurationProperties(prefix = "stackstudy.admin")
public record AdminProperties(List<String> emails, String commit, Links links) {

  public record Links(String render, String neon, String vercel, String backup) {}

  public AdminProperties {
    // Mesmo tratamento do cadastro: trim + minúsculas; itens vazios saem.
    emails =
        emails == null
            ? List.of()
            : emails.stream()
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .filter(e -> !e.isEmpty())
                .distinct()
                .toList();
    commit = blankToNull(commit);
    links =
        links == null
            ? new Links(null, null, null, null)
            : new Links(
                httpsOrNull(links.render()),
                httpsOrNull(links.neon()),
                httpsOrNull(links.vercel()),
                httpsOrNull(links.backup()));
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  /** Link só vale com https (evita javascript:/http: vindo de configuração errada). */
  static String httpsOrNull(String value) {
    String v = blankToNull(value);
    return v != null && v.startsWith("https://") ? v : null;
  }
}
