import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { displaySeconds } from '@/features/deepwork/clock';
import { formatHours } from '@/features/deepwork/format';
import { useActiveSession, useStats } from '@/features/deepwork/queries';
import { useSubjects } from '@/features/stack/queries';
import { formatClock } from '@/lib/time';
import { useNow } from '@/lib/useNow';

/** Barra inferior de todas as telas logadas: sessão ativa (do servidor) e horas da semana. */
export function StatusBar() {
  const { t, i18n } = useTranslation();
  const { data: active } = useActiveSession();
  const { data: subjects } = useSubjects();
  const { data: stats } = useStats();
  const now = useNow(!!active && !active.pausedAt);
  const subject = active ? subjects?.find((s) => s.id === active.subjectId) : undefined;

  return (
    <footer
      aria-label={t('statusbar.label')}
      className="flex h-statusbar shrink-0 items-center justify-between gap-4 border-t border-border bg-bg px-4 text-xs text-text-muted lg:px-8"
    >
      <div className="flex min-w-0 items-center gap-2" aria-live="off">
        {active?.roomId ? (
          <Link
            to={`/rooms/${active.roomId}`}
            className="flex min-w-0 items-center gap-2 hover:text-text"
          >
            <span
              aria-hidden="true"
              className="h-2 w-2 shrink-0 animate-pulse rounded-full bg-action motion-reduce:animate-none"
            />
            <span className="truncate text-text-secondary">{t('statusbar.room')}</span>
          </Link>
        ) : active ? (
          <Link to="/deep-work" className="flex min-w-0 items-center gap-2 hover:text-text">
            <span
              aria-hidden="true"
              className={`h-2 w-2 shrink-0 rounded-full ${active.pausedAt ? '' : 'animate-pulse motion-reduce:animate-none'}`}
              style={{ backgroundColor: subject?.color ?? 'var(--action)' }}
            />
            <span className="truncate text-text-secondary">
              {subject?.name ?? t('statusbar.session')}
            </span>
            <span className="tabular text-text">
              {active.mode === 'POMODORO'
                ? t('statusbar.remaining', { time: formatClock(displaySeconds(active, now)) })
                : formatClock(displaySeconds(active, now))}
            </span>
            {active.pausedAt && <span>{t('statusbar.paused')}</span>}
          </Link>
        ) : (
          <>
            <span aria-hidden="true" className="h-2 w-2 shrink-0 rounded-full bg-border-control" />
            <span>{t('statusbar.noSession')}</span>
          </>
        )}
      </div>
      <div className="tabular flex shrink-0 items-center gap-4">
        <span>
          {t('statusbar.week', { hours: formatHours(stats?.weekSeconds ?? 0, i18n.language) })}
        </span>
        <span>{t('statusbar.gpaEmpty')}</span>
      </div>
    </footer>
  );
}
