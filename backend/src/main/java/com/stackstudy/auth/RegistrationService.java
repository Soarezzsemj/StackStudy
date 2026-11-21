package com.stackstudy.auth;

import com.stackstudy.auth.SignupEnums.FocusRhythm;
import com.stackstudy.auth.dto.RegisterRequest;
import com.stackstudy.auth.email.EmailVerificationService;
import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.error.ErrorCode;
import com.stackstudy.common.error.RateLimitedException;
import com.stackstudy.common.security.RateLimitProperties;
import com.stackstudy.common.security.RateLimiter;
import com.stackstudy.roadmap.RoadmapService;
import com.stackstudy.session.DeepWorkSettings;
import com.stackstudy.session.DeepWorkSettingsRepository;
import java.time.Clock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro em 3 passos (conta, perfil e rotina) numa transação só: usuário, perfil, aceite,
 * preferências do Deep Work e roadmap seguido entram juntos ou nada entra.
 *
 * <p>E-mail já cadastrado: com a verificação de e-mail desligada (padrão), responde 409 com
 * mensagem neutra (a conta não fica criada, e o cadastro abre sessão na hora, então não dá para
 * esconder completamente). Mitigação: o BCrypt roda sempre (mesmo tempo), rate limit por IP ({@code
 * RateLimitFilter}) e por e-mail. Com a verificação ligada, a resposta é a mesma para e-mail novo e
 * existente (202) e o dono do e-mail recebe um aviso: sem enumeração.
 */
@Service
public class RegistrationService {

  private static final String DEFAULT_LOCALE = "pt-BR";

  /** Resultado: sessão aberta ou "verifique seu e-mail" (mesma resposta para e-mail existente). */
  public record Outcome(AuthService.AuthResult session) {
    public static Outcome verificationSent() {
      return new Outcome(null);
    }

    public boolean verificationSentOnly() {
      return session == null;
    }
  }

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final AuthService auth;
  private final DeepWorkSettingsRepository deepWorkSettings;
  private final RoadmapService roadmaps;
  private final EmailVerificationService verification;
  private final SignupProperties properties;
  private final RateLimiter limiter;
  private final RateLimitProperties rateLimits;
  private final Clock clock;

  public RegistrationService(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      AuthService auth,
      DeepWorkSettingsRepository deepWorkSettings,
      RoadmapService roadmaps,
      EmailVerificationService verification,
      SignupProperties properties,
      RateLimiter limiter,
      RateLimitProperties rateLimits,
      Clock clock) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.auth = auth;
    this.deepWorkSettings = deepWorkSettings;
    this.roadmaps = roadmaps;
    this.verification = verification;
    this.properties = properties;
    this.limiter = limiter;
    this.rateLimits = rateLimits;
    this.clock = clock;
  }

  @Transactional
  public Outcome register(RegisterRequest request, String userAgent) {
    String email = AuthService.normalizeEmail(request.email());
    if (rateLimits.enabled()) {
      // Por e-mail (o por IP fica no RateLimitFilter): tentativas repetidas no mesmo endereço.
      RateLimitProperties.Rule rule = rateLimits.register();
      RateLimiter.Decision d =
          limiter.acquire("register-email:" + email, rule.limit(), rule.window());
      if (!d.allowed()) {
        throw new RateLimitedException(d.retryAfterSeconds());
      }
    }
    if (!Boolean.TRUE.equals(request.acceptTerms()) || !Boolean.TRUE.equals(request.confirmAge())) {
      throw ApiException.invalidField("acceptTerms", "must be accepted");
    }
    PasswordPolicy.check("password", request.password(), email);
    // Sempre: o custo do BCrypt não diz se o e-mail existe.
    String hash = passwordEncoder.encode(request.password());

    if (users.existsByEmail(email)) {
      return alreadyRegistered(email);
    }
    User user =
        new User(
            request.name().trim(),
            email,
            hash,
            request.locale() == null ? DEFAULT_LOCALE : request.locale());
    FocusRhythm rhythm =
        request.focusRhythm() == null ? FocusRhythm.POMODORO_25_5 : request.focusRhythm();
    user.applySignup(
        blankToNull(request.nickname()),
        request.area() == null ? null : request.area().name(),
        blankToNull(request.course()),
        blankToNull(request.period()),
        blankToNull(request.institution()),
        rhythm.name(),
        request.deadlineReminders() == null || request.deadlineReminders(),
        Boolean.TRUE.equals(request.roomsVisible()));
    user.recordConsent(
        properties.termsVersion(),
        properties.privacyVersion(),
        clock.instant(),
        properties.minAge());
    if (verification.enabled()) {
      user.requireEmailVerification();
    }
    try {
      user = users.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      // Corrida: outro cadastro com o mesmo e-mail entrou antes.
      return alreadyRegistered(email);
    }

    // Rotina: ritmo de foco e meta semanal viram as preferências do Deep Work.
    DeepWorkSettings settings = new DeepWorkSettings(user.getId());
    settings.update(
        rhythm.focusMinutes(),
        rhythm.shortBreakMinutes(),
        settings.getLongBreakMinutes(),
        settings.getLongBreakEvery(),
        request.weeklyGoalHours() == null
            ? settings.getWeeklyGoalMinutes()
            : request.weeklyGoalHours() * 60,
        settings.isSoundEnabled(),
        settings.isNotificationsEnabled());
    deepWorkSettings.save(settings);

    // Roadmap sugerido (Fase 9): segue se existir; slug desconhecido é ignorado.
    if (request.followRoadmap() != null) {
      roadmaps.followForUser(user.getId(), request.followRoadmap());
    }

    if (verification.enabled()) {
      verification.sendVerification(user);
      return Outcome.verificationSent();
    }
    return new Outcome(auth.startSession(user, userAgent));
  }

  private Outcome alreadyRegistered(String email) {
    if (verification.enabled()) {
      verification.notifyExistingAccount(email);
      return Outcome.verificationSent();
    }
    throw new ApiException(
        ErrorCode.EMAIL_ALREADY_REGISTERED, "Could not create an account with this email");
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
