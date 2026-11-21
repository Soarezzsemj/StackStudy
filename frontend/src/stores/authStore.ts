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
  /**
   * Sai da sessão. 'expired' = caiu sozinha (refresh falhou com a pessoa usando o app): o login
   * mostra "Sua sessão expirou".
   */
  clear: (reason?: 'expired') => void;
  /** A última sessão terminou por expirar (não por logout). */
  sessionExpired: boolean;
}

export const useAuthStore = create<AuthState>((set) => ({
  status: 'unknown',
  accessToken: null,
  user: null,
  sessionExpired: false,
  setSession: (accessToken, user) =>
    set({ status: 'authenticated', accessToken, user, sessionExpired: false }),
  setUser: (user) => set({ user }),
  clear: (reason) =>
    set((s) => ({
      status: 'anonymous',
      accessToken: null,
      user: null,
      // Só conta como "expirou" se havia alguém logado (não no primeiro carregamento).
      sessionExpired: reason === 'expired' && s.status === 'authenticated',
    })),
}));
