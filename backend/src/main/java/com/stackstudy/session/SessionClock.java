package com.stackstudy.session;

import com.stackstudy.session.SessionEnums.Mode;
import java.time.Duration;
import java.time.Instant;

/**
 * Cálculo puro do tempo efetivo (sem pausas) de uma sessão. Tudo sai de timestamps: nada depende de
 * um contador rodando no servidor ou no navegador.
 */
public final class SessionClock {

  /** Limite de uma sessão (ex.: Flow esquecido aberto de um dia para o outro). */
  public static final long MAX_SESSION_SECONDS = Duration.ofHours(12).toSeconds();

  private SessionClock() {}

  /**
   * Segundos efetivos até {@code now}. Pausada, o relógio para no início da pausa.
   *
   * @param pausedSeconds pausas já encerradas
   * @param pausedAt início da pausa em andamento, ou {@code null}
   */
  public static long elapsedSeconds(
      Instant startedAt, long pausedSeconds, Instant pausedAt, Instant now) {
    Instant reference = pausedAt != null ? pausedAt : now;
    long raw = Duration.between(startedAt, reference).toSeconds() - pausedSeconds;
    return Math.max(0, raw);
  }

  /**
   * Tempo efetivo final ao concluir: no Pomodoro não passa do planejado (o app pode ter ficado
   * fechado depois do fim); em qualquer modo não passa de {@link #MAX_SESSION_SECONDS}.
   */
  public static long effectiveSeconds(
      Instant startedAt,
      long pausedSeconds,
      Instant pausedAt,
      Instant now,
      Mode mode,
      Integer plannedMinutes) {
    long elapsed = elapsedSeconds(startedAt, pausedSeconds, pausedAt, now);
    long cap = MAX_SESSION_SECONDS;
    if (mode == Mode.POMODORO && plannedMinutes != null) {
      cap = Math.min(cap, plannedMinutes * 60L);
    }
    return Math.min(elapsed, cap);
  }

  /** Segundos de uma pausa que termina agora (para somar em {@code pausedSeconds}). */
  public static long pauseSeconds(Instant pausedAt, Instant now) {
    return Math.max(0, Duration.between(pausedAt, now).toSeconds());
  }
}
