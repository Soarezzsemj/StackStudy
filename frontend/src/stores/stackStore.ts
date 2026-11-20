import { create } from 'zustand';
import { DEFAULT_FILTERS, type TaskFilters } from '@/features/stack/filters';

/** Qual diálogo do The Stack está aberto. Ids apontam para dados do TanStack Query. */
export type StackDialog =
  | { kind: 'task'; taskId?: string }
  | { kind: 'subject'; subjectId?: string }
  | { kind: 'semesters' }
  | { kind: 'semesterForm'; semesterId?: string };

interface StackState {
  filters: TaskFilters;
  selectedTaskId: string | null;
  dialog: StackDialog | null;
  setFilters: (patch: Partial<TaskFilters>) => void;
  resetFilters: () => void;
  select: (id: string | null) => void;
  openDialog: (dialog: StackDialog) => void;
  closeDialog: () => void;
}

/** Estado de UI do The Stack (filtros, seleção, diálogos). Dados do servidor ficam no Query. */
export const useStackStore = create<StackState>((set) => ({
  filters: DEFAULT_FILTERS,
  selectedTaskId: null,
  dialog: null,
  setFilters: (patch) => set((s) => ({ filters: { ...s.filters, ...patch } })),
  resetFilters: () => set({ filters: DEFAULT_FILTERS }),
  select: (selectedTaskId) => set({ selectedTaskId }),
  openDialog: (dialog) => set({ dialog }),
  closeDialog: () => set({ dialog: null }),
}));
