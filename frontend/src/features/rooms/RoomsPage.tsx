import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate, useParams } from 'react-router-dom';
import { Button, Card, EmptyState, ErrorState, Icon, PageTitle, Skeleton } from '@/components/ui';
import { ApiError } from '@/lib/api/client';
import type { RoomMode } from '@/lib/api/rooms';
import { useRoomStore } from '@/stores/roomStore';
import { MembersGrid } from './MembersGrid';
import { usePresence, useMyRooms, useRoom } from './queries';
import { RoomCard } from './RoomCard';
import {
  CreateRoomDialog,
  JoinRoomDialog,
  MySettingsDialog,
  OwnerSettingsDialog,
  ReportDialog,
} from './RoomDialogs';
import { MyRoomsCard, PublicRoomsCard, RankingCard, ReactionsCard, SafetyCard } from './RoomSide';

type PageDialog = 'create' | 'join' | 'me' | 'owner' | 'report' | null;

/**
 * Salas de estudo: estudar junto, vendo quem está ativo, SEM chat livre. /rooms abre a sala da
 * sessão em andamento (ou a primeira); sem salas, convida a criar ou entrar com código.
 */
export function RoomsPage() {
  const { id } = useParams();
  const [dialog, setDialog] = useState<PageDialog>(null);
  const close = () => setDialog(null);
  return (
    <>
      {id ? (
        <RoomView id={id} onDialog={setDialog} dialog={dialog} onClose={close} />
      ) : (
        <RoomsHome onDialog={setDialog} />
      )}
      {dialog === 'create' && <CreateRoomDialog onClose={close} />}
      {dialog === 'join' && <JoinRoomDialog onClose={close} />}
    </>
  );
}

function NoChatNotice() {
  const { t } = useTranslation();
  return (
    <p
      role="note"
      className="flex items-center gap-3 rounded-inner border border-space-glow/40 bg-space-deep/30 px-4 py-3 text-sm text-text-on-space"
    >
      <Icon name="ban" size={18} className="shrink-0 text-space-glow" />
      {t('rooms.noChat')}
    </p>
  );
}

function Header({ actions }: { actions?: ReactNode }) {
  const { t } = useTranslation();
  return (
    <PageTitle title={t('rooms.title')} description={t('rooms.description')} actions={actions} />
  );
}

function RoomsHome({ onDialog }: { onDialog: (d: PageDialog) => void }) {
  const { t } = useTranslation();
  const mine = useMyRooms();
  const session = useRoomStore((s) => s.session);
  if (mine.isPending) return <PageSkeleton />;
  if (mine.isError) {
    return (
      <>
        <Header />
        <ErrorState message={t('rooms.loadError')} onRetry={() => void mine.refetch()} />
      </>
    );
  }
  const target = session?.roomId ?? mine.data[0]?.id;
  if (target) return <Navigate to={`/rooms/${target}`} replace />;
  return (
    <>
      <Header />
      <div className="flex flex-col gap-6">
        <NoChatNotice />
        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
          <Card>
            <EmptyState
              title={t('rooms.empty.title')}
              body={t('rooms.empty.body')}
              action={
                <div className="flex flex-wrap justify-center gap-2">
                  <Button onClick={() => onDialog('create')}>
                    <Icon name="plus" size={18} />
                    {t('rooms.create.open')}
                  </Button>
                  <Button variant="secondary" onClick={() => onDialog('join')}>
                    {t('rooms.join.open')}
                  </Button>
                </div>
              }
            />
          </Card>
          <PublicRoomsCard />
        </div>
      </div>
    </>
  );
}

function RoomView({
  id,
  dialog,
  onDialog,
  onClose,
}: {
  id: string;
  dialog: PageDialog;
  onDialog: (d: PageDialog) => void;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const room = useRoom(id);
  const presence = usePresence(id);
  const sessionMode = useRoomStore((s) => (s.session?.roomId === id ? s.session.mode : undefined));
  const [chosen, setChosen] = useState<RoomMode>();

  if (room.isPending) return <PageSkeleton />;
  if (room.isError) {
    const missing = room.error instanceof ApiError && room.error.status === 404;
    return (
      <>
        <Header />
        {missing ? (
          <Card>
            <EmptyState title={t('rooms.notFound')} body={t('rooms.empty.body')} />
          </Card>
        ) : (
          <ErrorState message={t('rooms.loadError')} onRetry={() => void room.refetch()} />
        )}
      </>
    );
  }

  const data = room.data;
  const studyingNow = presence.data?.studyingNow ?? 0;
  // Modo da sessão em andamento; sem sessão, o escolhido no cartão (ou o padrão da sala).
  const mode = sessionMode ?? chosen ?? data.defaultMode;
  return (
    <>
      <Header
        actions={
          <>
            <Button variant="secondary" onClick={() => onDialog('me')}>
              <Icon name="user" size={18} />
              {t('rooms.me.open')}
            </Button>
            {data.owner && (
              <Button variant="secondary" onClick={() => onDialog('owner')}>
                <Icon name="edit" size={18} />
                {t('rooms.owner.open')}
              </Button>
            )}
          </>
        }
      />
      <div className="flex flex-col gap-6">
        <NoChatNotice />
        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
          <div className="flex min-w-0 flex-col gap-6">
            <h2 className="card-title m-0">
              {data.name}
              {data.owner && data.inviteCode && (
                <span className="tabular ml-3 text-sm font-normal text-text-secondary">
                  {t('rooms.owner.codeInline', { code: data.inviteCode })}
                </span>
              )}
            </h2>
            <RoomCard room={data} studyingNow={studyingNow} mode={mode} onModeChange={setChosen} />
            <MembersGrid room={data} presence={presence} />
          </div>
          <aside className="flex flex-col gap-6" aria-label={t('rooms.aside')}>
            <RankingCard room={data} mode={mode} />
            <ReactionsCard room={data} presence={presence.data} />
            <MyRoomsCard
              activeId={id}
              onCreate={() => onDialog('create')}
              onJoin={() => onDialog('join')}
            />
            <PublicRoomsCard />
            <SafetyCard room={data} presence={presence.data} onReport={() => onDialog('report')} />
          </aside>
        </div>
      </div>
      {dialog === 'me' && <MySettingsDialog room={data} onClose={onClose} />}
      {dialog === 'owner' && data.owner && <OwnerSettingsDialog room={data} onClose={onClose} />}
      {dialog === 'report' && (
        <ReportDialog room={data} members={presence.data?.members ?? []} onClose={onClose} />
      )}
    </>
  );
}

function PageSkeleton() {
  const { t } = useTranslation();
  return (
    <>
      <Header />
      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]" role="status">
        <span className="sr-only">{t('rooms.loading')}</span>
        <div className="flex flex-col gap-6" aria-hidden="true">
          <Skeleton className="h-96 w-full rounded-card" />
          <Skeleton className="h-48 w-full rounded-card" />
        </div>
        <div className="flex flex-col gap-6" aria-hidden="true">
          <Skeleton className="h-56 w-full rounded-card" />
          <Skeleton className="h-40 w-full rounded-card" />
        </div>
      </div>
    </>
  );
}
