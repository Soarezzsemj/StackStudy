import type { DeepWorkSettings } from '@/lib/api/deepwork';

export type BreakKind = 'shortBreak' | 'longBreak';

/** Pausa em andamento (só no navegador; não vira sessão no servidor). */
export interface BreakState {
  kind: BreakKind;
  /** Início em ms (relógio local). */
  startedAt: number;
  durationSeconds: number;
}

export const DEFAULT_SETTINGS: DeepWorkSettings = {
  focusMinutes: 25,
  shortBreakMinutes: 5,
  longBreakMinutes: 15,
  longBreakEvery: 4,
  weeklyGoalMinutes: 600,
  soundEnabled: true,
  notificationsEnabled: false,
};

/**
 * Pausa depois de concluir um foco. `completedFocus` já inclui o que acabou de terminar: a cada
 * `longBreakEvery` focos vem a pausa longa.
 */
export function nextBreak(
  completedFocus: number,
  settings: DeepWorkSettings,
  now: number,
): BreakState {
  const every = Math.max(1, settings.longBreakEvery);
  const long = completedFocus > 0 && completedFocus % every === 0;
  return {
    kind: long ? 'longBreak' : 'shortBreak',
    startedAt: now,
    durationSeconds: (long ? settings.longBreakMinutes : settings.shortBreakMinutes) * 60,
  };
}

/** Segundos restantes da pausa (nunca negativo). */
export function breakRemaining(state: BreakState, now: number): number {
  return Math.max(0, Math.ceil(state.durationSeconds - (now - state.startedAt) / 1000));
}

/** Posição no ciclo atual (0..every-1) para os indicadores de progresso. */
export function cyclePosition(completedFocus: number, every: number): number {
  return completedFocus % Math.max(1, every);
}
