export const HEAT_LEVELS = 5;

/** Converte um valor em nível de 0 a 4. Zero fica no 0; valor positivo é pelo menos 1. */
export function heatLevel(value: number, max: number): number {
  if (!Number.isFinite(value) || value <= 0 || !Number.isFinite(max) || max <= 0) return 0;
  return Math.min(HEAT_LEVELS - 1, Math.max(1, Math.ceil((value / max) * (HEAT_LEVELS - 1))));
}

/** Porcentagem de value/max entre 0 e 100 (0 se a entrada for inválida). */
export function clampPercent(value: number, max: number): number {
  if (!Number.isFinite(value) || !Number.isFinite(max) || max <= 0) return 0;
  return Math.min(100, Math.max(0, (value / max) * 100));
}
