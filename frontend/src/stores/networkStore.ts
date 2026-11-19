import { create } from 'zustand';

interface NetworkState {
  /** Quantas requisições passaram do tempo de "lenta" e ainda não terminaram. */
  slowRequests: number;
  slowStarted: () => void;
  slowEnded: () => void;
}

export const useNetworkStore = create<NetworkState>((set) => ({
  slowRequests: 0,
  slowStarted: () => set((s) => ({ slowRequests: s.slowRequests + 1 })),
  slowEnded: () => set((s) => ({ slowRequests: Math.max(0, s.slowRequests - 1) })),
}));
