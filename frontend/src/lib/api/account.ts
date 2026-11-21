import { api, apiRequest } from './client';
import type { GradeMethod } from './grades';
import type { AuthResponse, User } from './types';
import { useAuthStore } from '@/stores/authStore';

// Perfil, preferências e conta (`/api/me/...`). Campos null do backend vêm omitidos.

export interface ProfileInput {
  name: string;
  course: string;
  institution: string;
  currentPeriod: string;
  bio: string;
  avatarColor: string | null;
}

/** Preferências de todas as fases numa resposta (fonte: os settings de cada uma). */
export interface Preferences {
  locale: string;
  /** Ausente = usa o fuso do navegador. */
  timeZone?: string;
  gradeMethod: GradeMethod;
  gradeScaleMax: number;
  passingGrade: number;
  maxAbsencePct: number;
  focusMinutes: number;
  shortBreakMinutes: number;
  longBreakMinutes: number;
  longBreakEvery: number;
  weeklyGoalMinutes: number;
  newCardsPerDay: number;
  reviewsPerDay: number;
}

/** Só o que mudou; `timeZone: ''` volta ao fuso do navegador. */
export type PreferencesPatch = Partial<Omit<Preferences, 'timeZone'>> & { timeZone?: string };

export interface SessionView {
  id: string;
  userAgent?: string;
  createdAt: string;
  lastUsedAt: string;
  current: boolean;
}

export const accountApi = {
  updateProfile: (input: ProfileInput) => api.put<User>('/me/profile', input),
  preferences: () => api.get<Preferences>('/me/preferences'),
  updatePreferences: (patch: PreferencesPatch) =>
    apiRequest<Preferences>('/me/preferences', { method: 'PATCH', body: patch }),
  changeEmail: (email: string, currentPassword: string) =>
    api.put<User>('/me/email', { email, currentPassword }),

  /** Troca a senha; o backend devolve tokens novos desta sessão (e o cookie novo). */
  async changePassword(currentPassword: string, newPassword: string): Promise<User> {
    const data = await api.put<AuthResponse>('/me/password', { currentPassword, newPassword });
    useAuthStore.getState().setSession(data.accessToken, data.user);
    return data.user;
  },

  sessions: () => api.get<SessionView[]>('/me/sessions'),
  endSession: (id: string) => api.delete<void>(`/me/sessions/${id}`),
  endOtherSessions: () => api.delete<void>('/me/sessions/others'),
  endAllSessions: () => api.delete<void>('/me/sessions'),
  exportData: () => api.get<Record<string, unknown>>('/me/export'),
  deleteAccount: (email: string, currentPassword: string) =>
    apiRequest<void>('/me', { method: 'DELETE', body: { email, currentPassword } }),
};
