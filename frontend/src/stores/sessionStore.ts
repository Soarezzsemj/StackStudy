import { create } from 'zustand';

/**
 * Sessão de Deep Work ativa, mostrada na status bar em todas as telas.
 * A Fase 2 preenche pelo backend (o started_at fica no servidor, então sobrevive a fechar a aba).
 */
export interface ActiveSession {
  id: string;
  subjectName: string;
  subjectColor: string;
  startedAt: string;
  plannedMinutes: number | null;
}

interface SessionState {
  active: ActiveSession | null;
  setActive: (session: ActiveSession | null) => void;
}

export const useSessionStore = create<SessionState>((set) => ({
  active: null,
  setActive: (active) => set({ active }),
}));
