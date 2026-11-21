package com.stackstudy.account;

import com.stackstudy.account.dto.AccountRequests;
import com.stackstudy.account.dto.AccountResponses.SessionView;
import com.stackstudy.auth.ActiveSessionCache;
import com.stackstudy.auth.AuthService;
import com.stackstudy.auth.AuthSessionRepository;
import com.stackstudy.auth.User;
import com.stackstudy.auth.UserRepository;
import com.stackstudy.auth.dto.UserResponse;
import com.stackstudy.common.error.ApiError;
import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.error.ErrorCode;
import com.stackstudy.common.security.CurrentUser;
import com.stackstudy.subject.SubjectColors;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Perfil e conta do usuário logado: dados do perfil, e-mail, senha, dispositivos e exclusão. Toda
 * ação sensível pede a senha atual. Nunca loga senha, token ou hash.
 */
@Service
public class AccountService {

  private static final Logger log = LoggerFactory.getLogger(AccountService.class);

  private final UserRepository users;
  private final AuthSessionRepository sessions;
  private final AuthService auth;
  private final ActiveSessionCache sessionCache;
  private final PasswordEncoder passwordEncoder;
  private final JdbcTemplate jdbc;
  private final CurrentUser currentUser;
  private final Clock clock;

  public AccountService(
      UserRepository users,
      AuthSessionRepository sessions,
      AuthService auth,
      ActiveSessionCache sessionCache,
      PasswordEncoder passwordEncoder,
      JdbcTemplate jdbc,
      CurrentUser currentUser,
      Clock clock) {
    this.users = users;
    this.sessions = sessions;
    this.auth = auth;
    this.sessionCache = sessionCache;
    this.passwordEncoder = passwordEncoder;
    this.jdbc = jdbc;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  @Transactional
  public UserResponse updateProfile(AccountRequests.Profile r) {
    User user = user();
    user.updateProfile(
        r.name().trim(),
        blankToNull(r.course()),
        blankToNull(r.institution()),
        blankToNull(r.currentPeriod()),
        blankToNull(r.bio()),
        r.avatarColor() == null || r.avatarColor().isBlank()
            ? null
            : SubjectColors.normalize(r.avatarColor()));
    return UserResponse.from(user);
  }

  /** Novo e-mail (normalizado). Exige a senha atual; e-mail de outra conta = 409. */
  @Transactional
  public UserResponse changeEmail(AccountRequests.ChangeEmail r) {
    User user = user();
    requirePassword(user, r.currentPassword());
    String email = AuthService.normalizeEmail(r.email());
    if (!email.equals(user.getEmail())) {
      if (users.existsByEmail(email)) {
        throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, "Email already registered");
      }
      user.changeEmail(email);
      try {
        users.saveAndFlush(user);
      } catch (DataIntegrityViolationException ex) {
        // Corrida: outra conta pegou o mesmo e-mail entre a checagem e o UPDATE.
        throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, "Email already registered");
      }
    }
    return UserResponse.from(user);
  }

  /**
   * Nova senha. Exige a atual, encerra todas as outras sessões e gira o refresh token desta (o
   * cookie antigo deixa de valer). Devolve os tokens novos da sessão atual.
   */
  @Transactional
  public AuthService.AuthResult changePassword(AccountRequests.ChangePassword r) {
    User user = user();
    requirePassword(user, r.currentPassword());
    if (passwordEncoder.matches(r.newPassword(), user.getPasswordHash())) {
      throw ApiException.invalidField("newPassword", "must be different from the current one");
    }
    user.changePasswordHash(passwordEncoder.encode(r.newPassword()));
    UUID current = currentUser.sessionId();
    auth.endAllSessions(user.getId(), current);
    log.info("Password changed for user {}; other sessions ended", user.getId());
    return auth.rotateSession(user, current);
  }

  @Transactional(readOnly = true)
  public List<SessionView> sessions() {
    UUID current = currentUser.sessionId();
    return sessions.findActive(currentUser.id(), clock.instant()).stream()
        .map(s -> SessionView.from(s, current))
        .toList();
  }

  /** Encerra uma sessão. Devolve true se era a atual (aí o cookie também é limpo). */
  @Transactional
  public boolean endSession(UUID sessionId) {
    auth.endSession(currentUser.id(), sessionId);
    return sessionId.equals(currentUser.sessionId());
  }

  @Transactional
  public void endOtherSessions() {
    auth.endAllSessions(currentUser.id(), currentUser.sessionId());
  }

  /** Sair de todos os dispositivos, inclusive este. */
  @Transactional
  public void endAllSessions() {
    auth.endAllSessions(currentUser.id(), null);
  }

  /**
   * Exclui a conta e tudo dela. Confirmação: digitar o e-mail da conta e a senha atual. O banco
   * apaga em cascata (todas as tabelas têm {@code ON DELETE CASCADE} até {@code users}).
   */
  @Transactional
  public void deleteAccount(AccountRequests.DeleteAccount r) {
    User user = user();
    if (!AuthService.normalizeEmail(r.email()).equals(user.getEmail())) {
      throw ApiException.invalidField("email", "must match the account email");
    }
    requirePassword(user, r.currentPassword());
    UUID id = user.getId();
    // DELETE direto: o cascade é do banco (não depende do mapeamento JPA).
    jdbc.update("DELETE FROM users WHERE id = ?", id);
    sessionCache.evictUser(id);
    log.info("Account {} deleted by its owner", id);
  }

  private User user() {
    return users
        .findById(currentUser.id())
        .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
  }

  private void requirePassword(User user, String password) {
    if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
      throw new ApiException(
          ErrorCode.INVALID_CURRENT_PASSWORD,
          "Current password is incorrect",
          List.of(new ApiError.FieldProblem("currentPassword", "is incorrect")));
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
