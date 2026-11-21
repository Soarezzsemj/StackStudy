package com.stackstudy.admin;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sincroniza o papel ADMIN com a variável ADMIN_EMAILS, só na inicialização:
 *
 * <ul>
 *   <li>promove contas que <b>já existem</b> com esses e-mails;
 *   <li>rebaixa para USER quem é ADMIN e saiu da lista (lista vazia = nenhum admin).
 * </ul>
 *
 * Nunca no cadastro: quem registrar o e-mail do dono antes dele não vira admin na hora (e o dono
 * percebe ao não conseguir se cadastrar). Por isso o DEPLOY.md manda criar a conta primeiro e só
 * depois configurar a variável. É o único código que grava {@code users.role}.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

  private final JdbcTemplate jdbc;
  private final AdminProperties props;

  public AdminBootstrap(JdbcTemplate jdbc, AdminProperties props) {
    this.jdbc = jdbc;
    this.props = props;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    sync(props.emails());
  }

  /** Devolve [promovidos, rebaixados]. */
  @Transactional
  public int[] sync(List<String> emails) {
    int promoted = 0;
    int demoted;
    if (emails.isEmpty()) {
      demoted = jdbc.update("UPDATE users SET role = 'USER' WHERE role = 'ADMIN'");
    } else {
      String in = String.join(",", emails.stream().map(e -> "?").toList());
      Object[] params = emails.toArray();
      promoted =
          jdbc.update(
              "UPDATE users SET role = 'ADMIN' WHERE role <> 'ADMIN' AND email IN (" + in + ")",
              params);
      demoted =
          jdbc.update(
              "UPDATE users SET role = 'USER' WHERE role = 'ADMIN' AND email NOT IN (" + in + ")",
              params);
    }
    // Só contagens no log: e-mail é dado pessoal.
    log.info(
        "Admin roles synced from ADMIN_EMAILS: {} listed, {} promoted, {} demoted",
        emails.size(),
        promoted,
        demoted);
    return new int[] {promoted, demoted};
  }
}
