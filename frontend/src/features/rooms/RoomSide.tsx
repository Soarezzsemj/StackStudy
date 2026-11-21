import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import {
  Button,
  Card,
  Chip,
  EmptyState,
  ErrorState,
  Icon,
  SelectField,
  Skeleton,
  TextField,
} from '@/components/ui';
import { errorMessage } from '@/features/auth/errorMessage';
import {
  REACTIONS,
  roomsApi,
  type Presence,
  type Reaction,
  type RoomDetail,
  type RoomMode,
} from '@/lib/api/rooms';
import { cn } from '@/lib/cn';
import { useAuthStore } from '@/stores/authStore';
import { useDebouncedValue } from '@/lib/useDebouncedValue';
import {
  useBlocks,
  useJoinPublic,
  useMyRooms,
  usePublicRooms,
  useRanking,
  useRoomAction,
} from './queries';
import { formatDuration, validNickname } from './roomLogic';

// ---------------------------------------------------------------------------
// ranking
// ---------------------------------------------------------------------------

export function RankingCard({ room, mode }: { room: RoomDetail; mode: RoomMode }) {
  const { t } = useTranslation();
  const [period, setPeriod] = useState<'today' | 'week'>('week');
  const ranking = useRanking(room.id, period, mode);
  return (
    <Card title={t('rooms.ranking.title')} aria-label={t('rooms.ranking.title')}>
      <p className="mb-3 text-xs text-text-secondary">{t(`rooms.ranking.by.${mode}`)}</p>
      <div
        role="group"
        aria-label={t('rooms.ranking.period')}
        className="mb-4 flex rounded-pill border border-border p-1"
      >
        {(['today', 'week'] as const).map((p) => (
          <button
            key={p}
            type="button"
            aria-pressed={period === p}
            onClick={() => setPeriod(p)}
            className={cn(
              'min-h-9 flex-1 rounded-pill px-3 text-sm font-semibold',
              period === p ? 'bg-action text-action-ink' : 'text-text-secondary hover:text-text',
            )}
          >
            {t(`rooms.ranking.${p}`)}
          </button>
        ))}
      </div>
      {ranking.isPending ? (
        <Skeleton className="h-24 w-full" />
      ) : ranking.isError ? (
        <ErrorState message={t('rooms.loadError')} onRetry={() => void ranking.refetch()} />
      ) : ranking.data.entries.length === 0 ? (
        <p className="text-sm text-text-secondary">{t('rooms.ranking.empty')}</p>
      ) : (
        <ol className="m-0 flex list-none flex-col gap-2 p-0">
          {ranking.data.entries.map((e) => (
            <li
              key={`${e.position}-${e.nickname}`}
              className={cn(
                'flex items-center gap-3 rounded-inner px-3 py-2',
                e.me && 'bg-action/10',
              )}
            >
              <span className="tabular w-6 text-sm font-semibold text-text-muted">
                {e.position}º
              </span>
              <span className="flex-1 truncate font-semibold">{e.nickname}</span>
              <span className="tabular text-sm">
                {mode === 'POMODORO'
                  ? t('rooms.ranking.cycles', { count: e.cycles })
                  : formatDuration(e.seconds)}
              </span>
            </li>
          ))}
        </ol>
      )}
      <p className="mt-3 text-xs text-text-muted">
        {room.me.hideFromRanking ? t('rooms.ranking.hidden') : t('rooms.ranking.optOut')}
      </p>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// reações (sem texto livre)
// ---------------------------------------------------------------------------

export function ReactionsCard({ room, presence }: { room: RoomDetail; presence?: Presence }) {
  const { t, i18n } = useTranslation();
  const others = (presence?.members ?? []).filter((m) => !m.me);
  const [to, setTo] = useState('');
  const [received, setReceived] = useState<Presence['reactions']>([]);
  const send = useRoomAction(({ userId, kind }: { userId: string; kind: Reaction }) =>
    roomsApi.react(room.id, userId, kind),
  );
  const target = to || others[0]?.userId || '';

  // Reações chegam pela presença (entregues uma vez): guarda as últimas para mostrar.
  useEffect(() => {
    const incoming = presence?.reactions ?? [];
    if (incoming.length) setReceived((prev) => [...incoming, ...prev].slice(0, 5));
  }, [presence]);

  return (
    <Card title={t('rooms.reactions.title')} aria-label={t('rooms.reactions.title')}>
      <p className="mb-3 text-xs text-text-secondary">{t('rooms.reactions.hint')}</p>
      {others.length === 0 ? (
        <p className="text-sm text-text-secondary">{t('rooms.reactions.nobody')}</p>
      ) : room.me.muted ? (
        <p className="text-sm text-text-secondary">{t('rooms.reactions.muted')}</p>
      ) : (
        <div className="flex flex-col gap-3">
          <SelectField
            label={t('rooms.reactions.to')}
            value={target}
            onChange={(e) => setTo(e.target.value)}
          >
            {others.map((m) => (
              <option key={m.userId} value={m.userId}>
                {m.nickname}
              </option>
            ))}
          </SelectField>
          <div
            className="grid grid-cols-2 gap-2"
            role="group"
            aria-label={t('rooms.reactions.choose')}
          >
            {REACTIONS.map((kind) => (
              <Button
                key={kind}
                variant="secondary"
                disabled={send.isPending || !target}
                onClick={() => send.mutate({ userId: target, kind })}
              >
                {t(`rooms.reactions.kind.${kind}`)}
              </Button>
            ))}
          </div>
          {send.error && (
            <p role="alert" className="text-sm text-danger">
              {errorMessage(t, send.error)}
            </p>
          )}
          {send.isSuccess && !send.error && (
            <p className="text-sm text-action" aria-live="polite">
              {t('rooms.reactions.sent')}
            </p>
          )}
        </div>
      )}
      {received.length > 0 && (
        <ul
          className="m-0 mt-4 flex list-none flex-col gap-1 p-0"
          aria-live="polite"
          aria-label={t('rooms.reactions.received')}
        >
          {received.map((r, i) => (
            <li key={`${r.at}-${i}`} className="text-sm">
              <span className="font-semibold">{r.fromNickname}</span>:{' '}
              {t(`rooms.reactions.kind.${r.kind}`)}
              <span className="ml-2 text-xs text-text-muted">
                {new Intl.DateTimeFormat(i18n.language, { timeStyle: 'short' }).format(
                  new Date(r.at),
                )}
              </span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

// ---------------------------------------------------------------------------
// minhas salas e públicas
// ---------------------------------------------------------------------------

export function MyRoomsCard({
  activeId,
  onCreate,
  onJoin,
}: {
  activeId?: string;
  onCreate: () => void;
  onJoin: () => void;
}) {
  const { t } = useTranslation();
  const mine = useMyRooms();
  return (
    <Card title={t('rooms.mine.title')} aria-label={t('rooms.mine.title')}>
      {mine.isPending ? (
        <Skeleton className="h-16 w-full" />
      ) : mine.isError ? (
        <ErrorState message={t('rooms.loadError')} onRetry={() => void mine.refetch()} />
      ) : (
        <ul className="m-0 flex list-none flex-col gap-2 p-0">
          {mine.data.map((r) => (
            <li key={r.id}>
              <Link
                to={`/rooms/${r.id}`}
                aria-current={r.id === activeId ? 'page' : undefined}
                className={cn(
                  'card-inner flex min-h-11 items-center justify-between gap-2 px-3 py-2 hover:border-text-secondary',
                  r.id === activeId && 'border-action',
                )}
              >
                <span className="truncate font-semibold">{r.name}</span>
                <span className="tabular text-xs text-text-secondary">
                  {t('rooms.mine.studying', { count: r.studyingNow })}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
      <div className="mt-3 flex flex-wrap gap-2">
        <Button variant="secondary" onClick={onCreate}>
          <Icon name="plus" size={16} />
          {t('rooms.create.open')}
        </Button>
        <Button variant="ghost" onClick={onJoin}>
          {t('rooms.join.open')}
        </Button>
      </div>
    </Card>
  );
}

export function PublicRoomsCard() {
  const { t } = useTranslation();
  const [q, setQ] = useState('');
  const search = useDebouncedValue(q.trim(), 250);
  const list = usePublicRooms(search);
  const join = useJoinPublic();
  const [nickname, setNickname] = useState(useAuthStore.getState().user?.nickname ?? '');
  const [joining, setJoining] = useState<string>();

  return (
    <Card title={t('rooms.public.title')} aria-label={t('rooms.public.title')}>
      <TextField
        label={t('rooms.public.search')}
        type="search"
        value={q}
        onChange={(e) => setQ(e.target.value)}
      />
      <div className="mt-3">
        {list.isPending ? (
          <Skeleton className="h-16 w-full" />
        ) : list.isError ? (
          <ErrorState message={t('rooms.loadError')} onRetry={() => void list.refetch()} />
        ) : list.data.length === 0 ? (
          <EmptyState title={t('rooms.public.empty')} />
        ) : (
          <ul className="m-0 flex list-none flex-col gap-2 p-0">
            {list.data.map((r) => (
              <li key={r.id} className="card-inner flex flex-col gap-2 px-3 py-2">
                <span className="flex items-center justify-between gap-2">
                  <span className="truncate font-semibold">{r.name}</span>
                  {r.member && <Chip tone="action">{t('rooms.public.member')}</Chip>}
                </span>
                <span className="text-xs text-text-secondary">
                  {t('rooms.public.meta', {
                    members: r.memberCount,
                    max: r.maxMembers,
                    studying: r.studyingNow,
                  })}
                  {r.weeklyGoalHours
                    ? ` · ${t('rooms.public.goal', { hours: r.weeklyGoalHours })}`
                    : ''}
                </span>
                {r.member ? (
                  <Link
                    to={`/rooms/${r.id}`}
                    className="text-sm font-semibold text-action hover:underline"
                  >
                    {t('rooms.public.open')}
                  </Link>
                ) : joining === r.id ? (
                  <form
                    className="flex items-end gap-2"
                    onSubmit={(e) => {
                      e.preventDefault();
                      join.mutate({ id: r.id, nickname: nickname.trim() });
                    }}
                  >
                    <TextField
                      label={t('rooms.nickname.label')}
                      value={nickname}
                      maxLength={24}
                      onChange={(e) => setNickname(e.target.value)}
                    />
                    <Button type="submit" disabled={!validNickname(nickname) || join.isPending}>
                      {t('rooms.public.join')}
                    </Button>
                  </form>
                ) : (
                  <Button variant="secondary" disabled={r.full} onClick={() => setJoining(r.id)}>
                    {r.full ? t('rooms.public.full') : t('rooms.public.join')}
                  </Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
      {join.error && (
        <p role="alert" className="mt-2 text-sm text-danger">
          {errorMessage(t, join.error)}
        </p>
      )}
    </Card>
  );
}

// ---------------------------------------------------------------------------
// regras e segurança
// ---------------------------------------------------------------------------

export function SafetyCard({
  room,
  presence,
  onReport,
}: {
  room: RoomDetail;
  presence?: Presence;
  onReport: () => void;
}) {
  const { t } = useTranslation();
  const others = (presence?.members ?? []).filter((m) => !m.me);
  const [who, setWho] = useState('');
  const blocks = useBlocks();
  const block = useRoomAction((userId: string) => roomsApi.block(userId));
  const unblock = useRoomAction((userId: string) => roomsApi.unblock(userId));
  const target = who || others[0]?.userId || '';
  return (
    <Card title={t('rooms.safety.title')} aria-label={t('rooms.safety.title')}>
      <ul className="m-0 mb-4 flex list-disc flex-col gap-1 pl-5 text-sm text-text-secondary">
        <li>{t('rooms.safety.rule1')}</li>
        <li>{t('rooms.safety.rule2')}</li>
        <li>{t('rooms.safety.rule3')}</li>
      </ul>
      <Button variant="secondary" className="w-full" onClick={onReport}>
        <Icon name="alert" size={16} />
        {t('rooms.safety.report')}
      </Button>
      {others.length > 0 && (
        <div className="mt-4 flex flex-col gap-2">
          <SelectField
            label={t('rooms.safety.blockWho')}
            value={target}
            onChange={(e) => setWho(e.target.value)}
          >
            {others.map((m) => (
              <option key={m.userId} value={m.userId}>
                {m.nickname}
              </option>
            ))}
          </SelectField>
          <Button
            variant="ghost"
            disabled={!target || block.isPending}
            onClick={() => block.mutate(target)}
          >
            <Icon name="ban" size={16} />
            {t('rooms.safety.block')}
          </Button>
        </div>
      )}
      {(blocks.data ?? []).length > 0 && (
        <div className="mt-4">
          <p className="label mb-2 text-text-muted">{t('rooms.safety.blocked')}</p>
          <ul className="m-0 flex list-none flex-col gap-1 p-0">
            {blocks.data!.map((b) => (
              <li key={b.userId} className="flex items-center justify-between gap-2 text-sm">
                <span>{b.nickname ?? t('rooms.safety.someone')}</span>
                <Button
                  variant="ghost"
                  disabled={unblock.isPending}
                  onClick={() => unblock.mutate(b.userId)}
                >
                  {t('rooms.safety.unblock')}
                </Button>
              </li>
            ))}
          </ul>
        </div>
      )}
      {room.me.muted && (
        <p className="mt-3 text-xs text-text-muted">{t('rooms.reactions.muted')}</p>
      )}
    </Card>
  );
}
