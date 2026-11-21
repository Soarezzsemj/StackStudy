import { useEffect } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  roomsApi,
  type CreateRoomInput,
  type RoomDetail,
  type RoomMode,
  type RoomSession,
  type UpdateMeInput,
  type UpdateRoomInput,
} from '@/lib/api/rooms';
import { deepWorkKeys } from '@/features/deepwork/queries';
import { usePageVisible } from '@/lib/usePageVisible';
import { useTimeZone } from '@/lib/useTimeZone';
import { useRoomStore } from '@/stores/roomStore';
import { PRESENCE_POLL_MS } from './roomLogic';

export const roomKeys = {
  all: ['rooms'] as const,
  mine: ['rooms', 'mine'] as const,
  public: (q: string) => ['rooms', 'public', q] as const,
  detail: (id: string) => ['rooms', 'detail', id] as const,
  presence: (id: string) => ['rooms', 'presence', id] as const,
  ranking: (id: string) => ['rooms', 'ranking', id] as const,
  blocks: ['rooms', 'blocks'] as const,
};

export function useMyRooms() {
  return useQuery({ queryKey: roomKeys.mine, queryFn: roomsApi.mine, staleTime: 60_000 });
}

export function usePublicRooms(q: string) {
  return useQuery({
    queryKey: roomKeys.public(q),
    queryFn: () => roomsApi.publicRooms(q),
    staleTime: 60_000,
    placeholderData: (prev) => prev,
  });
}

export function useRoom(id: string) {
  const tz = useTimeZone();
  const setSession = useRoomStore((s) => s.setSession);
  const query = useQuery({
    queryKey: roomKeys.detail(id),
    queryFn: () => roomsApi.detail(id, tz),
    staleTime: 30_000,
  });
  // A sessão que veio no detalhe vira a minha sessão (cronômetro).
  useEffect(() => {
    if (query.data?.session) setSession(query.data.session, query.dataUpdatedAt);
  }, [query.data, query.dataUpdatedAt, setSession]);
  return query;
}

/**
 * Presença: polling de 30 s só com a aba visível. Ao voltar a ficar visível, atualiza na hora
 * (o TanStack pausa o intervalo com a aba oculta; o efeito garante a atualização imediata).
 */
export function usePresence(id: string) {
  const visible = usePageVisible();
  const query = useQuery({
    queryKey: roomKeys.presence(id),
    queryFn: () => roomsApi.presence(id),
    refetchInterval: visible ? PRESENCE_POLL_MS : false,
    refetchIntervalInBackground: false,
    staleTime: 10_000,
  });
  const { refetch } = query;
  useEffect(() => {
    if (visible) void refetch();
  }, [visible, refetch]);
  return query;
}

export function useRanking(id: string, period: 'today' | 'week', mode: RoomMode) {
  const tz = useTimeZone();
  const visible = usePageVisible();
  return useQuery({
    queryKey: [...roomKeys.ranking(id), period, mode],
    queryFn: () => roomsApi.ranking(id, period, mode, tz),
    staleTime: 60_000,
    refetchInterval: visible ? 120_000 : false,
    refetchIntervalInBackground: false,
  });
}

export function useBlocks() {
  return useQuery({ queryKey: roomKeys.blocks, queryFn: roomsApi.blocks, staleTime: 60_000 });
}

// mutações ------------------------------------------------------------------------------

/** Grava o detalhe devolvido e atualiza as listas. */
function useApplyRoom() {
  const qc = useQueryClient();
  return (room: RoomDetail) => {
    qc.setQueryData(roomKeys.detail(room.id), room);
    void qc.invalidateQueries({ queryKey: roomKeys.mine });
    void qc.invalidateQueries({ queryKey: ['rooms', 'public'] });
  };
}

export function useCreateRoom() {
  const tz = useTimeZone();
  const apply = useApplyRoom();
  return useMutation({
    mutationFn: (input: CreateRoomInput) => roomsApi.create(input, tz),
    onSuccess: apply,
  });
}

export function useJoinRoom() {
  const tz = useTimeZone();
  const apply = useApplyRoom();
  return useMutation({
    mutationFn: ({ code, nickname }: { code: string; nickname: string }) =>
      roomsApi.join(code, nickname, tz),
    onSuccess: apply,
  });
}

export function useJoinPublic() {
  const tz = useTimeZone();
  const apply = useApplyRoom();
  return useMutation({
    mutationFn: ({ id, nickname }: { id: string; nickname: string }) =>
      roomsApi.joinPublic(id, nickname, tz),
    onSuccess: apply,
  });
}

export function useUpdateRoom(id: string) {
  const tz = useTimeZone();
  const apply = useApplyRoom();
  return useMutation({
    mutationFn: (input: UpdateRoomInput) => roomsApi.update(id, input, tz),
    onSuccess: apply,
  });
}

export function useUpdateMe(id: string) {
  const tz = useTimeZone();
  const apply = useApplyRoom();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: UpdateMeInput) => roomsApi.updateMe(id, input, tz),
    onSuccess: (room) => {
      apply(room);
      void qc.invalidateQueries({ queryKey: roomKeys.presence(id) });
      void qc.invalidateQueries({ queryKey: roomKeys.ranking(id) });
    },
  });
}

/** Ações que mudam a sala inteira (sair, apagar, remover membro...): recarrega tudo das salas. */
export function useRoomAction<T>(fn: (input: T) => Promise<unknown>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: roomKeys.all });
      void qc.invalidateQueries({ queryKey: deepWorkKeys.active });
    },
  });
}

/** Começar, pausar, retomar, confirmar e parar: a resposta é a minha sessão. */
export function useSessionControl(id: string) {
  const qc = useQueryClient();
  const setSession = useRoomStore((s) => s.setSession);
  const refresh = (stopped: boolean) => {
    void qc.invalidateQueries({ queryKey: roomKeys.presence(id) });
    void qc.invalidateQueries({ queryKey: deepWorkKeys.active });
    if (stopped) {
      void qc.invalidateQueries({ queryKey: roomKeys.detail(id) });
      void qc.invalidateQueries({ queryKey: roomKeys.ranking(id) });
      void qc.invalidateQueries({ queryKey: ['deepwork'] });
    }
  };
  const onSession = (s: RoomSession) => {
    setSession(s.state === 'OFFLINE' ? null : s);
    refresh(s.state === 'OFFLINE');
  };
  return {
    start: useMutation({
      mutationFn: (input: { mode: RoomMode; subjectId?: string; takeover?: boolean }) =>
        roomsApi.start(id, input),
      onSuccess: onSession,
    }),
    pause: useMutation({ mutationFn: () => roomsApi.pause(id), onSuccess: onSession }),
    resume: useMutation({ mutationFn: () => roomsApi.resume(id), onSuccess: onSession }),
    stop: useMutation({ mutationFn: () => roomsApi.stop(id), onSuccess: onSession }),
  };
}
