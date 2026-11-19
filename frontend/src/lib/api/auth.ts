import { apiRequest, refreshSession } from './client';
import { wakeServer } from './wake';
import type { AuthResponse, User } from './types';
import { useAuthStore } from '@/stores/authStore';

export interface LoginInput {
  email: string;
  password: string;
}

export interface RegisterInput extends LoginInput {
  name: string;
  locale: string;
}

export const authApi = {
  async login(input: LoginInput): Promise<User> {
    const data = await apiRequest<AuthResponse>('/auth/login', {
      method: 'POST',
      body: input,
      auth: false,
    });
    useAuthStore.getState().setSession(data.accessToken, data.user);
    return data.user;
  },

  async register(input: RegisterInput): Promise<User> {
    const data = await apiRequest<AuthResponse>('/auth/register', {
      method: 'POST',
      body: input,
      auth: false,
    });
    useAuthStore.getState().setSession(data.accessToken, data.user);
    return data.user;
  },

  async logout(): Promise<void> {
    try {
      await apiRequest<void>('/auth/logout', { method: 'POST', auth: false });
    } finally {
      useAuthStore.getState().clear();
    }
  },

  me: () => apiRequest<User>('/me'),

  /**
   * Restaura a sessão ao carregar a página, usando o cookie de refresh.
   * Antes, espera o wake-up ping (se configurado): assim o refresh não cai num backend dormindo
   * nem estoura o tempo do rewrite, o que deslogaria o usuário à toa.
   */
  async bootstrap() {
    await wakeServer();
    return refreshSession();
  },
};
