import { create } from 'zustand';
import type { Card } from '@/lib/api/flashcards';

/** Diálogo aberto na tela de Flashcards (também aberto pela paleta Ctrl K). */
export type FlashcardDialog =
  | { kind: 'card'; card?: Card; deckId?: string }
  | { kind: 'deck'; deckId?: string }
  | { kind: 'settings' }
  | { kind: 'import'; deckId: string }
  | { kind: 'deleteDeck'; deckId: string }
  | null;

interface FlashcardState {
  dialog: FlashcardDialog;
  /** Último deck usado na criação rápida (sugestão para a próxima carta). */
  lastDeckId: string | null;
  openDialog: (dialog: Exclude<FlashcardDialog, null>) => void;
  closeDialog: () => void;
  setLastDeckId: (id: string) => void;
}

export const useFlashcardStore = create<FlashcardState>((set) => ({
  dialog: null,
  lastDeckId: null,
  openDialog: (dialog) => set({ dialog }),
  closeDialog: () => set({ dialog: null }),
  setLastDeckId: (lastDeckId) => set({ lastDeckId }),
}));
