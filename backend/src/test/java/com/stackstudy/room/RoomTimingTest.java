package com.stackstudy.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackstudy.room.RoomEnums.PomodoroPhase;
import com.stackstudy.room.RoomEnums.PresenceState;
import com.stackstudy.room.RoomEnums.RoomMode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contagem de tempo das salas (pura): o servidor só credita o que o relógio dele viu. */
class RoomTimingTest {

  static final Instant T0 = Instant.parse("2026-11-21T08:00:00Z");
  static final LiveSession.Timing TIMING = LiveSession.Timing.DEFAULT;

  static LiveSession free() {
    return session(RoomMode.FREE, T0);
  }

  static LiveSession session(RoomMode mode, Instant anchor) {
    return new LiveSession(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        mode,
        T0,
        null,
        TIMING,
        RoomPomodoro.DEFAULT,
        anchor,
        T0);
  }

  /** Heartbeats a cada 30 s de {@code from} até {@code from + length}; devolve o fim. */
  static Instant beat(LiveSession s, Instant from, Duration length) {
    Instant t = from;
    Instant end = from.plus(length);
    while (t.isBefore(end)) {
      t = t.plusSeconds(30);
      s.heartbeat(t);
    }
    return t;
  }

  @Test
  void heartbeatsCreditOnlyWhatTheServerClockSaw() {
    LiveSession s = free();
    Instant t = beat(s, T0, Duration.ofMinutes(10));
    assertThat(s.countedSeconds()).isEqualTo(600);
    assertThat(s.state(t)).isEqualTo(PresenceState.STUDYING);
  }

  @Test
  void twelveHourSessionIsCountedInFullWhenPresenceIsConfirmed() {
    LiveSession s = free();
    Instant t = T0;
    for (int hour = 0; hour < 12; hour++) {
      t = beat(s, t, Duration.ofMinutes(61));
      // Pedido de confirmação aos 60 min (desde a última); a pessoa responde no 1º minuto.
      s.confirm(t);
    }
    // 12 × 61 min, todos contados (o pendente entra ao confirmar).
    assertThat(s.countedSeconds()).isEqualTo(12 * 61 * 60);
    assertThat(s.countedSeconds()).isGreaterThan(Duration.ofHours(12).toSeconds());
    assertThat(s.paused()).isFalse();
  }

  @Test
  void gapsLongerThanTwoMinutesCountNothingAndTheMemberIsAway() {
    LiveSession s = free();
    Instant t = beat(s, T0, Duration.ofMinutes(5));
    assertThat(s.countedSeconds()).isEqualTo(300);
    // Aba fechada 10 min: aparece como ausente e nada é creditado.
    Instant later = t.plus(Duration.ofMinutes(10));
    assertThat(s.state(later)).isEqualTo(PresenceState.AWAY);
    assertThat(s.secondsAt(later)).isEqualTo(300);
    s.heartbeat(later);
    assertThat(s.countedSeconds()).isEqualTo(300);
    // Exatamente 2 min ainda conta.
    s.heartbeat(later.plus(Duration.ofMinutes(2)));
    assertThat(s.countedSeconds()).isEqualTo(420);
  }

  @Test
  void clockGoingBackwardsIsIgnored() {
    LiveSession s = free();
    s.heartbeat(T0.plusSeconds(60));
    s.heartbeat(T0.plusSeconds(10));
    s.heartbeat(T0.plusSeconds(40));
    assertThat(s.countedSeconds()).isEqualTo(90);
  }

  @Test
  void pausedTimeDoesNotCount() {
    LiveSession s = free();
    Instant t = beat(s, T0, Duration.ofMinutes(20));
    s.pause(t);
    t = beat(s, t, Duration.ofMinutes(30));
    assertThat(s.countedSeconds()).isEqualTo(1200);
    assertThat(s.state(t)).isEqualTo(PresenceState.PAUSED);
    s.resume(t);
    t = beat(s, t, Duration.ofMinutes(5));
    assertThat(s.countedSeconds()).isEqualTo(1500);
  }

  @Test
  void withoutConfirmationItAutoPausesAndIdleTimeDoesNotCount() {
    LiveSession s = free();
    // 60 min até o pedido, + 5 min de prazo sem resposta.
    Instant t = beat(s, T0, Duration.ofMinutes(59));
    assertThat(s.pendingConfirmDeadline(t)).isNull();
    t = beat(s, t, Duration.ofMinutes(2));
    assertThat(s.pendingConfirmDeadline(t)).isEqualTo(T0.plus(Duration.ofMinutes(65)));
    t = beat(s, t, Duration.ofMinutes(5));
    assertThat(s.autoPaused()).isTrue();
    assertThat(s.state(t)).isEqualTo(PresenceState.AUTO_PAUSED);
    // Só os 60 min até o pedido: os 5 sem resposta não contam.
    assertThat(s.countedSeconds()).isEqualTo(3600);
    t = beat(s, t, Duration.ofMinutes(30));
    assertThat(s.countedSeconds()).isEqualTo(3600);

    // Volta: confirma e o tempo recomeça daqui (os 30 min ociosos ficam de fora).
    s.confirm(t);
    assertThat(s.paused()).isFalse();
    beat(s, t, Duration.ofMinutes(10));
    assertThat(s.countedSeconds()).isEqualTo(3600 + 600);
  }

  @Test
  void confirmingWithinTheGraceKeepsThePendingTime() {
    LiveSession s = free();
    Instant t = beat(s, T0, Duration.ofMinutes(63));
    assertThat(s.countedSeconds()).isEqualTo(3600);
    assertThat(s.secondsAt(t)).isEqualTo(63 * 60);
    s.confirm(t);
    assertThat(s.countedSeconds()).isEqualTo(63 * 60);
    assertThat(s.pendingConfirmDeadline(t)).isNull();
  }

  @Test
  void timeoutAndPeriodicSaveDeadlines() {
    LiveSession s = free();
    Instant t = beat(s, T0, Duration.ofMinutes(29));
    assertThat(s.dueForSave(t)).isFalse();
    t = beat(s, t, Duration.ofMinutes(1));
    assertThat(s.dueForSave(t)).isTrue();
    s.markSaved(t);
    assertThat(s.dueForSave(t)).isFalse();
    assertThat(s.timedOut(t.plus(Duration.ofMinutes(15)))).isFalse();
    assertThat(s.timedOut(t.plus(Duration.ofMinutes(16)))).isTrue();
  }

  @Test
  void restoreContinuesFromTheSavedValueWithoutCreditingTheGap() {
    LiveSession s =
        LiveSession.restore(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            RoomMode.FREE,
            T0,
            "Anatomia",
            TIMING,
            RoomPomodoro.DEFAULT,
            T0,
            1800,
            0,
            false,
            T0.plus(Duration.ofMinutes(40)));
    beat(s, T0.plus(Duration.ofMinutes(40)), Duration.ofMinutes(1));
    assertThat(s.countedSeconds()).isEqualTo(1860);
    assertThat(s.subjectName()).isEqualTo("Anatomia");
  }

  @Test
  void pomodoroCountsOnlyFocusPhasesAndCompletedCycles() {
    LiveSession s = session(RoomMode.POMODORO, T0);
    // Uma rodada inteira: 4 × 25 de foco, 3 × 5 de pausa curta, 15 de longa = 130 min.
    Instant t = beat(s, T0, Duration.ofMinutes(130));
    assertThat(s.countedSeconds()).isEqualTo(100 * 60);
    assertThat(s.cycles()).isEqualTo(4);
    // Pomodoro não pede confirmação de presença.
    assertThat(s.pendingConfirmDeadline(t)).isNull();
    assertThat(s.paused()).isFalse();
  }

  @Test
  void sharedPomodoroPhaseComesFromTheClock() {
    RoomPomodoro p = RoomPomodoro.DEFAULT;
    RoomPomodoro.Phase focus = p.phaseAt(T0, T0.plus(Duration.ofMinutes(10)));
    assertThat(focus.phase()).isEqualTo(PomodoroPhase.FOCUS);
    assertThat(focus.cycle()).isEqualTo(1);
    assertThat(focus.of()).isEqualTo(4);
    assertThat(focus.remainingSeconds(T0.plus(Duration.ofMinutes(10)))).isEqualTo(15 * 60);

    RoomPomodoro.Phase shortBreak = p.phaseAt(T0, T0.plus(Duration.ofMinutes(27)));
    assertThat(shortBreak.phase()).isEqualTo(PomodoroPhase.SHORT_BREAK);
    assertThat(shortBreak.endsAt()).isEqualTo(T0.plus(Duration.ofMinutes(30)));

    RoomPomodoro.Phase second = p.phaseAt(T0, T0.plus(Duration.ofMinutes(31)));
    assertThat(second.phase()).isEqualTo(PomodoroPhase.FOCUS);
    assertThat(second.cycle()).isEqualTo(2);

    RoomPomodoro.Phase longBreak = p.phaseAt(T0, T0.plus(Duration.ofMinutes(120)));
    assertThat(longBreak.phase()).isEqualTo(PomodoroPhase.LONG_BREAK);
    assertThat(longBreak.cycle()).isEqualTo(4);

    // Repete a cada 130 min.
    RoomPomodoro.Phase next = p.phaseAt(T0, T0.plus(Duration.ofMinutes(131)));
    assertThat(next.phase()).isEqualTo(PomodoroPhase.FOCUS);
    assertThat(next.cycle()).isEqualTo(1);
    // Antes do começo do ciclo: o primeiro foco.
    assertThat(p.phaseAt(T0, T0.minusSeconds(60)).cycle()).isEqualTo(1);

    assertThat(
            p.focusSecondsBetween(
                T0, T0.plus(Duration.ofMinutes(20)), T0.plus(Duration.ofMinutes(35))))
        .isEqualTo(10 * 60);
    assertThat(p.focusEndsBetween(T0, T0, T0.plus(Duration.ofMinutes(25)))).isEqualTo(1);
    assertThat(p.focusEndsBetween(T0, T0, T0.plus(Duration.ofMinutes(24)))).isZero();
  }

  @Test
  void inviteCodesAreRandomFormattedAndNormalized() {
    Set<String> codes = new HashSet<>();
    for (int i = 0; i < 500; i++) {
      String c = InviteCodes.generate();
      assertThat(c).hasSize(12).matches("^[0-9A-HJKMNP-TV-Z]{12}$");
      codes.add(c);
    }
    assertThat(codes).hasSize(500);
    String code = "AB12CD34EF56";
    assertThat(InviteCodes.format(code)).isEqualTo("AB12-CD34-EF56");
    assertThat(InviteCodes.normalize(" ab12-cd34-ef56 ")).isEqualTo(code);
    // O → 0 e I/L → 1 (regra do Crockford).
    assertThat(InviteCodes.normalize("ABI2CD34EF5O")).isEqualTo("AB12CD34EF50");
    assertThat(InviteCodes.normalize("short")).isNull();
    assertThat(InviteCodes.normalize("AB12CD34EF5U")).isNull();
    assertThat(InviteCodes.normalize(null)).isNull();
  }
}
