import { useId } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Button, Card, Chip, ErrorState, Icon, SelectField } from '@/components/ui';
import { errorMessage } from '@/features/auth/errorMessage';
import { buildTaskQuery, DEFAULT_FILTERS } from '@/features/stack/filters';
import type { ActiveSession, DeepWorkSettings, SessionMode } from '@/lib/api/deepwork';
import { stackApi, type Subject } from '@/lib/api/stack';
import { cn } from '@/lib/cn';
import { formatClock } from '@/lib/time';
import { useNow } from '@/lib/useNow';
import { useDeepWorkStore } from '@/stores/deepWorkStore';
import { displaySeconds } from './clock';
import { breakRemaining, cyclePosition } from './pomodoro';
import { useSessionActions } from './queries';

interface TimerCardProps {
  active: ActiveSession | null;
  settings: DeepWorkSettings;
  /** Matérias do semestre atual (seletor). */
  subjects: Subject[];
  /** Todas as matérias, para mostrar a da sessão ativa mesmo se for de outro semestre. */
  subjectsById: Map<string, Subject>;
  onStart: () => void;
  onComplete: () => void;
}

/** Card principal: modo, timer grande, matéria/tarefa e controles. */
export function TimerCard({
  active,
  settings,
  subjects,
  subjectsById,
  onStart,
  onComplete,
}: TimerCardProps) {
  const { t } = useTranslation();
  const mode = useDeepWorkStore((s) => s.mode);
  const setMode = useDeepWorkStore((s) => s.setMode);
  const subjectId = useDeepWorkStore((s) => s.subjectId);
  const setSubject = useDeepWorkStore((s) => s.setSubject);
  const taskId = useDeepWorkStore((s) => s.taskId);
  const setTask = useDeepWorkStore((s) => s.setTask);
  const completedFocus = useDeepWorkStore((s) => s.completedFocus);
  const breakState = useDeepWorkStore((s) => s.breakState);
  const setBreak = useDeepWorkStore((s) => s.setBreak);
  const now = useNow(!!active || !!breakState);
  const { start, pause, resume, cancel } = useSessionActions();
  const timerId = useId();

  // Tarefas pendentes da matéria escolhida (opcional).
  const tasks = useQuery({
    queryKey: ['tasks', 'forFocus', subjectId],
    queryFn: () =>
      stackApi.tasks(buildTaskQuery({ ...DEFAULT_FILTERS, subject: subjectId }, 0, 50)),
    enabled: !!subjectId && !active,
  });

  const activeSubject = active ? subjectsById.get(active.subjectId) : undefined;
  const paused = !!active?.pausedAt;
  const phase = active ? (paused ? 'paused' : 'focus') : breakState ? breakState.kind : 'idle';

  let seconds: number;
  if (active) seconds = displaySeconds(active, now);
  else if (breakState) seconds = breakRemaining(breakState, now);
  else seconds = mode === 'POMODORO' ? settings.focusMinutes * 60 : 0;

  const every = Math.max(1, settings.longBreakEvery);
  const position = cyclePosition(completedFocus, every);
  const error = start.error ?? pause.error ?? resume.error ?? cancel.error;
  const busy = start.isPending || pause.isPending || resume.isPending || cancel.isPending;

  return (
    <Card variant="hero" className="flex flex-col gap-6 p-6 lg:p-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div
          role="group"
          aria-label={t('deepWork.mode.label')}
          className="flex gap-1 rounded-pill border border-border bg-bg/40 p-1"
        >
          {(['POMODORO', 'FLOW'] as SessionMode[]).map((m) => {
            const current = active ? active.mode === m : mode === m;
            return (
              <button
                key={m}
                type="button"
                aria-pressed={current}
                disabled={!!active}
                onClick={() => setMode(m)}
                className={cn(
                  'label min-h-11 rounded-pill px-4 transition-colors disabled:cursor-not-allowed',
                  current ? 'bg-surface-raised text-text' : 'text-text-muted hover:text-text',
                )}
              >
                {t(`deepWork.mode.${m}`)}
              </button>
            );
          })}
        </div>
        <Chip tone={phase === 'focus' ? 'action' : phase === 'idle' ? 'neutral' : 'space'}>
          {t(`deepWork.phase.${phase}`)}
        </Chip>
      </div>

      <div className="flex flex-col items-center gap-3 py-2 text-center">
        {activeSubject && (
          <p className="flex items-center gap-2 text-text-on-space">
            <span
              aria-hidden="true"
              className="h-2.5 w-2.5 rounded-full"
              style={{ backgroundColor: activeSubject.color }}
            />
            {activeSubject.name}
          </p>
        )}
        <p
          id={timerId}
          className="tabular m-0 text-[clamp(4.5rem,16vw,9rem)] font-[650] leading-none tracking-title"
          role="timer"
          aria-live="off"
          aria-label={t('deepWork.timerLabel', { time: formatClock(seconds) })}
        >
          {formatClock(seconds)}
        </p>
        {(mode === 'POMODORO' || active?.mode === 'POMODORO') && (
          <div
            className="flex items-center gap-2"
            aria-label={t('deepWork.cycle', { done: position, total: every })}
            role="img"
          >
            {Array.from({ length: every }, (_, i) => (
              <span
                key={i}
                aria-hidden="true"
                className={cn(
                  'h-2 w-2 rounded-full',
                  i < position ? 'bg-action' : 'bg-border-control',
                )}
              />
            ))}
          </div>
        )}
      </div>

      {!active && (
        <div className="grid gap-4 sm:grid-cols-2">
          <SelectField
            label={t('deepWork.subject')}
            value={subjectId}
            onChange={(e) => setSubject(e.target.value)}
          >
            <option value="">{t('deepWork.chooseSubject')}</option>
            {subjects.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </SelectField>
          <SelectField
            label={t('deepWork.task')}
            value={taskId}
            disabled={!subjectId}
            onChange={(e) => setTask(e.target.value)}
          >
            <option value="">{t('deepWork.noTask')}</option>
            {(tasks.data?.items ?? []).map((task) => (
              <option key={task.id} value={task.id}>
                {task.title}
              </option>
            ))}
          </SelectField>
        </div>
      )}

      <div className="flex flex-wrap items-center justify-center gap-3">
        {!active && (
          <>
            <Button
              className="min-w-48 px-8"
              disabled={!subjectId || busy}
              onClick={onStart}
              aria-keyshortcuts="Space"
            >
              <Icon name="deepWork" size={18} />
              {t('deepWork.start')}
            </Button>
            {breakState && (
              <Button variant="secondary" onClick={() => setBreak(null)}>
                {t('deepWork.skipBreak')}
              </Button>
            )}
          </>
        )}
        {active && (
          <>
            <Button
              className="min-w-40"
              disabled={busy}
              aria-keyshortcuts="Space"
              onClick={() => (paused ? resume.mutate(active.id) : pause.mutate(active.id))}
            >
              {paused ? t('deepWork.resume') : t('deepWork.pause')}
            </Button>
            <Button variant="secondary" disabled={busy} onClick={onComplete}>
              <Icon name="check" size={18} />
              {t('deepWork.complete')}
            </Button>
            <Button variant="ghost" disabled={busy} onClick={() => cancel.mutate(active.id)}>
              {t('deepWork.cancel')}
            </Button>
          </>
        )}
      </div>

      {subjects.length === 0 && !active && (
        <p className="text-center text-sm text-text-on-space">{t('deepWork.noSubjects')}</p>
      )}
      {error && <ErrorState message={errorMessage(t, error)} />}
    </Card>
  );
}
