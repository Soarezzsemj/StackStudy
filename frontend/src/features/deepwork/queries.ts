import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTimeZone } from '@/lib/useTimeZone';
import {
  deepWorkApi,
  withOffset,
  type ActiveSession,
  type CompleteInput,
  type DeepWorkSettings,
  type ManualInput,
  type StartInput,
  type StudySession,
} from '@/lib/api/deepwork';
import type { Page } from '@/lib/api/stack';
import { useAuthStore } from '@/stores/authStore';

export const deepWorkKeys = {
  all: ['deepwork'] as const,
  active: ['deepwork', 'active'] as const,
  settings: ['deepwork', 'settings'] as const,
  stats: (tz: string) => ['deepwork', 'stats', tz] as const,
  heatmap: (tz: string) => ['deepwork', 'heatmap', tz] as const,
  history: ['deepwork', 'history'] as const,
};

function useIsAuthenticated() {
  return useAuthStore((s) => s.status === 'authenticated');
}

/**
 * Sessão ativa (ou null). Volta a consultar ao focar a janela, para outra aba ou outro aparelho
 * ficarem em dia.
 */
export function useActiveSession() {
  return useQuery({
    queryKey: deepWorkKeys.active,
    queryFn: async (): Promise<ActiveSession | null> => {
      const session = await deepWorkApi.active();
      return session ? withOffset(session) : null;
    },
    enabled: useIsAuthenticated(),
    staleTime: 15_000,
    refetchOnWindowFocus: true,
  });
}

export function useDeepWorkSettings() {
  return useQuery({
    queryKey: deepWorkKeys.settings,
    queryFn: () => deepWorkApi.settings(),
    enabled: useIsAuthenticated(),
    staleTime: 5 * 60_000,
  });
}

export function useStats() {
  const tz = useTimeZone();
  return useQuery({
    queryKey: deepWorkKeys.stats(tz),
    queryFn: () => deepWorkApi.stats(tz),
    enabled: useIsAuthenticated(),
  });
}

export function useHeatmap() {
  const tz = useTimeZone();
  return useQuery({
    queryKey: deepWorkKeys.heatmap(tz),
    queryFn: () => deepWorkApi.heatmap(tz),
    enabled: useIsAuthenticated(),
    staleTime: 5 * 60_000,
  });
}

export function useHistory() {
  return useInfiniteQuery({
    queryKey: deepWorkKeys.history,
    queryFn: ({ pageParam }) => deepWorkApi.history(pageParam),
    initialPageParam: 0,
    getNextPageParam: (last: Page<StudySession>) => (last.hasNext ? last.page + 1 : undefined),
    enabled: useIsAuthenticated(),
  });
}

/** Ações sobre a sessão ativa. A resposta já traz o estado novo (e o relógio do servidor). */
export function useSessionActions() {
  const qc = useQueryClient();
  const setActive = (s: StudySession) =>
    qc.setQueryData<ActiveSession | null>(deepWorkKeys.active, s.active ? withOffset(s) : null);
  // Sessão encerrada muda estatísticas, heatmap e histórico.
  const refreshTotals = () =>
    Promise.all([
      qc.invalidateQueries({ queryKey: ['deepwork', 'stats'] }),
      qc.invalidateQueries({ queryKey: ['deepwork', 'heatmap'] }),
      qc.invalidateQueries({ queryKey: deepWorkKeys.history }),
      // Horas de foco somam no tópico de roadmap ligado.
      qc.invalidateQueries({ queryKey: ['roadmaps'] }),
    ]);
  // Conflito (outra aba já mexeu): busca o estado real.
  const resync = () => qc.invalidateQueries({ queryKey: deepWorkKeys.active });

  return {
    start: useMutation({
      mutationFn: (input: StartInput) => deepWorkApi.start(input),
      onSuccess: setActive,
      onError: resync,
    }),
    pause: useMutation({
      mutationFn: (id: string) => deepWorkApi.pause(id),
      onSuccess: setActive,
      onError: resync,
    }),
    resume: useMutation({
      mutationFn: (id: string) => deepWorkApi.resume(id),
      onSuccess: setActive,
      onError: resync,
    }),
    complete: useMutation({
      mutationFn: ({ id, input }: { id: string; input?: CompleteInput }) =>
        deepWorkApi.complete(id, input),
      onSuccess: (s) => {
        setActive(s);
        void refreshTotals();
      },
      onError: resync,
    }),
    cancel: useMutation({
      mutationFn: (id: string) => deepWorkApi.cancel(id),
      onSuccess: () => qc.setQueryData(deepWorkKeys.active, null),
      onError: resync,
    }),
  };
}

export function useSaveManualSession() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: ManualInput }) =>
      id ? deepWorkApi.update(id, input) : deepWorkApi.createManual(input),
    onSuccess: () =>
      Promise.all([
        qc.invalidateQueries({ queryKey: deepWorkKeys.all }),
        qc.invalidateQueries({ queryKey: ['roadmaps'] }),
      ]),
  });
}

export function useDeleteSession() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deepWorkApi.remove(id),
    onSuccess: () =>
      Promise.all([
        qc.invalidateQueries({ queryKey: deepWorkKeys.all }),
        qc.invalidateQueries({ queryKey: ['roadmaps'] }),
      ]),
  });
}

export function useSaveSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: DeepWorkSettings) => deepWorkApi.saveSettings(input),
    onSuccess: (saved) => {
      qc.setQueryData(deepWorkKeys.settings, saved);
      void qc.invalidateQueries({ queryKey: ['deepwork', 'stats'] });
    },
  });
}
