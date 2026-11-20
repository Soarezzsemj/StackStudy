import type { DayTotal } from '@/lib/api/deepwork';

/**
 * Níveis do heatmap por minutos de foco no dia (5 níveis, tokens --heat-0..4):
 * 0 = nada, 1 = até 30 min, 2 = até 1 h, 3 = até 2 h, 4 = mais de 2 h.
 */
export function focusLevel(seconds: number): number {
  if (!Number.isFinite(seconds) || seconds <= 0) return 0;
  const minutes = seconds / 60;
  if (minutes <= 30) return 1;
  if (minutes <= 60) return 2;
  if (minutes <= 120) return 3;
  return 4;
}

export interface HeatmapGrid {
  /** values[linha][coluna]: 7 linhas (segunda a domingo) × semanas. */
  values: number[][];
  /** Data ISO de cada célula, ou null nas células fora do período. */
  dates: (string | null)[][];
}

const pad = (n: number) => String(n).padStart(2, '0');
const iso = (d: Date) => `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`;

/**
 * Monta a grade estilo GitHub de `from` a `to` (datas ISO, inclusive). Datas tratadas em UTC só
 * como calendário (sem fuso): o backend já agrupou no fuso do usuário.
 */
export function buildHeatmapGrid(days: DayTotal[], from: string, to: string): HeatmapGrid {
  const totals = new Map(days.map((d) => [d.date, d.seconds]));
  const start = new Date(`${from}T00:00:00Z`);
  const end = new Date(`${to}T00:00:00Z`);
  // Recua até a segunda-feira da primeira semana.
  const offset = (start.getUTCDay() + 6) % 7;
  const cursor = new Date(start);
  cursor.setUTCDate(cursor.getUTCDate() - offset);

  const values: number[][] = Array.from({ length: 7 }, () => []);
  const dates: (string | null)[][] = Array.from({ length: 7 }, () => []);
  while (cursor <= end) {
    for (let row = 0; row < 7; row++) {
      const inRange = cursor >= start && cursor <= end;
      const key = iso(cursor);
      values[row]!.push(inRange ? (totals.get(key) ?? 0) : 0);
      dates[row]!.push(inRange ? key : null);
      cursor.setUTCDate(cursor.getUTCDate() + 1);
    }
  }
  return { values, dates };
}
