import { useTranslation } from 'react-i18next';
import {
  Avatar,
  Button,
  Card,
  Chip,
  EmptyState,
  ErrorState,
  Icon,
  Skeleton,
  type IconName,
} from '@/components/ui';
import {
  roomsApi,
  type MemberPresence,
  type Presence,
  type PresenceState,
  type RoomDetail,
} from '@/lib/api/rooms';
import { formatClock } from '@/lib/time';
import { useNow } from '@/lib/useNow';
import { useRoomStore } from '@/stores/roomStore';
import { useRoomAction, useUpdateMe } from './queries';
import { memberSeconds, stateTone } from './roomLogic';

const STATE_ICON: Record<PresenceState, IconName> = {
  STUDYING: 'deepWork',
  PAUSED: 'clock',
  AUTO_PAUSED: 'clock',
  AWAY: 'alert',
  OFFLINE: 'ban',
};

/** Quem está na sala: estado com ícone e texto (nunca só cor), tempo ou ciclos, matéria se permitida. */
export function MembersGrid({
  room,
  presence,
}: {
  room: RoomDetail;
  presence: { data?: Presence; isPending: boolean; isError: boolean; refetch: () => unknown };
}) {
  const { t } = useTranslation();
  const updateMe = useUpdateMe(room.id);
  const data = presence.data;
  return (
    <Card
      title={t('rooms.members.title')}
      aria-label={t('rooms.members.title')}
      actions={
        <label className="flex min-h-11 cursor-pointer items-center gap-2 text-sm">
          <input
            type="checkbox"
            className="h-5 w-5 accent-[var(--action)]"
            checked={room.me.showSubject}
            disabled={updateMe.isPending}
            onChange={(e) => updateMe.mutate({ showSubject: e.target.checked })}
          />
          {t('rooms.members.showSubject')}
        </label>
      }
    >
      {presence.isError ? (
        <ErrorState message={t('rooms.loadError')} onRetry={() => void presence.refetch()} />
      ) : !data ? (
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3" aria-hidden="true">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-24 w-full rounded-inner" />
          ))}
        </div>
      ) : data.members.length <= 1 ? (
        <EmptyState title={t('rooms.members.alone')} body={t('rooms.members.invite')} />
      ) : (
        <ul className="m-0 grid list-none gap-3 p-0 sm:grid-cols-2 xl:grid-cols-3">
          {data.members.map((m) => (
            <MemberCard key={m.userId} member={m} room={room} serverTime={data.serverTime} />
          ))}
        </ul>
      )}
    </Card>
  );
}

function MemberCard({
  member,
  room,
  serverTime,
}: {
  member: MemberPresence;
  room: RoomDetail;
  serverTime: string;
}) {
  const { t } = useTranslation();
  const offsetMs = useRoomStore((s) => s.offsetMs);
  const now = useNow(member.state === 'STUDYING');
  const remove = useRoomAction((userId: string) => roomsApi.removeMember(room.id, userId));
  const mute = useRoomAction((m: MemberPresence) =>
    roomsApi.muteMember(room.id, m.userId, !m.muted),
  );
  const canModerate = room.owner && !member.me;
  return (
    <li className="card-inner flex flex-col gap-2 p-4" data-state={member.state}>
      <div className="flex items-center gap-3">
        <Avatar name={member.nickname} />
        <div className="min-w-0 flex-1">
          <p className="truncate font-semibold">
            {member.nickname}
            {member.me && (
              <span className="ml-2 text-xs text-text-muted">{t('rooms.members.you')}</span>
            )}
          </p>
          <p className="text-xs text-text-muted">
            {member.role === 'OWNER' ? t('rooms.members.owner') : t('rooms.members.member')}
            {member.muted && ` · ${t('rooms.members.muted')}`}
          </p>
        </div>
        <Chip tone={stateTone(member.state)}>
          <Icon name={STATE_ICON[member.state]} size={14} />
          {t(`rooms.state.${member.state}`)}
        </Chip>
      </div>
      {member.state !== 'OFFLINE' && (
        <p className="tabular text-sm text-text-secondary">
          {member.mode === 'POMODORO'
            ? t('rooms.members.cycles', { count: member.cyclesCompleted })
            : t('rooms.members.time', {
                time: formatClock(memberSeconds(member, serverTime, offsetMs, now)),
              })}
          {member.subject && <span className="ml-2 text-text-muted">· {member.subject}</span>}
        </p>
      )}
      {canModerate && (
        <div className="flex flex-wrap gap-2">
          <Button variant="ghost" disabled={mute.isPending} onClick={() => mute.mutate(member)}>
            {member.muted ? t('rooms.members.unmute') : t('rooms.members.mute')}
          </Button>
          <Button
            variant="ghost"
            disabled={remove.isPending}
            onClick={() => remove.mutate(member.userId)}
          >
            {t('rooms.members.remove')}
            <span className="sr-only"> {member.nickname}</span>
          </Button>
        </div>
      )}
    </li>
  );
}
