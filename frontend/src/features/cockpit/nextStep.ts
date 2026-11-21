import type { CockpitData, CockpitTask } from '@/lib/api/cockpit';

/*
 * Lógica pura do Cockpit (sem React): o "próximo passo" sugerido, a saudação e o checklist do
 * primeiro acesso. Tudo a partir do payload de /api/cockpit (nada de conta de notas aqui).
 */

export type NextStep =
  | { kind: 'activeSession' }
  | { kind: 'setup'; step: 'semester' | 'subject' }
  | { kind: 'overdue'; task: CockpitTask; count: number }
  | { kind: 'exam'; task: CockpitTask; days: number }
  | { kind: 'review'; cards: number }
  | { kind: 'dueSoon'; task: CockpitTask }
  | { kind: 'focus'; remainingMinutes: number }
  | { kind: 'allDone' };

/** Prova "chegando": até 3 dias. */
export const EXAM_SOON_DAYS = 3;
const DAY_MS = 24 * 60 * 60 * 1000;

/** Dias inteiros (para cima) até o prazo; 0 = hoje ou já passou. */
export function daysUntil(iso: string, now: Date): number {
  return Math.max(0, Math.ceil((new Date(iso).getTime() - now.getTime()) / DAY_MS));
}

/**
 * Uma sugestão só, na ordem do que mais pesa: sessão em andamento, configurar o básico, atrasada,
 * prova em até 3 dias, cartas de hoje, prazo nas próximas 24 h, meta de foco do dia.
 */
export function nextStep(data: CockpitData, opts: { activeSession: boolean; now: Date }): NextStep {
  if (opts.activeSession) return { kind: 'activeSession' };
  const setup = firstPendingSetup(data);
  if (setup === 'semester' || setup === 'subject') return { kind: 'setup', step: setup };

  const overdue = data.tasks.overdue[0];
  if (overdue) return { kind: 'overdue', task: overdue, count: data.tasks.overdueCount };

  const exam = data.nextExam;
  if (exam) {
    const days = daysUntil(exam.dueAt, opts.now);
    if (days <= EXAM_SOON_DAYS) return { kind: 'exam', task: exam, days };
  }
  if (data.flashcards.dueToday > 0) return { kind: 'review', cards: data.flashcards.dueToday };

  const soon = data.tasks.upcoming.find(
    (t) => new Date(t.dueAt).getTime() - opts.now.getTime() <= DAY_MS,
  );
  if (soon) return { kind: 'dueSoon', task: soon };

  const remaining = data.focus.dailyGoalSeconds - data.focus.todaySeconds;
  if (remaining > 0) return { kind: 'focus', remainingMinutes: Math.ceil(remaining / 60) };
  return { kind: 'allDone' };
}

/** Saudação pela hora local. */
export function greetingKey(now: Date): 'morning' | 'afternoon' | 'evening' {
  const h = now.getHours();
  if (h >= 5 && h < 12) return 'morning';
  if (h >= 12 && h < 18) return 'afternoon';
  return 'evening';
}

/** Primeiro nome para a saudação ("Ada Lovelace" → "Ada"). */
export function firstName(name: string | undefined): string {
  return (name ?? '').trim().split(/\s+/)[0] ?? '';
}

export type OnboardingStepKey = 'semester' | 'subject' | 'task' | 'focus';

export interface OnboardingStep {
  key: OnboardingStepKey;
  done: boolean;
}

/** Checklist do primeiro acesso, na ordem em que faz sentido fazer. */
export function onboardingSteps(data: CockpitData): OnboardingStep[] {
  const o = data.onboarding;
  return [
    { key: 'semester', done: o.hasSemester },
    { key: 'subject', done: o.hasSubject },
    { key: 'task', done: o.hasTask },
    { key: 'focus', done: o.hasFocusSession },
  ];
}

export function firstPendingSetup(data: CockpitData): OnboardingStepKey | undefined {
  return onboardingSteps(data).find((s) => !s.done)?.key;
}

/** Sem semestre ou sem matéria, os cartões zerados não ajudam: mostra só o checklist. */
export function isBrandNew(data: CockpitData): boolean {
  return !data.onboarding.hasSemester || !data.onboarding.hasSubject;
}

/** Progresso 0–100 (meta zero = sem barra). */
export function goalPct(value: number, goal: number): number {
  if (goal <= 0) return 0;
  return Math.min(100, Math.round((value / goal) * 100));
}
