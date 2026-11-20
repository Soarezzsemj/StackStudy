import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type InfiniteData,
  type QueryClient,
} from '@tanstack/react-query';
import {
  stackApi,
  type Page,
  type SemesterInput,
  type SubjectInput,
  type Task,
  type TaskInput,
  type TaskStatus,
} from '@/lib/api/stack';
import { useAuthStore } from '@/stores/authStore';
import { buildTaskQuery, type TaskFilters } from './filters';

export const stackKeys = {
  semesters: ['semesters'] as const,
  allSubjects: ['subjects'] as const,
  tasks: (filters: TaskFilters) => ['tasks', filters] as const,
  allTasks: ['tasks'] as const,
};

function useIsAuthenticated() {
  return useAuthStore((s) => s.status === 'authenticated');
}

export function useSemesters() {
  return useQuery({
    queryKey: stackKeys.semesters,
    queryFn: () => stackApi.semesters(),
    enabled: useIsAuthenticated(),
  });
}

/** Semestre atual (ou undefined se ainda não houver). */
export function useCurrentSemester() {
  const query = useSemesters();
  return { ...query, current: query.data?.find((s) => s.current) };
}

/**
 * Todas as matérias do usuário numa só consulta (com pendentes). A tela filtra pelo semestre no
 * cliente; assim tarefas de semestres antigos ainda mostram o nome e a cor da matéria.
 */
export function useSubjects() {
  return useQuery({
    queryKey: stackKeys.allSubjects,
    queryFn: () => stackApi.subjects(),
    enabled: useIsAuthenticated(),
  });
}

/** Matérias do semestre atual. */
export function useCurrentSubjects() {
  const semesters = useCurrentSemester();
  const subjects = useSubjects();
  const currentId = semesters.current?.id;
  return {
    semesters,
    subjects,
    current: semesters.current,
    list: currentId ? (subjects.data ?? []).filter((s) => s.semesterId === currentId) : [],
  };
}

/** Lista paginada; a tela carrega mais páginas sob demanda. */
export function useTasks(filters: TaskFilters) {
  return useInfiniteQuery({
    queryKey: stackKeys.tasks(filters),
    queryFn: ({ pageParam, signal }) => stackApi.tasks(buildTaskQuery(filters, pageParam), signal),
    initialPageParam: 0,
    getNextPageParam: (last: Page<Task>) => (last.hasNext ? last.page + 1 : undefined),
    enabled: useIsAuthenticated(),
  });
}

/** Tarefas mudaram: atualiza listas e as contagens de pendentes das matérias. */
function invalidateTasks(qc: QueryClient) {
  return Promise.all([
    qc.invalidateQueries({ queryKey: stackKeys.allTasks }),
    qc.invalidateQueries({ queryKey: stackKeys.allSubjects }),
  ]);
}

export function useSaveTask() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: TaskInput }) =>
      id ? stackApi.updateTask(id, input) : stackApi.createTask(input),
    onSuccess: () => invalidateTasks(qc),
  });
}

/** Troca o status na hora (otimista) e confirma com o servidor. */
export function useSetTaskStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: TaskStatus }) =>
      stackApi.setTaskStatus(id, status),
    onMutate: async ({ id, status }) => {
      await qc.cancelQueries({ queryKey: stackKeys.allTasks });
      const snapshot = qc.getQueriesData<InfiniteData<Page<Task>>>({
        queryKey: stackKeys.allTasks,
      });
      qc.setQueriesData<InfiniteData<Page<Task>>>({ queryKey: stackKeys.allTasks }, (data) =>
        data
          ? {
              ...data,
              pages: data.pages.map((p) => ({
                ...p,
                items: p.items.map((t) => (t.id === id ? { ...t, status } : t)),
              })),
            }
          : data,
      );
      return { snapshot };
    },
    onError: (_err, _vars, ctx) => {
      ctx?.snapshot.forEach(([key, data]) => qc.setQueryData<InfiniteData<Page<Task>>>(key, data));
    },
    onSettled: () => invalidateTasks(qc),
  });
}

export function useDeleteTask() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => stackApi.deleteTask(id),
    onSuccess: () => invalidateTasks(qc),
  });
}

export function useSaveSubject() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: SubjectInput }) =>
      id ? stackApi.updateSubject(id, input) : stackApi.createSubject(input),
    onSuccess: () => qc.invalidateQueries({ queryKey: stackKeys.allSubjects }),
  });
}

export function useDeleteSubject() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => stackApi.deleteSubject(id),
    // As tarefas da matéria somem junto (cascade no banco).
    onSuccess: () => invalidateTasks(qc),
  });
}

export function useSaveSemester() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: SemesterInput }) =>
      id ? stackApi.updateSemester(id, input) : stackApi.createSemester(input),
    onSuccess: () => qc.invalidateQueries({ queryKey: stackKeys.semesters }),
  });
}

export type SemesterAction = 'archive' | 'unarchive' | 'current';

export function useSemesterAction() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, action }: { id: string; action: SemesterAction }) =>
      action === 'archive'
        ? stackApi.archiveSemester(id)
        : action === 'unarchive'
          ? stackApi.unarchiveSemester(id)
          : stackApi.makeCurrentSemester(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: stackKeys.semesters }),
  });
}
