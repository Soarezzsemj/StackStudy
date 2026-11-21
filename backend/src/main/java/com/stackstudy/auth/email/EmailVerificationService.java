package com.stackstudy.auth.email;

import com.stackstudy.auth.RefreshTokenGenerator;
import com.stackstudy.auth.SignupProperties;
import com.stackstudy.auth.User;
import com.stackstudy.auth.UserRepository;
import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.error.ErrorCode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verificação de e-mail ({@code EMAIL_VERIFICATION_ENABLED}, desligada por padrão): token aleatório
 * de 32 bytes, só o SHA-256 no banco, uso único e com validade ({@code verification-ttl}).
 *
 * <p>Também é o que fecha a enumeração de contas no cadastro: com a verificação ligada, e-mail novo
 * e e-mail já cadastrado recebem a mesma resposta; o dono do e-mail existente recebe um aviso.
 */
@Service
public class EmailVerificationService {

  private final JdbcTemplate jdbc;
  private final UserRepository users;
  private final RefreshTokenGenerator tokens;
  private final EmailSender sender;
  private final SignupProperties properties;
  private final Clock clock;

  public EmailVerificationService(
      JdbcTemplate jdbc,
      UserRepository users,
      RefreshTokenGenerator tokens,
      EmailSender sender,
      SignupProperties properties,
      Environment environment,
      Clock clock) {
    this.jdbc = jdbc;
    this.users = users;
    this.tokens = tokens;
    this.sender = sender;
    this.properties = properties;
    this.clock = clock;
    boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
    if (prod && properties.emailVerificationEnabled() && sender instanceof LogEmailSender) {
      // O log de dev escreveria o link (com o token) nos logs de produção.
      throw new IllegalStateException(
          "EMAIL_VERIFICATION_ENABLED=true in prod needs a real EmailSender bean (none configured)");
    }
  }

  public boolean enabled() {
    return properties.emailVerificationEnabled();
  }

  /** Gera o token e manda o link para o e-mail do usuário. */
  @Transactional
  public void sendVerification(User user) {
    String raw = tokens.newToken();
    jdbc.update(
        "INSERT INTO email_verification_tokens (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
        user.getId(),
        tokens.hash(raw),
        Timestamp.from(clock.instant().plus(properties.verificationTtl())));
    String link = properties.appUrl() + "/verify-email?token=" + raw;
    sender.send(
        new EmailSender.EmailMessage(
            user.getEmail(),
            "Confirme seu e-mail no StackStudy",
            "Olá! Para ativar sua conta, abra o link abaixo (vale por "
                + properties.verificationTtl().toHours()
                + " h):\n"
                + link
                + "\n\nSe não foi você, ignore esta mensagem."));
  }

  /** Alguém tentou se cadastrar com um e-mail que já tem conta: avisa o dono (sem link). */
  public void notifyExistingAccount(String email) {
    sender.send(
        new EmailSender.EmailMessage(
            email,
            "Tentativa de cadastro no StackStudy",
            "Alguém tentou criar uma conta com este e-mail, que já está cadastrado. Se foi você, "
                + "entre normalmente em "
                + properties.appUrl()
                + "/login. Se não foi, nada mudou na sua conta."));
  }

  /** Confirma o token (uma vez só, dentro da validade) e devolve o usuário verificado. */
  @Transactional
  public User verify(String rawToken) {
    if (rawToken == null || rawToken.isBlank() || rawToken.length() > 200) {
      throw invalid();
    }
    Instant now = clock.instant();
    List<UUID> found =
        jdbc.query(
            "UPDATE email_verification_tokens SET used_at = ? WHERE token_hash = ?"
                + " AND used_at IS NULL AND expires_at > ? RETURNING user_id",
            (rs, i) -> rs.getObject(1, UUID.class),
            Timestamp.from(now),
            tokens.hash(rawToken),
            Timestamp.from(now));
    if (found.isEmpty()) {
      throw invalid();
    }
    User user = users.findById(found.get(0)).orElseThrow(EmailVerificationService::invalid);
    user.markEmailVerified(now);
    return user;
  }

  private static ApiException invalid() {
    return new ApiException(ErrorCode.INVALID_VERIFICATION_TOKEN, "Invalid or expired link");
  }
}
