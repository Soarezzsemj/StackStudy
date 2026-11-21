import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Button, Card, Icon, ProgressBar, SelectField } from '@/components/ui';
import { errorMessage } from '@/features/auth/errorMessage';
import { useCurrentSubjects } from '@/features/stack/queries';
import { ApiError } from '@/lib/api/client';
import type { RoomDetail, RoomMode } from '@/lib/api/rooms';
import { cn } from '@/lib/cn';
import { formatClock } from '@/lib/time';
import { useNow } from '@/lib/useNow';
import { useRoomStore } from '@/stores/roomStore';
import { useSessionControl } from './queries';
import { formatDuration, formatTime, goalPercent, phaseAt, sessionSeconds } from './roomLogic';

const MODES: RoomMode[] = ['FREE', 'POMODORO'];

/** Cartão principal: modo no topo, cronômetro grande calculado dos timestamps do servidor. */
export function RoomCard({
  room,
  studyingNow,
  mode,
  onModeChange,
}: {
  room: RoomDetail;
  studyingNow: number;
  /** Modo mostrado (o da sessão em andamento ou o escolhido). */
  mode: RoomMode;
  onModeChange: (mode: RoomMode) => void;
}) {
  const { t, i18n } = useTranslation();
  const stored = useRoomStore((s) => s.session);
  const offsetMs = useRoomStore((s) => s.offsetMs);
  const session = stored && stored.roomId === room.id ? stored : null;
  const [subjectId, setSubjectId] = useState('');
  const { list: subjects } = useCurrentSubjects();
  const control = useSessionControl(room.id);
  const now = useNow(true);

  const seconds = session ? sessionSeconds(session, offsetMs, now) : 0;
  const phase = phaseAt(room.pomodoro, now + offsetMs);
  // Os totais do detalhe já trazem a minha sessão como estava na hora da leitura: troca pelo agora.
  const fetched = room.session?.countedSeconds ?? 0;
  const live = (total: number) => total - fetched + seconds;
  const goal = goalPercent(live(room.weekSeconds), room.weeklyGoalHours);
  const busy =
    control.start.isPending ||
    control.pause.isPending ||
    control.resume.isPending ||
    control.stop.isPending;
  const startError = control.start.error;
  const conflict = startError instanceof ApiError && startError.code === 'ROOM_SESSION_ACTIVE';
  const error = startError ?? control.pause.error ?? control.resume.error ?? control.stop.error;
  const paused = session && session.state !== 'STUDYING' && session.state !== 'AWAY';
  const autoPaused = session?.state === 'AUTO_PAUSED';

  function start(takeover = false) {
    control.start.mutate({
      mode,
      ...(subjectId ? { subjectId } : {}),
      ...(takeover ? { takeover: true } : {}),
    });
  }

  return (
    <Card variant="hero" aria-label={t('rooms.card.label', { name: room.name })}>
      {/* Seletor de modo no topo do cartão. */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div
          role="radiogroup"
          aria-label={t('rooms.mode.label')}
          className="flex rounded-pill border border-border bg-bg/40 p-1"
        >
          {MODES.map((m) => (
            <button
              key={m}
              type="button"
              role="radio"
              aria-checked={mode === m}
              disabled={!!session}
              onClick={() => onModeChange(m)}
              className={cn(
                'min-h-9 rounded-pill px-4 text-sm font-semibold disabled:cursor-not-allowed',
                mode === m ? 'bg-action text-action-ink' : 'text-text-on-space hover:text-text',
              )}
            >
              {t(`rooms.mode.${m}`)}
            </button>
          ))}
        </div>
        <p className="text-sm text-text-on-space" aria-live="polite">
          {t('rooms.card.studyingNow', { count: studyingNow })}
        </p>
      </div>
      <p className="mt-3 text-sm text-text-on-space">{t(`rooms.mode.explain.${mode}`)}</p>

      {/* Cronômetro grande. */}
      <div className="my-6 text-center">
        {mode === 'POMODORO' ? (
          <>
            <p className="label text-text-on-space">
              {t(`rooms.phase.${phase.phase}`)} ·{' '}
              {t('rooms.phase.cycle', { cycle: phase.cycle, of: phase.of })}
            </p>
            <p
              role="timer"
              aria-label={t('rooms.phase.remaining', { time: formatClock(phase.remainingSeconds) })}
              className="tabular mt-2 text-7xl font-[650] leading-none tracking-card sm:text-8xl"
            >
              {formatClock(phase.remainingSeconds)}
            </p>
            <p className="mt-3 text-sm text-text-on-space">
              {session
                ? t('rooms.pomodoro.cycles', { count: session.cyclesCompleted })
                : t('rooms.pomodoro.shared')}
            </p>
          </>
        ) : (
          <>
            <p className="label text-text-on-space">
              {session
                ? t('rooms.free.since', { time: formatTime(session.startedAt, i18n.language) })
                : t('rooms.free.ready')}
            </p>
            <p
              role="timer"
              aria-label={t('rooms.free.elapsed', { time: formatClock(seconds) })}
              className="tabular mt-2 text-7xl font-[650] leading-none tracking-card sm:text-8xl"
            >
              {formatClock(seconds)}
            </p>
          </>
        )}
        {session && (
          <p className="mt-3 text-sm font-semibold" aria-live="polite">
            {autoPaused ? t('rooms.state.autoPausedLong') : t(`rooms.state.${session.state}`)}
          </p>
        )}
      </div>

      <dl className="m-0 grid grid-cols-3 gap-3 text-center text-sm">
        <Stat
          label={t('rooms.stats.today')}
          value={formatDuration(live(room.myStats.todaySeconds))}
        />
        <Stat
          label={t('rooms.stats.week')}
          value={formatDuration(live(room.myStats.weekSeconds))}
        />
        <Stat
          label={t('rooms.stats.longest')}
          value={formatDuration(Math.max(room.myStats.longestSeconds, seconds))}
        />
      </dl>

      {/* Controles. */}
      <div className="mt-6 flex flex-wrap items-end justify-center gap-3">
        {!session && (
          <>
            <SelectField
              label={t('rooms.card.subject')}
              value={subjectId}
              onChange={(e) => setSubjectId(e.target.value)}
              className="min-w-48"
            >
              <option value="">{t('rooms.card.noSubject')}</option>
              {subjects.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </SelectField>
            <Button className="min-w-44" disabled={busy} onClick={() => start(false)}>
              <Icon name="deepWork" size={18} />
              {t('rooms.card.start')}
            </Button>
          </>
        )}
        {session && (
          <>
            {paused ? (
              <Button className="min-w-36" disabled={busy} onClick={() => control.resume.mutate()}>
                {t('rooms.card.resume')}
              </Button>
            ) : (
              <Button
                variant="secondary"
                className="min-w-36"
                disabled={busy}
                onClick={() => control.pause.mutate()}
              >
                {t('rooms.card.pause')}
              </Button>
            )}
            <Button
              variant="secondary"
              className="min-w-36"
              disabled={busy}
              onClick={() => control.stop.mutate()}
            >
              <Icon name="check" size={18} />
              {t('rooms.card.stop')}
            </Button>
          </>
        )}
      </div>

      {room.activeElsewhere && !session && (
        <p className="mt-4 text-center text-sm text-text-on-space" role="status">
          {room.activeElsewhere === 'DEEP_WORK' ? (
            <>
              {t('rooms.card.deepWorkActive')}{' '}
              <Link to="/deep-work" className="font-semibold text-action hover:underline">
                {t('rooms.card.openDeepWork')}
              </Link>
            </>
          ) : (
            t('rooms.card.elsewhere')
          )}
        </p>
      )}
      {conflict && (
        <div className="mt-4 flex flex-col items-center gap-2 text-center" role="alert">
          <p className="text-sm">{t('rooms.card.conflict')}</p>
          <Button variant="secondary" disabled={busy} onClick={() => start(true)}>
            {t('rooms.card.takeover')}
          </Button>
        </div>
      )}
      {error && !conflict && (
        <p role="alert" className="mt-4 text-center text-sm text-danger">
          {errorMessage(t, error)}
        </p>
      )}

      <p className="mt-6 text-center text-xs text-text-on-space">
        {t('rooms.card.note', {
          save: room.timing.saveEveryMinutes,
          confirm: room.timing.confirmEveryMinutes,
        })}
      </p>

      {room.weeklyGoalHours && goal !== null && (
        <div className="mt-6">
          <ProgressBar
            value={goal}
            label={t('rooms.goal.progress', {
              done: formatDuration(live(room.weekSeconds)),
              goal: room.weeklyGoalHours,
            })}
            showValue
          />
        </div>
      )}
    </Card>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="card-inner bg-bg/40 p-3">
      <dt className="label text-text-on-space">{label}</dt>
      <dd className="tabular m-0 mt-1 text-lg font-semibold">{value}</dd>
    </div>
  );
}
