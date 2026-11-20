import { describe, expect, it } from 'vitest';
import type { Task } from '@/lib/api/stack';
import { formatDue, fromDue, toDue } from './due';
import { DEFAULT_FILTERS, buildTaskQuery, hasActiveFilters } from './filters';
import { dueBucket, groupTasksByDue } from './grouping';
import { resolveShortcut } from './shortcuts';

// Datas locais (o jsdom usa o fuso da máquina; os cálculos são todos no fuso local).
const local = (y: number, m: number, d: number, h = 0, min = 0, s = 0) =>
  new Date(y, m - 1, d, h, min, s);

function task(partial: Partial<Task>): Task {
  return {
    id: partial.id ?? Math.random().toString(36).slice(2),
    title: partial.title ?? 't',
    type: 'OTHER',
    priority: 'MEDIUM',
    status: 'TODO',
    dueAllDay: false,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...partial,
  };
}

describe('dueBucket', () => {
  // Quarta-feira, 18/11/2026, 10:00.
  const now = local(2026, 11, 18, 10);

  it('classifies by local day and ISO week (ends on Sunday)', () => {
    const at = (d: Date) => task({ dueAt: d.toISOString() });
    expect(dueBucket(task({}), now)).toBe('noDue');
    expect(dueBucket(at(local(2026, 11, 18, 9)), now)).toBe('overdue');
    expect(dueBucket(at(local(2026, 11, 18, 23, 59, 59)), now)).toBe('today');
    expect(dueBucket(at(local(2026, 11, 19, 0, 0)), now)).toBe('week');
    expect(dueBucket(at(local(2026, 11, 22, 23, 0)), now)).toBe('week');
    expect(dueBucket(at(local(2026, 11, 23, 0, 0)), now)).toBe('later');
  });

  it('done tasks are never overdue', () => {
    const earlierToday = local(2026, 11, 18, 8);
    const lastWeek = local(2026, 11, 10, 8);
    expect(dueBucket(task({ dueAt: earlierToday.toISOString(), status: 'DONE' }), now)).toBe(
      'today',
    );
    expect(dueBucket(task({ dueAt: lastWeek.toISOString(), status: 'DONE' }), now)).toBe('past');
    expect(dueBucket(task({ dueAt: lastWeek.toISOString(), status: 'DOING' }), now)).toBe(
      'overdue',
    );
  });

  it('on Sunday, "this week" is empty and Monday is later', () => {
    const sunday = local(2026, 11, 22, 12);
    expect(dueBucket(task({ dueAt: local(2026, 11, 23, 9).toISOString() }), sunday)).toBe('later');
  });

  it('groups in fixed order, skipping empty groups and keeping API order', () => {
    const groups = groupTasksByDue(
      [
        task({ id: 'a', dueAt: local(2026, 11, 17).toISOString() }),
        task({ id: 'b', dueAt: local(2026, 11, 30).toISOString() }),
        task({ id: 'c' }),
        task({ id: 'd', dueAt: local(2026, 11, 16).toISOString() }),
      ],
      now,
    );
    expect(groups.map((g) => g.bucket)).toEqual(['overdue', 'later', 'noDue']);
    expect(groups[0]!.tasks.map((t) => t.id)).toEqual(['a', 'd']);
  });
});

describe('due date conversion', () => {
  it('date only means end of the local day, flagged as all-day', () => {
    const due = toDue('2026-12-01', '');
    expect(due.dueAllDay).toBe(true);
    expect(new Date(due.dueAt!).getTime()).toBe(local(2026, 12, 1, 23, 59, 59).getTime());
    expect(fromDue(due.dueAt!, true)).toEqual({ date: '2026-12-01', time: '' });
  });

  it('date and time round-trip in local time', () => {
    const due = toDue('2026-12-01', '14:30');
    expect(due.dueAllDay).toBe(false);
    expect(fromDue(due.dueAt!, false)).toEqual({ date: '2026-12-01', time: '14:30' });
  });

  it('empty or invalid date clears the due date', () => {
    expect(toDue('', '10:00')).toEqual({ dueAt: null, dueAllDay: false });
    expect(toDue('01/12/2026', '')).toEqual({ dueAt: null, dueAllDay: false });
    expect(fromDue(undefined, false)).toEqual({ date: '', time: '' });
  });

  it('formats with time only when not all-day', () => {
    const iso = local(2026, 12, 1, 14, 30).toISOString();
    expect(formatDue(iso, false, 'en')).toContain('·');
    expect(formatDue(iso, true, 'en')).not.toContain('·');
  });
});

describe('buildTaskQuery', () => {
  const parse = (q: string) => Object.fromEntries(new URLSearchParams(q));

  it('default filters ask for pending tasks, first page', () => {
    expect(parse(buildTaskQuery(DEFAULT_FILTERS))).toEqual({
      status: 'TODO,DOING',
      page: '0',
      size: '100',
    });
    expect(hasActiveFilters(DEFAULT_FILTERS)).toBe(false);
  });

  it('maps every filter', () => {
    const f = { subject: 'abc', type: 'EXAM', status: 'DONE', q: '  prova  ' } as const;
    expect(parse(buildTaskQuery(f, 2, 50))).toEqual({
      subjectId: 'abc',
      type: 'EXAM',
      status: 'DONE',
      q: 'prova',
      page: '2',
      size: '50',
    });
    expect(hasActiveFilters(f)).toBe(true);
  });

  it('"none" subject and "all" status', () => {
    const q = parse(buildTaskQuery({ ...DEFAULT_FILTERS, subject: 'none', status: 'all' }));
    expect(q.withoutSubject).toBe('true');
    expect(q.status).toBeUndefined();
    expect(q.subjectId).toBeUndefined();
  });
});

describe('resolveShortcut', () => {
  const key = (k: string, target: EventTarget | null = document.body, mods = {}) =>
    resolveShortcut({ key: k, ctrlKey: false, metaKey: false, altKey: false, target, ...mods });

  it('maps keys on the page body', () => {
    expect(key('n')).toBe('newTask');
    expect(key('E')).toBe('editTask');
    expect(key(' ')).toBe('toggleDone');
    expect(key('ArrowDown')).toBe('next');
    expect(key('ArrowUp')).toBe('prev');
    expect(key('x')).toBeNull();
  });

  it('ignores typing in fields and modifier combos', () => {
    const input = document.createElement('input');
    const textarea = document.createElement('textarea');
    const select = document.createElement('select');
    expect(key('n', input)).toBeNull();
    expect(key(' ', textarea)).toBeNull();
    expect(key('e', select)).toBeNull();
    expect(key('n', document.body, { ctrlKey: true })).toBeNull();
    expect(key('k', document.body, { metaKey: true })).toBeNull();
  });

  it('Space only toggles on the task row, not on other buttons', () => {
    const button = document.createElement('button');
    const row = document.createElement('button');
    row.setAttribute('data-task-row', '');
    expect(key(' ', button)).toBeNull();
    expect(key(' ', row)).toBe('toggleDone');
    // Letras funcionam mesmo com foco num botão.
    expect(key('n', button)).toBe('newTask');
  });
});
