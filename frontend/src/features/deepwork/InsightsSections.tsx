import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  Heatmap,
  Icon,
  LoadingState,
  ProgressBar,
  StatBlock,
} from '@/components/ui';
import type { StudySession } from '@/lib/api/deepwork';
import type { Subject } from '@/lib/api/stack';
import { cn } from '@/lib/cn';
import { formatDay, formatDuration, formatHours } from './format';
import { buildHeatmapGrid, focusLevel } from './heatmapGrid';
import { useDeleteSession, useHeatmap, useHistory, useStats } from './queries';

/** Hoje, semana x meta, sequências, média diária e horas por matéria. */
export function StatsSection() {
  const { t, i18n } = useTranslation();
  const stats = useStats();
  const [range, setRange] = useState<'week' | 'month'>('week');

  if (stats.isPending) return <LoadingState />;
  if (stats.isError) {
    return (
      <ErrorState message={t('deepWork.stats.loadError')} onRetry={() => void stats.refetch()} />
    );
  }
  const s = stats.data;
  const lang = i18n.language;
  const bySubject = range === 'week' ? s.weekBySubject : s.monthBySubject;
  const top = bySubject.reduce((m, x) => Math.max(m, x.seconds), 0);

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
      <Card title={t('deepWork.stats.title')}>
        <dl className="m-0 grid grid-cols-2 gap-6 sm:grid-cols-4">
          <StatBlock
            label={t('deepWork.stats.today')}
            value={formatHours(s.todaySeconds, lang)}
            unit="h"
          />
          <StatBlock
            label={t('deepWork.stats.week')}
            value={formatHours(s.weekSeconds, lang)}
            unit={t('deepWork.stats.ofGoal', { goal: formatHours(s.weeklyGoalSeconds, lang) })}
          />
          <StatBlock
            label={t('deepWork.stats.streak')}
            value={s.currentStreak}
            unit={t('deepWork.stats.days', { count: s.currentStreak })}
            hint={t('deepWork.stats.longest', { count: s.longestStreak })}
          />
          <StatBlock
            label={t('deepWork.stats.average')}
            value={formatDuration(s.dailyAverageSeconds)}
            hint={t('deepWork.stats.averageHint')}
          />
        </dl>
        {s.weeklyGoalSeconds > 0 && (
          <ProgressBar
            className="mt-6"
            value={s.weekSeconds}
            max={s.weeklyGoalSeconds}
            label={t('deepWork.stats.goalProgress')}
            showValue
          />
        )}
      </Card>

      <Card
        title={t('deepWork.stats.bySubject')}
        actions={
          <div role="group" aria-label={t('deepWork.stats.range')} className="flex gap-1">
            {(['week', 'month'] as const).map((r) => (
              <button
                key={r}
                type="button"
                aria-pressed={range === r}
                onClick={() => setRange(r)}
                className={cn(
                  'label min-h-11 rounded-pill px-3',
                  range === r ? 'bg-surface-raised text-text' : 'text-text-muted hover:text-text',
                )}
              >
                {t(`deepWork.stats.${r}Short`)}
              </button>
            ))}
          </div>
        }
      >
        {bySubject.length === 0 ? (
          <EmptyState title={t('deepWork.stats.noData')} />
        ) : (
          <ul className="m-0 flex list-none flex-col gap-4 p-0">
            {bySubject.map((x) => (
              <li key={x.subjectId} className="flex flex-col gap-2">
                <div className="flex items-center gap-2 text-sm">
                  <span
                    aria-hidden="true"
                    className="h-2.5 w-2.5 rounded-full"
                    style={{ backgroundColor: x.color }}
                  />
                  <span className="flex-1 truncate">{x.name}</span>
                  <span className="tabular text-text-secondary">
                    {formatHours(x.seconds, lang)} h
                  </span>
                </div>
                <ProgressBar value={x.seconds} max={top} color={x.color} label={x.name} />
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}

/** Heatmap dos últimos 12 meses, agregado pelo backend no fuso do navegador. */
export function FocusHeatmap() {
  const { t, i18n } = useTranslation();
  const heatmap = useHeatmap();
  const grid = useMemo(
    () =>
      heatmap.data ? buildHeatmapGrid(heatmap.data.days, heatmap.data.from, heatmap.data.to) : null,
    [heatmap.data],
  );
  const total = heatmap.data?.days.reduce((sum, d) => sum + d.seconds, 0) ?? 0;

  return (
    <Card title={t('deepWork.heatmap.title')} eyebrow={t('deepWork.heatmap.eyebrow')}>
      {heatmap.isPending ? (
        <LoadingState />
      ) : heatmap.isError || !grid ? (
        <ErrorState
          message={t('deepWork.heatmap.loadError')}
          onRetry={() => void heatmap.refetch()}
        />
      ) : (
        <>
          <Heatmap
            values={grid.values}
            level={focusLevel}
            label={t('deepWork.heatmap.label', { hours: formatHours(total, i18n.language) })}
            cellTitle={(row, col, value) => {
              const date = grid.dates[row]?.[col];
              if (!date) return '';
              return t('deepWork.heatmap.cell', {
                duration: value > 0 ? formatDuration(value) : t('deepWork.heatmap.none'),
                day: formatDay(date, i18n.language),
              });
            }}
          />
          {total === 0 && (
            <p className="mt-3 text-sm text-text-muted">{t('deepWork.heatmap.empty')}</p>
          )}
        </>
      )}
    </Card>
  );
}

/** Sessões recentes, com registro manual, edição e exclusão. */
export function HistoryCard({
  subjectsById,
  onAdd,
  onEdit,
}: {
  subjectsById: Map<string, Subject>;
  onAdd: () => void;
  onEdit: (session: StudySession) => void;
}) {
  const { t, i18n } = useTranslation();
  const history = useHistory();
  const remove = useDeleteSession();
  const [confirming, setConfirming] = useState<string | null>(null);
  const items = history.data?.pages.flatMap((p) => p.items) ?? [];

  return (
    <Card
      title={t('deepWork.history.title')}
      actions={
        <Button variant="secondary" onClick={onAdd}>
          <Icon name="plus" size={18} />
          {t('deepWork.manual.add')}
        </Button>
      }
    >
      {history.isPending ? (
        <LoadingState />
      ) : history.isError ? (
        <ErrorState
          message={t('deepWork.history.loadError')}
          onRetry={() => void history.refetch()}
        />
      ) : items.length === 0 ? (
        <EmptyState title={t('deepWork.history.empty')} body={t('deepWork.history.emptyBody')} />
      ) : (
        <ul className="m-0 flex list-none flex-col gap-2 p-0">
          {items.map((s) => {
            const subject = subjectsById.get(s.subjectId);
            const started = new Date(s.startedAt);
            return (
              <li key={s.id} className="card-inner flex flex-wrap items-center gap-3 px-4 py-3">
                <span
                  aria-hidden="true"
                  className="h-2.5 w-2.5 shrink-0 rounded-full"
                  style={{ backgroundColor: subject?.color ?? 'var(--border-control)' }}
                />
                <div className="min-w-0 flex-1">
                  <p className="truncate font-semibold">
                    {subject?.name ?? t('deepWork.history.unknownSubject')}
                  </p>
                  <p className="text-sm text-text-secondary">
                    <span className="tabular">
                      {new Intl.DateTimeFormat(i18n.language, {
                        dateStyle: 'medium',
                        timeStyle: 'short',
                      }).format(started)}
                    </span>
                    {s.source === 'MANUAL' && <span> · {t('deepWork.history.manual')}</span>}
                    {s.note && <span> · {s.note}</span>}
                  </p>
                </div>
                <span className="tabular text-text">{formatDuration(s.effectiveSeconds)}</span>
                <Button
                  variant="ghost"
                  iconOnly
                  aria-label={t('deepWork.history.edit')}
                  onClick={() => onEdit(s)}
                >
                  <Icon name="edit" size={18} />
                </Button>
                <Button
                  variant={confirming === s.id ? 'primary' : 'ghost'}
                  iconOnly={confirming !== s.id}
                  aria-label={confirming === s.id ? undefined : t('deepWork.history.delete')}
                  disabled={remove.isPending}
                  onClick={() =>
                    confirming === s.id
                      ? remove.mutate(s.id, { onSettled: () => setConfirming(null) })
                      : setConfirming(s.id)
                  }
                >
                  {confirming === s.id ? (
                    t('common.confirmDelete')
                  ) : (
                    <Icon name="trash" size={18} />
                  )}
                </Button>
              </li>
            );
          })}
        </ul>
      )}
      {history.hasNextPage && (
        <Button
          variant="secondary"
          className="mt-4"
          disabled={history.isFetchingNextPage}
          onClick={() => void history.fetchNextPage()}
        >
          {t('stack.tasks.loadMore')}
        </Button>
      )}
      {remove.error && <ErrorState className="mt-4" message={t('errors.generic')} />}
    </Card>
  );
}
