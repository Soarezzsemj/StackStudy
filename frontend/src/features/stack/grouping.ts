import type { Task } from '@/lib/api/stack';

/**
 * Grupos da lista por prazo. "past" só aparece com tarefas feitas de prazo já passado (as
 * pendentes nesse caso são "overdue").
 */
export const DUE_BUCKETS = ['overdue', 'today', 'week', 'later', 'noDue', 'past'] as const;
export type DueBucket = (typeof DUE_BUCKETS)[number];

function startOfDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

function addDays(d: Date, days: number): Date {
  // setDate respeita horário de verão (diferente de somar 24 h).
  const copy = new Date(d);
  copy.setDate(copy.getDate() + days);
  return copy;
}

/**
 * Em qual grupo a tarefa cai, no fuso local. "Esta semana" vai até domingo (semana começa na
 * segunda), sem incluir hoje.
 */
export function dueBucket(task: Pick<Task, 'dueAt' | 'status'>, now: Date): DueBucket {
  if (!task.dueAt) return 'noDue';
  const due = new Date(task.dueAt);
  const today = startOfDay(now);
  const tomorrow = addDays(today, 1);
  // Dias até a próxima segunda: domingo (0) -> 1, segunda (1) -> 7, ...
  const nextMonday = addDays(today, 7 - ((today.getDay() + 6) % 7));

  if (due < now) {
    if (task.status !== 'DONE') return 'overdue';
    return due >= today ? 'today' : 'past';
  }
  if (due < tomorrow) return 'today';
  if (due < nextMonday) return 'week';
  return 'later';
}

export interface TaskGroup {
  bucket: DueBucket;
  tasks: Task[];
}

/** Agrupa mantendo a ordem que veio da API (prazo crescente) e pula grupos vazios. */
export function groupTasksByDue(tasks: Task[], now: Date): TaskGroup[] {
  const map = new Map<DueBucket, Task[]>();
  for (const task of tasks) {
    const bucket = dueBucket(task, now);
    const list = map.get(bucket);
    if (list) list.push(task);
    else map.set(bucket, [task]);
  }
  return DUE_BUCKETS.filter((b) => map.has(b)).map((bucket) => ({
    bucket,
    tasks: map.get(bucket) ?? [],
  }));
}
