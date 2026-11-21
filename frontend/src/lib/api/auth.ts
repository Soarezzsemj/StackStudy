import { apiRequest, refreshSession } from './client';
import { wakeServer } from './wake';
import type { AuthResponse, User } from './types';
import { useAuthStore } from '@/stores/authStore';

export interface LoginInput {
  email: string;
  password: string;
}

export type StudyArea =
  | 'TECH_EXACT'
  | 'HEALTH'
  | 'LAW'
  | 'HUMANITIES'
  | 'BUSINESS'
  | 'ARTS_DESIGN'
  | 'HIGH_SCHOOL'
  | 'OTHER';

export type FocusRhythm = 'POMODORO_25_5' | 'POMODORO_50_10' | 'FREE';

/** Cadastro em 3 passos, enviado de uma vez no fim. */
export interface SignupInput extends LoginInput {
  name: string;
  nickname: string;
  area: StudyArea;
  course?: string;
  period?: string;
  institution?: string;
  weeklyGoalHours: number;
  focusRhythm: FocusRhythm;
  deadlineReminders: boolean;
  followRoadmap?: string;
  roomsVisible: boolean;
  acceptTerms: boolean;
  confirmAge: boolean;
  locale: string;
}

/** Sessão aberta ou "verifique seu e-mail" (com a verificação ligada). */
export type SignupResult = { kind: 'session'; user: User } | { kind: 'verify' };

export interface SignupConfig {
  googleEnabled: boolean;
  emailVerificationEnabled: boolean;
  profilePhotoEnabled: boolean;
  minAge?: number;
  termsVersion: string;
  privacyVersion: string;
}

export interface RoadmapSuggestion {
  slug: string;
  title: string;
  area: string;
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

  async register(input: SignupInput): Promise<SignupResult> {
    const data = await apiRequest<AuthResponse | { status: 'VERIFICATION_SENT' }>(
      '/auth/register',
      { method: 'POST', body: input, auth: false },
    );
    if ('status' in data) return { kind: 'verify' };
    useAuthStore.getState().setSession(data.accessToken, data.user);
    return { kind: 'session', user: data.user };
  },

  async verifyEmail(token: string): Promise<User> {
    const data = await apiRequest<AuthResponse>('/auth/verify-email', {
      method: 'POST',
      body: { token },
      auth: false,
    });
    useAuthStore.getState().setSession(data.accessToken, data.user);
    return data.user;
  },

  /** Flags e versões do cadastro (público). */
  config: () => apiRequest<SignupConfig>('/auth/config', { auth: false }),

  /** Roadmap sugerido para a área e o curso (público); sem sugestão, null. */
  async suggestRoadmap(area: string, course: string): Promise<RoadmapSuggestion | null> {
    const q = new URLSearchParams({ area, course }).toString();
    const data = await apiRequest<RoadmapSuggestion | undefined>('/roadmaps/suggest?' + q, {
      auth: false,
    });
    return data ?? null;
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
