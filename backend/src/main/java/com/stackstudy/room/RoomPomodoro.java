package com.stackstudy.room;

import com.stackstudy.room.RoomEnums.PomodoroPhase;
import java.time.Instant;

/**
 * Ciclo Pomodoro compartilhado da sala, calculado só pelo relógio (puro, sem timer no servidor): a
 * rodada é {@code every} focos com pausas curtas entre eles e uma pausa longa no fim, repetida a
 * partir de {@code anchor}. Todos os membros veem a mesma fase e o mesmo tempo restante.
 */
public record RoomPomodoro(
    int focusMinutes, int shortBreakMinutes, int longBreakMinutes, int every) {

  public static final RoomPomodoro DEFAULT = new RoomPomodoro(25, 5, 15, 4);

  /**
   * @param cycle 1..every (qual foco da rodada; na pausa, o do foco que acabou)
   */
  public record Phase(PomodoroPhase phase, int cycle, int of, Instant startedAt, Instant endsAt) {
    public long remainingSeconds(Instant now) {
      return Math.max(0, endsAt.getEpochSecond() - now.getEpochSecond());
    }
  }

  long focus() {
    return focusMinutes * 60L;
  }

  long shortBreak() {
    return shortBreakMinutes * 60L;
  }

  long longBreak() {
    return longBreakMinutes * 60L;
  }

  /** Duração de uma rodada inteira, em segundos. */
  long round() {
    return every * focus() + (every - 1L) * shortBreak() + longBreak();
  }

  public Phase phaseAt(Instant anchor, Instant now) {
    long a = anchor.getEpochSecond();
    long t = Math.max(now.getEpochSecond(), a);
    long r = round();
    long roundStart = a + Math.floorDiv(t - a, r) * r;
    long pos = t - roundStart;
    long cursor = 0;
    for (int k = 1; k <= every; k++) {
      if (pos < cursor + focus()) {
        return phase(PomodoroPhase.FOCUS, k, roundStart + cursor, roundStart + cursor + focus());
      }
      cursor += focus();
      long pause = k == every ? longBreak() : shortBreak();
      PomodoroPhase kind = k == every ? PomodoroPhase.LONG_BREAK : PomodoroPhase.SHORT_BREAK;
      if (pos < cursor + pause) {
        return phase(kind, k, roundStart + cursor, roundStart + cursor + pause);
      }
      cursor += pause;
    }
    // Inalcançável (pos < r); por segurança, começo da próxima rodada.
    return phase(PomodoroPhase.FOCUS, 1, roundStart + r, roundStart + r + focus());
  }

  private Phase phase(PomodoroPhase kind, int cycle, long start, long end) {
    return new Phase(kind, cycle, every, Instant.ofEpochSecond(start), Instant.ofEpochSecond(end));
  }

  /** Segundos de foco dentro de [from, to) (o que conta no modo Pomodoro). */
  public long focusSecondsBetween(Instant anchor, Instant from, Instant to) {
    long total = 0;
    long t = Math.max(from.getEpochSecond(), anchor.getEpochSecond());
    long end = to.getEpochSecond();
    while (t < end) {
      Phase p = phaseAt(anchor, Instant.ofEpochSecond(t));
      long segEnd = Math.min(end, p.endsAt().getEpochSecond());
      if (p.phase() == PomodoroPhase.FOCUS) {
        total += segEnd - t;
      }
      t = segEnd;
    }
    return total;
  }

  /** Quantos focos terminaram em (from, to] (ciclos concluídos com a pessoa presente). */
  public int focusEndsBetween(Instant anchor, Instant from, Instant to) {
    int count = 0;
    long t = Math.max(from.getEpochSecond(), anchor.getEpochSecond());
    long end = to.getEpochSecond();
    while (t < end) {
      Phase p = phaseAt(anchor, Instant.ofEpochSecond(t));
      long segEnd = p.endsAt().getEpochSecond();
      if (p.phase() == PomodoroPhase.FOCUS && segEnd <= end && segEnd > from.getEpochSecond()) {
        count++;
      }
      t = Math.min(end, segEnd);
    }
    return count;
  }
}
