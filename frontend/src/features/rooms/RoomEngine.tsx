import { useCallback, useEffect, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Button, Dialog } from '@/components/ui';
import { deepWorkKeys, useActiveSession } from '@/features/deepwork/queries';
import { ApiError } from '@/lib/api/client';
import { roomsApi, type RoomSession } from '@/lib/api/rooms';
import { useNow } from '@/lib/useNow';
import { usePageVisible } from '@/lib/usePageVisible';
import { useRoomStore } from '@/stores/roomStore';
import { confirmSecondsLeft, HEARTBEAT_MS } from './roomLogic';
import { roomKeys } from './queries';

/**
 * Motor da sala de estudo, montado no AppShell (vale em qualquer tela): manda o heartbeat a cada
 * 30 s enquanto houver sessão de sala — também com a aba oculta, senão o servidor marcaria
 * "ausente" em 2 min — e um imediato ao voltar para a aba. Mostra o "Ainda está estudando?".
 * Não envia duração nenhuma: o servidor conta o tempo.
 */
export function RoomEngine() {
  const qc = useQueryClient();
  const active = useActiveSession();
  const roomId = active.data?.roomId;
  const session = useRoomStore((s) => s.session);
  const setSession = useRoomStore((s) => s.setSession);
  const receivedAt = useRoomStore((s) => s.receivedAt);
  const visible = usePageVisible();

  const beat = useCallback(async () => {
    if (!roomId) return;
    try {
      const s = await roomsApi.heartbeat(roomId);
      setSession(s.state === 'OFFLINE' ? null : s);
    } catch (e) {
      // Sessão encerrada (TIMEOUT, removido, sala apagada): limpa e ressincroniza.
      if (e instanceof ApiError && (e.status === 404 || e.status === 409)) {
        setSession(null);
        void qc.invalidateQueries({ queryKey: deepWorkKeys.active });
        void qc.invalidateQueries({ queryKey: roomKeys.all });
      }
    }
  }, [roomId, setSession, qc]);

  useEffect(() => {
    if (!roomId) return;
    void beat();
    const id = window.setInterval(() => void beat(), HEARTBEAT_MS);
    return () => window.clearInterval(id);
  }, [roomId, beat]);

  // Voltou para a aba: atualiza na hora.
  useEffect(() => {
    if (visible && roomId) void beat();
  }, [visible, roomId, beat]);

  // Sem sessão no servidor (resposta mais nova que a sessão da tela): nada de cronômetro.
  useEffect(() => {
    if (active.isSuccess && !roomId && session && active.dataUpdatedAt > receivedAt) {
      setSession(null);
    }
  }, [active.isSuccess, active.dataUpdatedAt, roomId, session, receivedAt, setSession]);

  if (!session?.confirmDeadline) return null;
  return <PresenceConfirmDialog key={session.confirmDeadline} session={session} />;
}

/** "Ainda está estudando?" com contagem regressiva. Enter confirma (o botão já tem o foco). */
export function PresenceConfirmDialog({ session }: { session: RoomSession }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const offsetMs = useRoomStore((s) => s.offsetMs);
  const setSession = useRoomStore((s) => s.setSession);
  const [hidden, setHidden] = useState(false);
  const [busy, setBusy] = useState(false);
  const now = useNow(true);
  const left = confirmSecondsLeft(session.confirmDeadline!, offsetMs, now);
  if (hidden) return null;

  async function confirm() {
    setBusy(true);
    try {
      setSession(await roomsApi.confirm(session.roomId));
      void qc.invalidateQueries({ queryKey: roomKeys.presence(session.roomId) });
    } finally {
      setBusy(false);
    }
  }

  const mm = String(Math.floor(left / 60)).padStart(1, '0');
  const ss = String(left % 60).padStart(2, '0');
  return (
    <Dialog title={t('rooms.confirm.title')} onClose={() => setHidden(true)}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void confirm();
        }}
        className="flex flex-col gap-4"
      >
        <p className="text-text-secondary">{t('rooms.confirm.body')}</p>
        <p
          className="tabular text-center text-5xl font-[650] tracking-card"
          role="timer"
          aria-live="off"
          aria-label={t('rooms.confirm.countdown', { time: `${mm}:${ss}` })}
        >
          {mm}:{ss}
        </p>
        <p className="text-center text-xs text-text-muted" aria-live="polite">
          {left > 0 ? t('rooms.confirm.hint') : t('rooms.confirm.expired')}
        </p>
        <Button type="submit" data-autofocus disabled={busy}>
          {t('rooms.confirm.yes')}
        </Button>
      </form>
    </Dialog>
  );
}
