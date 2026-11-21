import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { changeLanguage, isLanguage } from '@/i18n';
import {
  accountApi,
  type Preferences,
  type PreferencesPatch,
  type ProfileInput,
} from '@/lib/api/account';
import type { User } from '@/lib/api/types';
import { useAuthStore } from '@/stores/authStore';

export const profileKeys = {
  preferences: ['me', 'preferences'] as const,
  sessions: ['me', 'sessions'] as const,
};

function useIsAuthenticated() {
  return useAuthStore((s) => s.status === 'authenticated');
}

function setUser(user: User) {
  useAuthStore.getState().setUser(user);
}

export function useSaveProfile() {
  return useMutation({
    mutationFn: (input: ProfileInput) => accountApi.updateProfile(input),
    onSuccess: setUser,
  });
}

export function usePreferences() {
  return useQuery({
    queryKey: profileKeys.preferences,
    queryFn: () => accountApi.preferences(),
    enabled: useIsAuthenticated(),
  });
}

/**
 * Salva as preferências. Idioma e fuso entram no usuário logado na hora; o resto vive nos
 * settings de cada fase, então as telas delas recarregam (tudo é invalidado).
 */
export function useSavePreferences() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (patch: PreferencesPatch) => accountApi.updatePreferences(patch),
    onSuccess: (prefs: Preferences) => {
      const user = useAuthStore.getState().user;
      if (user) setUser({ ...user, locale: prefs.locale, timeZone: prefs.timeZone });
      if (isLanguage(prefs.locale)) void changeLanguage(prefs.locale);
      qc.setQueryData(profileKeys.preferences, prefs);
      void qc.invalidateQueries({
        predicate: (q) => q.queryKey[0] !== 'me',
      });
    },
  });
}

export function useChangeEmail() {
  return useMutation({
    mutationFn: ({ email, currentPassword }: { email: string; currentPassword: string }) =>
      accountApi.changeEmail(email, currentPassword),
    onSuccess: setUser,
  });
}

export function useChangePassword() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      currentPassword,
      newPassword,
    }: {
      currentPassword: string;
      newPassword: string;
    }) => accountApi.changePassword(currentPassword, newPassword),
    // As outras sessões foram encerradas.
    onSuccess: () => void qc.invalidateQueries({ queryKey: profileKeys.sessions }),
  });
}

export function useSessions() {
  return useQuery({
    queryKey: profileKeys.sessions,
    queryFn: () => accountApi.sessions(),
    enabled: useIsAuthenticated(),
  });
}

export function useEndSession() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => accountApi.endSession(id),
    onSuccess: () => void qc.invalidateQueries({ queryKey: profileKeys.sessions }),
  });
}

export function useEndOtherSessions() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => accountApi.endOtherSessions(),
    onSuccess: () => void qc.invalidateQueries({ queryKey: profileKeys.sessions }),
  });
}

/** Sair de todos os dispositivos: este também, então a sessão local é limpa. */
export function useEndAllSessions() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => accountApi.endAllSessions(),
    onSuccess: () => {
      useAuthStore.getState().clear();
      qc.clear();
    },
  });
}

export function useExportData() {
  return useMutation({ mutationFn: () => accountApi.exportData() });
}

export function useDeleteAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ email, currentPassword }: { email: string; currentPassword: string }) =>
      accountApi.deleteAccount(email, currentPassword),
    onSuccess: () => {
      useAuthStore.getState().clear();
      qc.clear();
    },
  });
}
