import { describe, expect, it } from 'vitest';
import {
  displaySeconds,
  elapsedSeconds,
  isFocusOver,
  remainingSeconds,
  type ClockSession,
} from './clock';
import { formatDuration } from './format';
import { buildHeatmapGrid, focusLevel } from './heatmapGrid';
import { DEFAULT_SETTINGS, breakRemaining, cyclePosition, nextBreak } from './pomodoro';

const T0 = Date.parse('2026-11-20T10:00:00Z');
const iso = (ms: number) => new Date(ms).toISOString();

function session(partial: Partial<ClockSession> = {}): ClockSession {
  return {
    startedAt: iso(T0),
    pausedSeconds: 0,
    mode: 'POMODORO',
    plannedMinutes: 25,
    offsetMs: 0,
    ...partial,
  };
}

describe('clock (from timestamps)', () => {
  it('elapsed discounts closed pauses and freezes while paused', () => {
    expect(elapsedSeconds(session(), T0 + 600_000)).toBe(600);
    expect(elapsedSeconds(session({ pausedSeconds: 120 }), T0 + 600_000)).toBe(480);
    const paused = session({ pausedAt: iso(T0 + 300_000) });
    expect(elapsedSeconds(paused, T0 + 300_000)).toBe(300);
    // Uma hora depois, ainda pausada: o relógio não andou.
    expect(elapsedSeconds(paused, T0 + 3_900_000)).toBe(300);
  });

  it('corrects the local clock with the server offset', () => {
    // Relógio local 2 min atrasado em relação ao servidor.
    const s = session({ offsetMs: 120_000 });
    expect(elapsedSeconds(s, T0 + 60_000)).toBe(180);
  });

  it('pomodoro counts down and knows when it is over; flow counts up', () => {
    expect(remainingSeconds(session(), T0 + 600_000)).toBe(900);
    expect(displaySeconds(session(), T0 + 600_000)).toBe(900);
    expect(isFocusOver(session(), T0 + 1_499_000)).toBe(false);
    expect(isFocusOver(session(), T0 + 1_500_000)).toBe(true);
    // Depois do fim mostra 00:00, não negativo.
    expect(displaySeconds(session(), T0 + 2_000_000)).toBe(0);
    // Pausado não "acaba".
    expect(isFocusOver(session({ pausedAt: iso(T0 + 1_600_000) }), T0 + 2_000_000)).toBe(false);
    const flow = session({ mode: 'FLOW', plannedMinutes: undefined });
    expect(displaySeconds(flow, T0 + 4_000_000)).toBe(4000);
    expect(isFocusOver(flow, T0 + 99_000_000)).toBe(false);
  });
});

describe('pomodoro cycle', () => {
  it('long break every N focus blocks, short otherwise', () => {
    const kinds = [1, 2, 3, 4, 5, 8].map((n) => nextBreak(n, DEFAULT_SETTINGS, T0).kind);
    expect(kinds).toEqual([
      'shortBreak',
      'shortBreak',
      'shortBreak',
      'longBreak',
      'shortBreak',
      'longBreak',
    ]);
    expect(nextBreak(4, DEFAULT_SETTINGS, T0).durationSeconds).toBe(15 * 60);
    expect(nextBreak(1, { ...DEFAULT_SETTINGS, shortBreakMinutes: 7 }, T0).durationSeconds).toBe(
      420,
    );
  });

  it('break remaining comes from its start timestamp', () => {
    const b = nextBreak(1, DEFAULT_SETTINGS, T0);
    expect(breakRemaining(b, T0)).toBe(300);
    expect(breakRemaining(b, T0 + 120_500)).toBe(180);
    expect(breakRemaining(b, T0 + 999_000)).toBe(0);
    expect(cyclePosition(5, 4)).toBe(1);
  });
});

describe('heatmap', () => {
  it('five levels by minutes per day', () => {
    expect([0, 60, 30 * 60, 31 * 60, 60 * 60, 90 * 60, 121 * 60].map(focusLevel)).toEqual([
      0, 1, 1, 2, 2, 3, 4,
    ]);
  });

  it('builds Monday-first weeks and blanks days outside the range', () => {
    // 2026-11-18 é quarta; a primeira coluna começa na segunda 16.
    const grid = buildHeatmapGrid(
      [
        { date: '2026-11-18', seconds: 3600 },
        { date: '2026-11-23', seconds: 600 },
      ],
      '2026-11-18',
      '2026-11-24',
    );
    expect(grid.values).toHaveLength(7);
    expect(grid.values[0]).toHaveLength(2);
    expect(grid.dates[0]![0]).toBeNull(); // seg 16, fora
    expect(grid.dates[2]![0]).toBe('2026-11-18');
    expect(grid.values[2]![0]).toBe(3600);
    expect(grid.dates[0]![1]).toBe('2026-11-23');
    expect(grid.values[0]![1]).toBe(600);
    expect(grid.dates[2]![1]).toBeNull(); // qua 25, depois do fim
  });

  it('a full year has 52 or 53 columns', () => {
    const grid = buildHeatmapGrid([], '2025-11-21', '2026-11-20');
    expect(grid.values[0]!.length).toBeGreaterThanOrEqual(52);
    expect(grid.values[0]!.length).toBeLessThanOrEqual(54);
  });

  it('formats durations', () => {
    expect(formatDuration(45 * 60)).toBe('45 min');
    expect(formatDuration(2 * 3600)).toBe('2 h');
    expect(formatDuration(5400)).toBe('1 h 30 min');
  });
});
