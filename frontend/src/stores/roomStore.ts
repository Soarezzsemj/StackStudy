import { create } from 'zustand';
import type { RoomSession } from '@/lib/api/rooms';
import { clockOffset } from '@/features/rooms/roomLogic';

/**
 * Minha sessão de sala (uma por pessoa), com a diferença de relógio do servidor. Atualizada pelas
 * respostas do servidor (detalhe da sala, start/pause/resume/stop, heartbeats); o cronômetro na
 * tela é calculado a partir daqui.
 */
interface RoomState {
  session: RoomSession | null;
  offsetMs: number;
  /** Quando a sessão chegou (para não ser apagada por uma resposta mais antiga). */
  receivedAt: number;
  setSession: (session: RoomSession | null, receivedAt?: number) => void;
}

export const useRoomStore = create<RoomState>((set) => ({
  session: null,
  offsetMs: 0,
  receivedAt: 0,
  setSession: (session, receivedAt = Date.now()) =>
    set(
      session
        ? { session, offsetMs: clockOffset(session.serverTime, receivedAt), receivedAt }
        : { session: null, receivedAt },
    ),
}));
