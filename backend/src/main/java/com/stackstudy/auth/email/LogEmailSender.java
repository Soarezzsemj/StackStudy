package com.stackstudy.auth.email;

import com.stackstudy.admin.EmailMask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Só para desenvolvimento: não envia nada, escreve o e-mail no log (destinatário mascarado). O
 * corpo pode ter um link de verificação, por isso o {@code EmailVerificationService} recusa subir
 * em produção com a verificação ligada e só este "envio".
 */
@Component
public class LogEmailSender implements EmailSender {

  private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

  @Override
  public void send(EmailMessage message) {
    log.info(
        "[dev e-mail, not sent] to={} subject=\"{}\"\n{}",
        EmailMask.mask(message.to()),
        message.subject(),
        message.text());
  }
}
