package com.stackstudy.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackstudy.session.SessionEnums.Mode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Regras puras: tempo efetivo, ciclo de vida na entidade e sequências. */
class SessionLogicTest {

  private static final Instant T0 = Instant.parse("2026-11-20T10:00:00Z");

  private static Instant at(long seconds) {
    return T0.plusSeconds(seconds);
  }

  @Test
  void elapsedIgnoresPausesAndStopsWhilePaused() {
    assertThat(SessionClock.elapsedSeconds(T0, 0, null, at(600))).isEqualTo(600);
    assertThat(SessionClock.elapsedSeconds(T0, 120, null, at(600))).isEqualTo(480);
    // Pausada aos 300 s: o relógio para ali, não importa quanto tempo passe.
    assertThat(SessionClock.elapsedSeconds(T0, 0, at(300), at(5000))).isEqualTo(300);
    // Relógio "voltando" nunca dá negativo.
    assertThat(SessionClock.elapsedSeconds(T0, 0, null, at(-10))).isZero();
  }

  @Test
  void effectiveIsCappedByPlanInPomodoroAndByMaxInFlow() {
    assertThat(SessionClock.effectiveSeconds(T0, 0, null, at(7200), Mode.POMODORO, 25))
        .isEqualTo(1500);
    assertThat(SessionClock.effectiveSeconds(T0, 0, null, at(600), Mode.POMODORO, 25))
        .isEqualTo(600);
    assertThat(SessionClock.effectiveSeconds(T0, 0, null, at(7200), Mode.FLOW, null))
        .isEqualTo(7200);
    assertThat(SessionClock.effectiveSeconds(T0, 0, null, at(100_000), Mode.FLOW, null))
        .isEqualTo(SessionClock.MAX_SESSION_SECONDS);
  }

  @Test
  void lifecycleStartPauseResumeComplete() {
    StudySession s =
        StudySession.start(UUID.randomUUID(), UUID.randomUUID(), null, Mode.FLOW, null, T0);
    assertThat(s.isActive()).isTrue();

    s.pause(at(600));
    s.pause(at(700)); // pausar de novo não muda o início da pausa
    assertThat(s.isPaused()).isTrue();
    assertThat(s.effectiveSecondsAt(at(900))).isEqualTo(600);

    s.resume(at(900));
    s.resume(at(950)); // retomar de novo não soma nada
    assertThat(s.getPausedSeconds()).isEqualTo(300);
    assertThat(s.effectiveSecondsAt(at(1200))).isEqualTo(900);

    s.complete(at(1500), "capítulo 3 revisado", (short) 4);
    assertThat(s.isActive()).isFalse();
    assertThat(s.getEffectiveSeconds()).isEqualTo(1200);
    assertThat(s.getEndedAt()).isEqualTo(at(1500));
    assertThat(s.getNote()).isEqualTo("capítulo 3 revisado");
  }

  @Test
  void completingWhilePausedClosesThePause() {
    StudySession s =
        StudySession.start(UUID.randomUUID(), UUID.randomUUID(), null, Mode.FLOW, null, T0);
    s.pause(at(1000));
    s.complete(at(4000), null, null);
    assertThat(s.getEffectiveSeconds()).isEqualTo(1000);
    assertThat(s.isPaused()).isFalse();
    assertThat(s.getPausedSeconds()).isEqualTo(3000);
  }

  @Test
  void manualAndEditUseStartPlusDuration() {
    StudySession s = StudySession.manual(UUID.randomUUID(), UUID.randomUUID(), null, T0, 90);
    assertThat(s.isActive()).isFalse();
    assertThat(s.getEffectiveSeconds()).isEqualTo(5400);
    assertThat(s.getEndedAt()).isEqualTo(at(5400));
    s.edit(s.getSubjectId(), null, at(60), 30);
    assertThat(s.getEffectiveSeconds()).isEqualTo(1800);
  }

  @Test
  void streaksCountConsecutiveLocalDays() {
    LocalDate today = LocalDate.of(2026, 11, 20);
    List<LocalDate> days =
        List.of(
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 2),
            LocalDate.of(2026, 11, 3),
            LocalDate.of(2026, 11, 4),
            LocalDate.of(2026, 11, 18),
            LocalDate.of(2026, 11, 19),
            LocalDate.of(2026, 11, 20));
    assertThat(StreakCalculator.compute(days, today)).isEqualTo(new StreakCalculator.Streaks(3, 4));
  }

  @Test
  void streakSurvivesUntilTheDayEndsAndBreaksAfter() {
    List<LocalDate> days = List.of(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 19));
    // Hoje (20) ainda sem estudo: a sequência de ontem continua valendo.
    assertThat(StreakCalculator.compute(days, LocalDate.of(2026, 11, 20)).current()).isEqualTo(2);
    // Virou o dia 21 sem estudar no dia 20: quebrou.
    assertThat(StreakCalculator.compute(days, LocalDate.of(2026, 11, 21)).current()).isZero();
    assertThat(StreakCalculator.compute(days, LocalDate.of(2026, 11, 21)).longest()).isEqualTo(2);
  }

  @Test
  void streakAcrossMonthAndYearAndEmpty() {
    List<LocalDate> days = List.of(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1));
    assertThat(StreakCalculator.compute(days, LocalDate.of(2026, 1, 1)))
        .isEqualTo(new StreakCalculator.Streaks(2, 2));
    assertThat(StreakCalculator.compute(List.of(), LocalDate.of(2026, 1, 1)))
        .isEqualTo(new StreakCalculator.Streaks(0, 0));
    // Dia no futuro (relógio adiantado) é ignorado.
    assertThat(
            StreakCalculator.compute(List.of(LocalDate.of(2026, 1, 2)), LocalDate.of(2026, 1, 1)))
        .isEqualTo(new StreakCalculator.Streaks(0, 0));
  }
}
