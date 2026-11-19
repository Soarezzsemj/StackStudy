import { create } from 'zustand';

interface UiState {
  paletteOpen: boolean;
  mobileNavOpen: boolean;
  setPaletteOpen: (open: boolean) => void;
  togglePalette: () => void;
  setMobileNavOpen: (open: boolean) => void;
}

export const useUiStore = create<UiState>((set) => ({
  paletteOpen: false,
  mobileNavOpen: false,
  setPaletteOpen: (paletteOpen) => set({ paletteOpen }),
  togglePalette: () => set((s) => ({ paletteOpen: !s.paletteOpen })),
  setMobileNavOpen: (mobileNavOpen) => set({ mobileNavOpen }),
}));
