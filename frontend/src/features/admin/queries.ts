import { useQuery } from '@tanstack/react-query';
import { adminApi, type AdminRange } from '@/lib/api/admin';
import { useAuthStore } from '@/stores/authStore';

/** Atualização automática: a cada 60 s, só com a aba visível. */
export const ADMIN_REFRESH_MS = 60_000;

export const adminKeys = {
  all: ['admin'] as const,
  overview: (range: AdminRange) => ['admin', 'overview', range] as const,
  health: ['admin', 'health'] as const,
  signups: ['admin', 'signups'] as const,
  errors: ['admin', 'errors'] as const,
};

/**
 * Opções comuns. `refetchIntervalInBackground: false` (padrão do TanStack, explícito aqui) pausa o
 * intervalo com a aba escondida: o painel aberto num canto não mantém o Render e o Neon acordados.
 * Sem retry em 403 (o cliente já não repete 4xx).
 */
function useAdminQueryOptions() {
  const isAdmin = useAuthStore((s) => s.status === 'authenticated' && s.user?.role === 'ADMIN');
  return {
    enabled: isAdmin,
    refetchInterval: ADMIN_REFRESH_MS,
    refetchIntervalInBackground: false,
    staleTime: 30_000,
  } as const;
}

export function useAdminOverview(range: AdminRange) {
  return useQuery({
    queryKey: adminKeys.overview(range),
    queryFn: () => adminApi.overview(range),
    ...useAdminQueryOptions(),
  });
}

export function useAdminHealth() {
  return useQuery({
    queryKey: adminKeys.health,
    queryFn: adminApi.health,
    ...useAdminQueryOptions(),
  });
}

export function useAdminSignups() {
  return useQuery({
    queryKey: adminKeys.signups,
    queryFn: adminApi.signups,
    ...useAdminQueryOptions(),
  });
}

export function useAdminErrors() {
  return useQuery({
    queryKey: adminKeys.errors,
    queryFn: adminApi.errors,
    ...useAdminQueryOptions(),
  });
}
