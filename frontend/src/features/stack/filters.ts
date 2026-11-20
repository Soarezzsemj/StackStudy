import type { TaskStatus, TaskType } from '@/lib/api/stack';

/** "open" = a fazer + em andamento (padrão da tela). */
export type StatusFilter = 'open' | 'all' | TaskStatus;

export interface TaskFilters {
  /** "all", "none" (sem matéria) ou o id da matéria. */
  subject: string;
  type: 'all' | TaskType;
  status: StatusFilter;
  q: string;
}

export const DEFAULT_FILTERS: TaskFilters = { subject: 'all', type: 'all', status: 'open', q: '' };

export const PAGE_SIZE = 100;

/** Monta a query string de `GET /api/tasks` a partir dos filtros da tela. */
export function buildTaskQuery(filters: TaskFilters, page = 0, size = PAGE_SIZE): string {
  const params = new URLSearchParams();
  if (filters.subject === 'none') params.set('withoutSubject', 'true');
  else if (filters.subject !== 'all') params.set('subjectId', filters.subject);
  if (filters.type !== 'all') params.set('type', filters.type);
  if (filters.status === 'open') params.set('status', 'TODO,DOING');
  else if (filters.status !== 'all') params.set('status', filters.status);
  const q = filters.q.trim();
  if (q) params.set('q', q.slice(0, 100));
  params.set('page', String(page));
  params.set('size', String(size));
  return params.toString();
}

/** Algum filtro diferente do padrão? (para o estado vazio oferecer "limpar filtros") */
export function hasActiveFilters(filters: TaskFilters): boolean {
  return (
    filters.subject !== DEFAULT_FILTERS.subject ||
    filters.type !== DEFAULT_FILTERS.type ||
    filters.status !== DEFAULT_FILTERS.status ||
    filters.q.trim() !== ''
  );
}
