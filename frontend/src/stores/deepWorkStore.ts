import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';
import type { SessionMode } from '@/lib/api/deepwork';
import type { BreakState } from '@/features/deepwork/pomodoro';

/**
 * Estado de UI do Deep Work. Fica no localStorage (só conveniência deste navegador): modo e matéria
 * escolhidos, focos concluídos no ciclo e a pausa em andamento (com timestamp, sobrevive a recarregar).
 * A sessão de foco em si é do servidor.
 */
/** Tópico de roadmap escolhido no "Tópico atual" para a próxima sessão. */
export interface FocusTopic {
  id: string;
  title: string;
  slug: string;
}

interface DeepWorkState {
  mode: SessionMode;
  subjectId: string;
  taskId: string;
  completedFocus: number;
  breakState: BreakState | null;
  topic: FocusTopic | null;
  setTopic: (topic: FocusTopic | null) => void;
  setMode: (mode: SessionMode) => void;
  setSubject: (subjectId: string) => void;
  setTask: (taskId: string) => void;
  focusCompleted: (next: BreakState | null) => void;
  setBreak: (state: BreakState | null) => void;
  resetCycle: () => void;
}

export const useDeepWorkStore = create<DeepWorkState>()(
  persist(
    (set) => ({
      mode: 'POMODORO',
      subjectId: '',
      taskId: '',
      completedFocus: 0,
      breakState: null,
      topic: null,
      setTopic: (topic) => set({ topic }),
      setMode: (mode) => set({ mode }),
      setSubject: (subjectId) => set({ subjectId, taskId: '' }),
      setTask: (taskId) => set({ taskId }),
      focusCompleted: (breakState) =>
        set((s) => ({ completedFocus: s.completedFocus + 1, breakState })),
      setBreak: (breakState) => set({ breakState }),
      resetCycle: () => set({ completedFocus: 0, breakState: null }),
    }),
    {
      name: 'stackstudy.deepwork',
      storage: createJSONStorage(() => localStorage),
    },
  ),
);
