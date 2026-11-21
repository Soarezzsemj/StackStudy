import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  roadmapsApi,
  type ProgressInput,
  type RoadmapDetail,
  type SuggestionInput,
} from '@/lib/api/roadmaps';

// O conteúdo é estático (muda só com deploy); o progresso muda pelas mutações abaixo, que já
// devolvem o roadmap inteiro. Por isso um staleTime longo.
const STALE = 5 * 60_000;

export const roadmapKeys = {
  all: ['roadmaps'] as const,
  list: (area: string, q: string) => ['roadmaps', 'list', area, q] as const,
  detail: (slug: string) => ['roadmaps', 'detail', slug] as const,
};

export function useRoadmapList(area = '', q = '') {
  return useQuery({
    queryKey: roadmapKeys.list(area, q),
    queryFn: () => roadmapsApi.list({ area, q }),
    staleTime: STALE,
    placeholderData: (prev) => prev,
  });
}

export function useRoadmapDetail(slug: string | undefined) {
  return useQuery({
    queryKey: roadmapKeys.detail(slug ?? ''),
    queryFn: () => roadmapsApi.detail(slug!),
    enabled: !!slug,
    staleTime: STALE,
  });
}

/** Grava o roadmap devolvido e invalida a lista e o Cockpit (percentual, seguindo). */
function useApplyDetail() {
  const qc = useQueryClient();
  return (detail: RoadmapDetail) => {
    qc.setQueryData(roadmapKeys.detail(detail.slug), detail);
    void qc.invalidateQueries({ queryKey: ['roadmaps', 'list'] });
    void qc.invalidateQueries({ queryKey: ['cockpit'] });
  };
}

export function useFollowRoadmap() {
  const apply = useApplyDetail();
  return useMutation({
    mutationFn: ({ slug, primary }: { slug: string; primary?: boolean }) =>
      roadmapsApi.follow(slug, primary),
    onSuccess: apply,
  });
}

export function useUnfollowRoadmap() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (slug: string) => roadmapsApi.unfollow(slug),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: roadmapKeys.all });
      void qc.invalidateQueries({ queryKey: ['cockpit'] });
    },
  });
}

export function useNodeProgress(slug: string) {
  const apply = useApplyDetail();
  return useMutation({
    mutationFn: ({ nodeId, input }: { nodeId: string; input: ProgressInput }) =>
      roadmapsApi.progress(slug, nodeId, input),
    onSuccess: apply,
  });
}

export function useRoadmapGoal(slug: string) {
  const apply = useApplyDetail();
  return useMutation({
    mutationFn: (hours: number) => roadmapsApi.goal(slug, hours),
    onSuccess: apply,
  });
}

export function useSuggestRoadmap() {
  return useMutation({ mutationFn: (input: SuggestionInput) => roadmapsApi.suggest(input) });
}
