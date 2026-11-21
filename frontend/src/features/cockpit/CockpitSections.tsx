import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import {
  Button,
  buttonClass,
  Card,
  Chip,
  EmptyState,
  Heatmap,
  Icon,
  ProgressBar,
  StatBlock,
} from '@/components/ui';
import { formatDay, formatDuration, formatHours } from '@/features/deepwork/format';
import { buildHeatmapGrid, focusLevel } from '@/features/deepwork/heatmapGrid';
import { standingIcon, standingTone, toneColor } from '@/features/grades/format';
import { gradeHeadline, headlineSubjectOf } from '@/features/grades/summary';
import { formatDue } from '@/features/stack/due';
import type { CockpitData, CockpitTask } from '@/lib/api/cockpit';
import { cn } from '@/lib/cn';
import { daysUntil, goalPct, onboardingSteps, type OnboardingStepKey } from './nextStep';

// ---------------------------------------------------------------------------
// resumo
// ---------------------------------------------------------------------------

/** Foco de hoje, semana, sequência e cartas do dia. */
export function SummaryCards({ data }: { data: CockpitData }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const { focus, flashcards } = data;
  const todayMin = Math.floor(focus.todaySeconds / 60);
  const goalMin = Math.round(focus.dailyGoalSeconds / 60);
  return (
    <section
      aria-label={t('cockpit.summary.label')}
      className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4"
    >
      <Card as="div" className="p-5">
        <dl className="m-0 flex flex-col gap-3">
          <StatBlock
            label={t('cockpit.summary.focusToday')}
            value={todayMin}
            unit={t('cockpit.summary.minutes')}
            hint={
              goalMin > 0
                ? t('cockpit.summary.ofGoal', { goal: goalMin })
                : t('cockpit.summary.noGoal')
            }
          />
        </dl>
        {goalMin > 0 && (
          <ProgressBar
            className="mt-3"
            value={goalPct(focus.todaySeconds, focus.dailyGoalSeconds)}
            label={t('cockpit.summary.focusTodayProgress')}
          />
        )}
      </Card>
      <Card as="div" className="p-5">
        <dl className="m-0 flex flex-col gap-3">
          <StatBlock
            label={t('cockpit.summary.week')}
            value={formatHours(focus.weekSeconds, lang)}
            hint={
              focus.weeklyGoalSeconds > 0
                ? t('cockpit.summary.weekGoal', {
                    goal: formatHours(focus.weeklyGoalSeconds, lang),
                  })
                : undefined
            }
          />
        </dl>
        {focus.weeklyGoalSeconds > 0 && (
          <ProgressBar
            className="mt-3"
            value={goalPct(focus.weekSeconds, focus.weeklyGoalSeconds)}
            label={t('cockpit.summary.weekProgress')}
          />
        )}
      </Card>
      <Card as="div" className="p-5">
        <dl className="m-0">
          <StatBlock
            label={t('cockpit.summary.streak')}
            value={focus.currentStreak}
            unit={t('cockpit.summary.days', { count: focus.currentStreak })}
            hint={t('cockpit.summary.longest', { count: focus.longestStreak })}
          />
        </dl>
      </Card>
      <Card as="div" className="p-5">
        <dl className="m-0">
          <StatBlock
            label={t('cockpit.summary.cards')}
            value={flashcards.dueToday}
            hint={
              flashcards.totalCards === 0
                ? t('cockpit.summary.noCards')
                : t('cockpit.summary.cardsSplit', {
                    reviews: flashcards.reviewsToday,
                    news: flashcards.newToday,
                  })
            }
          />
        </dl>
      </Card>
    </section>
  );
}

// ---------------------------------------------------------------------------
// prazos
// ---------------------------------------------------------------------------

export function DeadlinesCard({ data, onNewTask }: { data: CockpitData; onNewTask: () => void }) {
  const { t } = useTranslation();
  const { overdue, upcoming, overdueCount, upcomingCount } = data.tasks;
  const empty = overdue.length === 0 && upcoming.length === 0;
  return (
    <Card
      title={t('cockpit.deadlines.title')}
      aria-label={t('cockpit.deadlines.title')}
      actions={
        <Link to="/stack" className={buttonClass('ghost')}>
          {t('cockpit.deadlines.all')}
        </Link>
      }
    >
      {empty ? (
        <EmptyState
          title={t('cockpit.deadlines.emptyTitle')}
          body={t('cockpit.deadlines.emptyBody')}
          action={
            <Button variant="secondary" onClick={onNewTask}>
              <Icon name="plus" size={18} />
              {t('cockpit.actions.newTask')}
            </Button>
          }
        />
      ) : (
        <div className="flex flex-col gap-5">
          {overdue.length > 0 && (
            <TaskGroup
              title={t('cockpit.deadlines.overdue', { count: overdueCount })}
              tasks={overdue}
              more={overdueCount - overdue.length}
              danger
            />
          )}
          {upcoming.length > 0 && (
            <TaskGroup
              title={t('cockpit.deadlines.upcoming', { count: upcomingCount })}
              tasks={upcoming}
              more={upcomingCount - upcoming.length}
            />
          )}
        </div>
      )}
    </Card>
  );
}

function TaskGroup({
  title,
  tasks,
  more,
  danger,
}: {
  title: string;
  tasks: CockpitTask[];
  more: number;
  danger?: boolean;
}) {
  const { t, i18n } = useTranslation();
  return (
    <section aria-label={title}>
      <h3
        className={cn(
          'label mb-2 flex items-center gap-2',
          danger ? 'text-danger' : 'text-text-muted',
        )}
      >
        {danger && <Icon name="alert" size={14} />}
        {title}
      </h3>
      <ul className="m-0 flex list-none flex-col gap-1 p-0">
        {tasks.map((task) => (
          <li
            key={task.id}
            className="flex items-center gap-3 rounded-inner px-3 py-2 hover:bg-surface-raised"
          >
            <span
              aria-hidden="true"
              className="h-2 w-2 shrink-0 rounded-full"
              style={{ backgroundColor: task.subjectColor ?? 'var(--border-control)' }}
            />
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-semibold">{task.title}</span>
              <span className="block truncate text-xs text-text-muted">
                {task.subjectName ?? t('cockpit.deadlines.noSubject')} ·{' '}
                {t(`stack.tasks.type.${task.type}`)}
              </span>
            </span>
            <span
              className={cn(
                'tabular shrink-0 text-xs',
                danger ? 'text-danger' : 'text-text-secondary',
              )}
            >
              {formatDue(task.dueAt, task.dueAllDay, i18n.language)}
            </span>
          </li>
        ))}
      </ul>
      {more > 0 && (
        <p className="mt-2 px-3 text-xs text-text-muted">
          {t('cockpit.deadlines.more', { count: more })}
        </p>
      )}
    </section>
  );
}

// ---------------------------------------------------------------------------
// radar de risco e próxima prova
// ---------------------------------------------------------------------------

export function RiskCard({ data }: { data: CockpitData }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  return (
    <Card
      title={t('cockpit.risk.title')}
      aria-label={t('cockpit.risk.title')}
      actions={
        <Link to="/grades" className={buttonClass('ghost')}>
          {t('cockpit.risk.open')}
        </Link>
      }
    >
      {data.atRisk.length === 0 ? (
        <p className="flex items-center gap-2 text-sm text-text-secondary">
          <Icon name="check" size={18} className="text-action" />
          {t('cockpit.risk.none')}
        </p>
      ) : (
        <ul className="m-0 flex list-none flex-col gap-3 p-0">
          {data.atRisk.map(({ subject, gradeRisk, attendanceRisk }) => {
            // Mesma mensagem do Grade Predictor (summary.ts): a conta nunca é refeita aqui.
            const headline = gradeRisk
              ? gradeHeadline(headlineSubjectOf(subject), subject.result, lang)
              : null;
            const tone = gradeRisk ? standingTone(subject.result.standing) : 'warning';
            const over = subject.attendance.status === 'OVER';
            return (
              <li key={subject.subjectId} className="card-inner flex flex-col gap-1 px-4 py-3">
                <p className="flex items-center gap-2 font-semibold">
                  <span
                    aria-hidden="true"
                    className="h-2.5 w-2.5 shrink-0 rounded-full"
                    style={{ backgroundColor: subject.color }}
                  />
                  {subject.name}
                </p>
                {headline && (
                  <p className="flex items-start gap-2 text-sm" style={{ color: toneColor(tone) }}>
                    <Icon
                      name={standingIcon(subject.result.standing)}
                      size={16}
                      className="mt-0.5 shrink-0"
                    />
                    {t(`grades.headline.${headline.key}`, headline.values)}
                  </p>
                )}
                {attendanceRisk && (
                  <p
                    className="flex items-start gap-2 text-sm"
                    style={{ color: toneColor(over ? 'danger' : 'warning') }}
                  >
                    <Icon name="alert" size={16} className="mt-0.5 shrink-0" />
                    {t(over ? 'cockpit.risk.absencesOver' : 'cockpit.risk.absencesNear', {
                      used: subject.attendance.used,
                      allowed: subject.attendance.allowed ?? 0,
                    })}
                  </p>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}

export function NextExamCard({ data, now }: { data: CockpitData; now: Date }) {
  const { t, i18n } = useTranslation();
  const exam = data.nextExam;
  return (
    <Card title={t('cockpit.exam.title')} aria-label={t('cockpit.exam.title')}>
      {!exam ? (
        <p className="text-sm text-text-secondary">{t('cockpit.exam.none')}</p>
      ) : (
        <div className="flex flex-col gap-2">
          <p className="flex items-center gap-2 font-semibold">
            <span
              aria-hidden="true"
              className="h-2.5 w-2.5 shrink-0 rounded-full"
              style={{ backgroundColor: exam.subjectColor ?? 'var(--border-control)' }}
            />
            {exam.title}
          </p>
          <p className="text-sm text-text-secondary">
            {exam.subjectName ?? t('cockpit.deadlines.noSubject')} ·{' '}
            {formatDue(exam.dueAt, exam.dueAllDay, i18n.language)}
          </p>
          <Chip
            tone={daysUntil(exam.dueAt, now) <= 3 ? 'warning' : 'neutral'}
            className="self-start"
          >
            {t('cockpit.exam.inDays', { count: daysUntil(exam.dueAt, now) })}
          </Chip>
        </div>
      )}
    </Card>
  );
}

// ---------------------------------------------------------------------------
// roadmap
// ---------------------------------------------------------------------------

/** Roadmap principal: percentual e próximo tópico. Quem não segue nenhum não vê o cartão. */
export function RoadmapCard({ data }: { data: CockpitData }) {
  const { t, i18n } = useTranslation();
  const r = data.roadmap;
  if (!r) return null;
  const pct = new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 1 }).format(r.percent);
  return (
    <Card
      title={t('cockpit.roadmap.title')}
      aria-label={t('cockpit.roadmap.title')}
      actions={
        <Link
          to={`/roadmaps/${r.slug}`}
          className="text-sm font-semibold text-action hover:underline"
        >
          {t('cockpit.roadmap.open')}
        </Link>
      }
    >
      <p className="font-semibold">{r.title}</p>
      <ProgressBar
        value={r.percent}
        label={t('cockpit.roadmap.progress', {
          percent: pct,
          done: r.doneTopics,
          total: r.totalTopics,
        })}
        showValue
        className="mt-3"
      />
      <p className="mt-3 text-sm text-text-secondary">
        {r.nextNodeTitle
          ? t('cockpit.roadmap.next', { title: r.nextNodeTitle, stage: r.nextStagePosition })
          : t('cockpit.roadmap.none')}
      </p>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// mini heatmap
// ---------------------------------------------------------------------------

export function MiniHeatmapCard({ data }: { data: CockpitData }) {
  const { t, i18n } = useTranslation();
  const grid = useMemo(
    () => buildHeatmapGrid(data.heatmap.days, data.heatmap.from, data.heatmap.to),
    [data.heatmap],
  );
  const total = data.heatmap.days.reduce((sum, d) => sum + d.seconds, 0);
  return (
    <Card
      title={t('cockpit.heatmap.title')}
      aria-label={t('cockpit.heatmap.title')}
      actions={
        <Link to="/deep-work" className={buttonClass('ghost')}>
          {t('cockpit.heatmap.open')}
        </Link>
      }
    >
      <Heatmap
        values={grid.values}
        level={focusLevel}
        label={t('cockpit.heatmap.label', { hours: formatHours(total, i18n.language) })}
        cellTitle={(row, col, value) => {
          const date = grid.dates[row]?.[col];
          if (!date) return '';
          return t('deepWork.heatmap.cell', {
            duration: value > 0 ? formatDuration(value) : t('deepWork.heatmap.none'),
            day: formatDay(date, i18n.language),
          });
        }}
      />
    </Card>
  );
}

// ---------------------------------------------------------------------------
// primeiro acesso e ações rápidas
// ---------------------------------------------------------------------------

export function OnboardingCard({
  data,
  onStep,
}: {
  data: CockpitData;
  onStep: (step: OnboardingStepKey) => void;
}) {
  const { t } = useTranslation();
  const steps = onboardingSteps(data);
  const done = steps.filter((s) => s.done).length;
  const next = steps.find((s) => !s.done)?.key;
  return (
    <Card
      title={t('cockpit.onboarding.title')}
      eyebrow={t('cockpit.onboarding.progress', { done, total: steps.length })}
      aria-label={t('cockpit.onboarding.title')}
    >
      <p className="mb-4 text-sm text-text-secondary">{t('cockpit.onboarding.hint')}</p>
      <ProgressBar
        className="mb-4"
        value={(done / steps.length) * 100}
        label={t('cockpit.onboarding.progress', { done, total: steps.length })}
      />
      <ol className="m-0 flex list-none flex-col gap-2 p-0">
        {steps.map((s) => (
          <li key={s.key} className="card-inner flex flex-wrap items-center gap-3 px-4 py-3">
            <span
              aria-hidden="true"
              className={cn(
                'flex h-7 w-7 shrink-0 items-center justify-center rounded-full border-2',
                s.done ? 'border-action bg-action text-action-ink' : 'border-border-control',
              )}
            >
              {s.done && <Icon name="check" size={16} />}
            </span>
            <span className="min-w-0 flex-1">
              <span
                className={cn(
                  'block text-sm font-semibold',
                  s.done && 'text-text-muted line-through',
                )}
              >
                {t(`cockpit.onboarding.steps.${s.key}.title`)}
              </span>
              <span className="block text-xs text-text-muted">
                {s.done
                  ? t('cockpit.onboarding.done')
                  : t(`cockpit.onboarding.steps.${s.key}.body`)}
              </span>
            </span>
            {!s.done && (
              <Button
                variant={s.key === next ? 'primary' : 'secondary'}
                onClick={() => onStep(s.key)}
              >
                {t(`cockpit.onboarding.steps.${s.key}.action`)}
              </Button>
            )}
          </li>
        ))}
      </ol>
    </Card>
  );
}

export function QuickActions({
  onFocus,
  onReview,
  onNewTask,
  focusActive,
}: {
  onFocus: () => void;
  onReview: () => void;
  onNewTask: () => void;
  focusActive: boolean;
}) {
  const { t } = useTranslation();
  return (
    <nav aria-label={t('cockpit.actions.label')} className="flex flex-wrap gap-2">
      <Button variant="secondary" onClick={onFocus}>
        <Icon name="deepWork" size={18} />
        {focusActive ? t('cockpit.actions.goToSession') : t('cockpit.actions.startFocus')}
      </Button>
      <Button variant="secondary" onClick={onReview}>
        <Icon name="flashcards" size={18} />
        {t('cockpit.actions.review')}
      </Button>
      <Button variant="secondary" onClick={onNewTask}>
        <Icon name="plus" size={18} />
        {t('cockpit.actions.newTask')}
      </Button>
      <span className="hidden items-center text-xs text-text-muted sm:flex">
        {t('cockpit.actions.paletteHint')}
      </span>
    </nav>
  );
}
