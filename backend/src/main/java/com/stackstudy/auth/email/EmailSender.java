package com.stackstudy.auth.email;

/**
 * Envio de e-mail. Nesta fase só existe {@link LogEmailSender} (desenvolvimento: escreve no log,
 * não envia nada). Para produção, crie um bean com o provedor escolhido e marque-o com
 * {@code @Primary}; nenhum provedor está configurado no projeto.
 */
public interface EmailSender {

  record EmailMessage(String to, String subject, String text) {}

  void send(EmailMessage message);
}
