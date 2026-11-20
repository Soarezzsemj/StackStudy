// Relógio do Deep Work: tudo calculado a partir de timestamps do servidor. O setInterval da tela só
// força um novo render; recarregar a página ou deixar a aba em segundo plano não perde tempo.

export interface ClockSession {
  startedAt: string;
  pausedAt?: string;
  pausedSeconds: number;
  plannedMinutes?: number;
  mode: 'POMODORO' | 'FLOW';
  /** Relógio do servidor menos o local (ms). */
  offsetMs: number;
}

/** "Agora" no relógio do servidor. */
export function serverNow(session: Pick<ClockSession, 'offsetMs'>, localNowMs: number): number {
  return localNowMs + session.offsetMs;
}

/** Segundos efetivos (sem pausas). Pausada, para no início da pausa. */
export function elapsedSeconds(session: ClockSession, localNowMs: number): number {
  const reference = session.pausedAt
    ? Date.parse(session.pausedAt)
    : serverNow(session, localNowMs);
  const raw = (reference - Date.parse(session.startedAt)) / 1000 - session.pausedSeconds;
  return Math.max(0, Math.floor(raw));
}

/** Pomodoro: segundos que faltam (pode ficar negativo quando o tempo acabou). */
export function remainingSeconds(session: ClockSession, localNowMs: number): number {
  return (session.plannedMinutes ?? 0) * 60 - elapsedSeconds(session, localNowMs);
}

/** O que o timer mostra: contagem regressiva no Pomodoro, progressiva no Flow. */
export function displaySeconds(session: ClockSession, localNowMs: number): number {
  return session.mode === 'POMODORO' && session.plannedMinutes
    ? Math.max(0, remainingSeconds(session, localNowMs))
    : elapsedSeconds(session, localNowMs);
}

/** O Pomodoro chegou ao fim (e não está pausado)? */
export function isFocusOver(session: ClockSession, localNowMs: number): boolean {
  return (
    session.mode === 'POMODORO' &&
    !!session.plannedMinutes &&
    !session.pausedAt &&
    remainingSeconds(session, localNowMs) <= 0
  );
}
