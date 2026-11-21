package com.stackstudy.account;

import com.stackstudy.account.dto.AccountRequests.PreferencesPatch;
import com.stackstudy.account.dto.AccountResponses.Preferences;
import com.stackstudy.auth.User;
import com.stackstudy.auth.UserRepository;
import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.security.CurrentUser;
import com.stackstudy.flashcard.FlashcardSettingsService;
import com.stackstudy.flashcard.dto.FlashcardRequests;
import com.stackstudy.flashcard.dto.FlashcardResponses;
import com.stackstudy.grade.GradeSettingsService;
import com.stackstudy.grade.dto.GradeRequests;
import com.stackstudy.grade.dto.GradeResponses;
import com.stackstudy.session.DeepWorkSettingsService;
import com.stackstudy.session.dto.SessionRequests;
import com.stackstudy.session.dto.SessionResponses;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Preferências da tela de Perfil. Não duplica nada: idioma e fuso ficam no usuário; notas, Deep
 * Work e flashcards continuam nos settings de cada fase (que as telas já usam). Esta é só a porta
 * única, e grava tudo numa transação.
 */
@Service
public class PreferencesService {

  private final UserRepository users;
  private final GradeSettingsService grades;
  private final DeepWorkSettingsService deepWork;
  private final FlashcardSettingsService flashcards;
  private final CurrentUser currentUser;

  public PreferencesService(
      UserRepository users,
      GradeSettingsService grades,
      DeepWorkSettingsService deepWork,
      FlashcardSettingsService flashcards,
      CurrentUser currentUser) {
    this.users = users;
    this.grades = grades;
    this.deepWork = deepWork;
    this.flashcards = flashcards;
    this.currentUser = currentUser;
  }

  @Transactional(readOnly = true)
  public Preferences get() {
    return assemble(user(), grades.get(), deepWork.get(), flashcards.get());
  }

  @Transactional
  public Preferences update(PreferencesPatch p) {
    User user = user();
    if (p.locale() != null) {
      user.setLocale(p.locale());
    }
    if (p.timeZone() != null) {
      user.setTimeZone(p.timeZone().isBlank() ? null : validZone(p.timeZone().trim()));
    }

    GradeResponses.Settings g = grades.get();
    if (p.gradeMethod() != null
        || p.gradeScaleMax() != null
        || p.passingGrade() != null
        || p.maxAbsencePct() != null) {
      // Mantém o resto (exame, CR, arredondamento); a validação entre campos é a das notas.
      g =
          grades.update(
              new GradeRequests.Settings(
                  or(p.gradeMethod(), g.method()),
                  or(p.gradeScaleMax(), g.scaleMax()),
                  or(p.passingGrade(), g.passingGrade()),
                  or(p.maxAbsencePct(), g.maxAbsencePct()),
                  g.finalExamEnabled(),
                  g.finalExamMinAverage(),
                  g.finalExamAverageWeight(),
                  g.finalExamPassingGrade(),
                  g.gpaScale(),
                  g.gpaWeight(),
                  g.roundBeforeCompare(),
                  g.roundingScale()));
    }

    SessionResponses.Settings d = deepWork.get();
    if (p.focusMinutes() != null
        || p.shortBreakMinutes() != null
        || p.longBreakMinutes() != null
        || p.longBreakEvery() != null
        || p.weeklyGoalMinutes() != null) {
      d =
          deepWork.update(
              new SessionRequests.Settings(
                  or(p.focusMinutes(), d.focusMinutes()),
                  or(p.shortBreakMinutes(), d.shortBreakMinutes()),
                  or(p.longBreakMinutes(), d.longBreakMinutes()),
                  or(p.longBreakEvery(), d.longBreakEvery()),
                  or(p.weeklyGoalMinutes(), d.weeklyGoalMinutes()),
                  d.soundEnabled(),
                  d.notificationsEnabled()));
    }

    FlashcardResponses.Settings f = flashcards.get();
    if (p.newCardsPerDay() != null || p.reviewsPerDay() != null) {
      f =
          flashcards.update(
              new FlashcardRequests.Settings(
                  or(p.newCardsPerDay(), f.newPerDay()), or(p.reviewsPerDay(), f.reviewsPerDay())));
    }
    return assemble(user, g, d, f);
  }

  private User user() {
    return users
        .findById(currentUser.id())
        .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
  }

  /** Só nomes IANA (ex.: America/Sao_Paulo), como no Deep Work. */
  static String validZone(String tz) {
    if (!"UTC".equals(tz) && !ZoneId.getAvailableZoneIds().contains(tz)) {
      throw ApiException.invalidField(
          "timeZone", "must be an IANA time zone, e.g. America/Sao_Paulo");
    }
    return tz;
  }

  private static Preferences assemble(
      User u,
      GradeResponses.Settings g,
      SessionResponses.Settings d,
      FlashcardResponses.Settings f) {
    return new Preferences(
        u.getLocale(),
        u.getTimeZone(),
        g.method(),
        g.scaleMax(),
        g.passingGrade(),
        g.maxAbsencePct(),
        d.focusMinutes(),
        d.shortBreakMinutes(),
        d.longBreakMinutes(),
        d.longBreakEvery(),
        d.weeklyGoalMinutes(),
        f.newPerDay(),
        f.reviewsPerDay());
  }

  private static <T> T or(T value, T fallback) {
    return value != null ? value : fallback;
  }
}
