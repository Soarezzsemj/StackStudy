import { create } from 'zustand';
import type { User } from '@/lib/api/types';

export type AuthStatus = 'unknown' | 'authenticated' | 'anonymous';

interface AuthState {
  status: AuthStatus;
  /** Access token só em memória; o refresh token fica num cookie HttpOnly. */
  accessToken: string | null;
  user: User | null;
  setSession: (accessToken: string, user: User) => void;
  /** Atualiza o perfil sem mexer no token (Perfil e preferências). */
  setUser: (user: User) => void;
  clear: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  status: 'unknown',
  accessToken: null,
  user: null,
  setSession: (accessToken, user) => set({ status: 'authenticated', accessToken, user }),
  setUser: (user) => set({ user }),
  clear: () => set({ status: 'anonymous', accessToken: null, user: null }),
}));
