import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSessionStore } from '@/stores/sessionStore';
import { formatClock, sessionClockSeconds } from '@/lib/time';

function useNow(enabled: boolean) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!enabled) return;
    const id = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, [enabled]);
  return now;
}

/** Barra inferior de todas as telas logadas: sessão ativa e resumo da semana. */
export function StatusBar() {
  const { t } = useTranslation();
  const active = useSessionStore((s) => s.active);
  const now = useNow(active !== null);

  return (
    <footer
      aria-label={t('statusbar.label')}
      className="flex h-statusbar shrink-0 items-center justify-between gap-4 border-t border-border bg-bg px-4 text-xs text-text-muted lg:px-8"
    >
      <div className="flex min-w-0 items-center gap-2" aria-live="off">
        {active ? (
          <>
            <span
              aria-hidden="true"
              className="h-2 w-2 shrink-0 animate-pulse rounded-full"
              style={{ backgroundColor: active.subjectColor }}
            />
            <span className="truncate text-text-secondary">{active.subjectName}</span>
            <span className="tabular text-text">
              {active.plannedMinutes
                ? t('statusbar.remaining', { time: formatClock(sessionClockSeconds(active, now)) })
                : formatClock(sessionClockSeconds(active, now))}
            </span>
          </>
        ) : (
          <>
            <span aria-hidden="true" className="h-2 w-2 shrink-0 rounded-full bg-border-control" />
            <span>{t('statusbar.noSession')}</span>
          </>
        )}
      </div>
      <div className="tabular flex shrink-0 items-center gap-4">
        <span>{t('statusbar.week', { hours: 0 })}</span>
        <span>{t('statusbar.gpaEmpty')}</span>
      </div>
    </footer>
  );
}
