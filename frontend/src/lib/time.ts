import type { ActiveSession } from '@/stores/sessionStore';

/** Formata segundos como mm:ss, ou h:mm:ss acima de uma hora. */
export function formatClock(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  const mm = String(m).padStart(2, '0');
  const ss = String(sec).padStart(2, '0');
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`;
}

/** Segundos restantes da sessão planejada, ou segundos passados no modo Flow. */
export function sessionClockSeconds(session: ActiveSession, now: number): number {
  const elapsed = (now - new Date(session.startedAt).getTime()) / 1000;
  return session.plannedMinutes ? session.plannedMinutes * 60 - elapsed : elapsed;
}
