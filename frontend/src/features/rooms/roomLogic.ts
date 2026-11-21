import type {
  MemberPresence,
  PomodoroConfig,
  PomodoroPhase,
  PresenceState,
  RoomSession,
} from '@/lib/api/rooms';

/**
 * Lógica pura das salas. O cronômetro sai SEMPRE dos timestamps do servidor (contado até
 * `serverTime` + o que passou desde então no relógio do servidor), nunca de um contador local:
 * trocar de aba ou dormir o computador não perde nem inventa tempo.
 */

/** O servidor não credita intervalos maiores que isso entre heartbeats (2 min). */
export const MAX_CREDIT_SECONDS = 120;
export const HEARTBEAT_MS = 30_000;
export const PRESENCE_POLL_MS = 30_000;

/** Diferença entre o relógio do servidor e o local (ms). */
export function clockOffset(serverTime: string, receivedAt: number): number {
  const server = Date.parse(serverTime);
  return Number.isFinite(server) ? server - receivedAt : 0;
}

function elapsedSince(serverTime: string, offsetMs: number, localNow: number): number {
  const diff = (localNow + offsetMs - Date.parse(serverTime)) / 1000;
  return Math.min(MAX_CREDIT_SECONDS, Math.max(0, diff));
}

/** Segundos da minha sessão agora: contado + o que passou, se estiver contando. */
export function sessionSeconds(
  session: Pick<RoomSession, 'countedSeconds' | 'running' | 'serverTime'>,
  offsetMs: number,
  localNow: number,
): number {
  const extra = session.running ? elapsedSince(session.serverTime, offsetMs, localNow) : 0;
  return Math.floor(session.countedSeconds + extra);
}

/** Tempo de um membro na presença (mesma regra, só quando está estudando). */
export function memberSeconds(
  member: Pick<MemberPresence, 'sessionSeconds' | 'state'>,
  serverTime: string,
  offsetMs: number,
  localNow: number,
): number {
  const extra = member.state === 'STUDYING' ? elapsedSince(serverTime, offsetMs, localNow) : 0;
  return Math.floor(member.sessionSeconds + extra);
}

export interface LocalPhase {
  phase: PomodoroPhase;
  cycle: number;
  of: number;
  endsAt: number;
  remainingSeconds: number;
}

/**
 * Fase do ciclo compartilhado no instante `nowMs` (espelho do RoomPomodoro do backend): rodada =
 * `every` focos com pausas curtas entre eles e uma longa no fim, a partir de `cycleStartedAt`.
 */
export function phaseAt(config: PomodoroConfig, nowMs: number): LocalPhase {
  const focus = config.focusMinutes * 60;
  const short = config.shortBreakMinutes * 60;
  const long = config.longBreakMinutes * 60;
  const every = config.longBreakEvery;
  const round = every * focus + (every - 1) * short + long;
  const anchor = Math.floor(Date.parse(config.cycleStartedAt) / 1000);
  const t = Math.max(Math.floor(nowMs / 1000), anchor);
  const roundStart = anchor + Math.floor((t - anchor) / round) * round;
  const pos = t - roundStart;
  let cursor = 0;
  for (let k = 1; k <= every; k++) {
    if (pos < cursor + focus) return make('FOCUS', k, roundStart + cursor + focus);
    cursor += focus;
    const pause = k === every ? long : short;
    if (pos < cursor + pause) {
      return make(k === every ? 'LONG_BREAK' : 'SHORT_BREAK', k, roundStart + cursor + pause);
    }
    cursor += pause;
  }
  return make('FOCUS', 1, roundStart + round + focus);

  function make(phase: PomodoroPhase, cycle: number, end: number): LocalPhase {
    return { phase, cycle, of: every, endsAt: end * 1000, remainingSeconds: Math.max(0, end - t) };
  }
}

/** Segundos para responder "Ainda está estudando?" (0 = prazo acabou). */
export function confirmSecondsLeft(deadline: string, offsetMs: number, localNow: number): number {
  return Math.max(0, Math.ceil((Date.parse(deadline) - (localNow + offsetMs)) / 1000));
}

/** Progresso da meta semanal da sala (0–100). Sem meta, null. */
export function goalPercent(weekSeconds: number, goalHours: number | undefined): number | null {
  if (!goalHours) return null;
  return Math.min(100, Math.round((weekSeconds / (goalHours * 3600)) * 100));
}

export type StateTone = 'action' | 'warning' | 'neutral' | 'space';

export function stateTone(state: PresenceState): StateTone {
  switch (state) {
    case 'STUDYING':
      return 'action';
    case 'PAUSED':
    case 'AUTO_PAUSED':
      return 'warning';
    case 'AWAY':
      return 'space';
    default:
      return 'neutral';
  }
}

/** "1 h 05 min" / "45 min" para totais. */
export function formatDuration(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h === 0) return `${m} min`;
  return `${h} h ${String(m).padStart(2, '0')} min`;
}

/** HH:MM no horário local. */
export function formatTime(iso: string, locale: string): string {
  return new Intl.DateTimeFormat(locale, { hour: '2-digit', minute: '2-digit' }).format(
    new Date(iso),
  );
}

/** Código de convite como a pessoa digita (sem espaços, maiúsculo, com hífens a cada 4). */
export function formatCodeInput(value: string): string {
  const clean = value
    .toUpperCase()
    .replace(/[^0-9A-Z]/g, '')
    .slice(0, 12);
  return clean.match(/.{1,4}/g)?.join('-') ?? '';
}

/** Apelido aceito pelo backend: letras, números, espaço, ponto, hífen e sublinhado (2–24). */
export function validNickname(value: string): boolean {
  return /^[\p{L}\p{N} ._-]{2,24}$/u.test(value.trim());
}
