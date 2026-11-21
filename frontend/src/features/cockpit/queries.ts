import { useQuery } from '@tanstack/react-query';
import { cockpitApi } from '@/lib/api/cockpit';
import { useTimeZone } from '@/lib/useTimeZone';
import { useAuthStore } from '@/stores/authStore';

export const cockpitKeys = {
  all: ['cockpit'] as const,
  get: (tz: string) => ['cockpit', tz] as const,
};

/**
 * Tela inicial numa chamada só. Volta a buscar ao abrir a tela (o que foi feito nas outras telas
 * aparece), mas mostra o que já tem enquanto isso: sem esqueleto piscando.
 */
export function useCockpit() {
  const tz = useTimeZone();
  return useQuery({
    queryKey: cockpitKeys.get(tz),
    queryFn: () => cockpitApi.get(tz),
    enabled: useAuthStore((s) => s.status === 'authenticated'),
  });
}
