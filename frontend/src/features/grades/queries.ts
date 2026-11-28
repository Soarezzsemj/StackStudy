import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  gradesApi,
  type AbsenceInput,
  type AssessmentInput,
  type GradeSettings,
  type SubjectRuleInput,
} from '@/lib/api/grades';
import { useAuthStore } from '@/stores/authStore';

export const gradeKeys = {
  all: ['grades'] as const,
  /** `current` = semestre atual do usuário. */
  overview: (semesterId: string | undefined) =>
    ['grades', 'overview', semesterId ?? 'current'] as const,
  absences: (subjectId: string) => ['grades', 'absences', subjectId] as const,
};

function useIsAuthenticated() {
  return useAuthStore((s) => s.status === 'authenticated');
}

/** Tela inteira numa chamada: matérias do semestre com notas, faltas e o CR. */
export function useGradesOverview(semesterId?: string, opts: { background?: boolean } = {}) {
  return useQuery({
    queryKey: gradeKeys.overview(semesterId),
    queryFn: () => gradesApi.overview(semesterId),
    enabled: useIsAuthenticated(),
    // Trocar de semestre mantém a tela anterior até a nova chegar.
    placeholderData: keepPreviousData,
    // Barra lateral (em todas as telas): não refaz a consulta por foco nem a cada 30 s. Salvar
    // nota, regra ou falta invalida o cache e atualiza na hora.
    ...(opts.background ? { staleTime: 5 * 60_000, refetchOnWindowFocus: false } : {}),
  });
}

export function useAbsences(subjectId: string | undefined) {
  return useQuery({
    queryKey: gradeKeys.absences(subjectId ?? ''),
    queryFn: () => gradesApi.absences(subjectId!),
    enabled: useIsAuthenticated() && !!subjectId,
  });
}

/** Qualquer alteração de nota muda a visão geral, o CR e a lista de matérias da Stack. */
function useInvalidateGrades() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: gradeKeys.all });
}

export function useSaveGradeSettings() {
  const invalidate = useInvalidateGrades();
  return useMutation({
    mutationFn: (input: GradeSettings) => gradesApi.updateSettings(input),
    onSuccess: invalidate,
  });
}

export function useSaveRule() {
  const invalidate = useInvalidateGrades();
  return useMutation({
    mutationFn: ({ subjectId, input }: { subjectId: string; input: SubjectRuleInput }) =>
      gradesApi.updateRule(subjectId, input),
    onSuccess: invalidate,
  });
}

export function useSaveAssessments() {
  const invalidate = useInvalidateGrades();
  return useMutation({
    mutationFn: ({ subjectId, items }: { subjectId: string; items: AssessmentInput[] }) =>
      gradesApi.replaceAssessments(subjectId, items),
    onSuccess: invalidate,
  });
}

export function useCreateAbsence() {
  const invalidate = useInvalidateGrades();
  return useMutation({
    mutationFn: ({ subjectId, input }: { subjectId: string; input: AbsenceInput }) =>
      gradesApi.createAbsence(subjectId, input),
    onSuccess: invalidate,
  });
}

export function useDeleteAbsence() {
  const invalidate = useInvalidateGrades();
  return useMutation({
    mutationFn: (id: string) => gradesApi.deleteAbsence(id),
    onSuccess: invalidate,
  });
}
