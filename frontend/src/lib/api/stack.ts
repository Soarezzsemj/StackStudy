import { api } from './client';

// Tipos e chamadas do The Stack: semestres, matérias e tarefas.

export const SUBJECT_COLORS = [
  '#6aa7ff',
  '#f2b861',
  '#bd6cf7',
  '#5ad1e6',
  '#ff7a90',
  '#cdfb50',
] as const;
export const SUBJECT_STATUSES = ['IN_PROGRESS', 'PASSED', 'FAILED', 'DROPPED'] as const;
export const TASK_TYPES = [
  'EXAM',
  'ASSIGNMENT',
  'READING',
  'PROJECT',
  'EXERCISE_LIST',
  'OTHER',
] as const;
export const TASK_PRIORITIES = ['LOW', 'MEDIUM', 'HIGH'] as const;
export const TASK_STATUSES = ['TODO', 'DOING', 'DONE'] as const;

export type SubjectStatus = (typeof SUBJECT_STATUSES)[number];
export type TaskType = (typeof TASK_TYPES)[number];
export type TaskPriority = (typeof TASK_PRIORITIES)[number];
export type TaskStatus = (typeof TASK_STATUSES)[number];

export interface Semester {
  id: string;
  name: string;
  startDate: string;
  endDate: string;
  archived: boolean;
  current: boolean;
}

export interface SemesterInput {
  name: string;
  startDate: string;
  endDate: string;
}

export interface Subject {
  id: string;
  semesterId: string;
  name: string;
  code?: string;
  professor?: string;
  credits: number;
  workloadHours?: number;
  color: string;
  status: SubjectStatus;
  pendingTasks: number;
}

export interface SubjectInput {
  semesterId: string;
  name: string;
  code?: string;
  professor?: string;
  credits?: number;
  workloadHours?: number | null;
  color?: string;
  status?: SubjectStatus;
}

export interface Task {
  id: string;
  subjectId?: string;
  title: string;
  description?: string;
  type: TaskType;
  priority: TaskPriority;
  status: TaskStatus;
  dueAt?: string;
  dueAllDay: boolean;
  completedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface TaskInput {
  title: string;
  description?: string | null;
  subjectId?: string | null;
  type?: TaskType;
  priority?: TaskPriority;
  status?: TaskStatus;
  dueAt?: string | null;
  dueAllDay?: boolean;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export const stackApi = {
  semesters: () => api.get<Semester[]>('/semesters'),
  createSemester: (input: SemesterInput) => api.post<Semester>('/semesters', input),
  updateSemester: (id: string, input: SemesterInput) =>
    api.put<Semester>(`/semesters/${id}`, input),
  archiveSemester: (id: string) => api.post<Semester>(`/semesters/${id}/archive`),
  unarchiveSemester: (id: string) => api.post<Semester>(`/semesters/${id}/unarchive`),
  makeCurrentSemester: (id: string) => api.post<Semester>(`/semesters/${id}/current`),

  subjects: (semesterId?: string) =>
    api.get<Subject[]>(semesterId ? `/subjects?semesterId=${semesterId}` : '/subjects'),
  createSubject: (input: SubjectInput) => api.post<Subject>('/subjects', input),
  updateSubject: (id: string, input: SubjectInput) => api.put<Subject>(`/subjects/${id}`, input),
  deleteSubject: (id: string) => api.delete<void>(`/subjects/${id}`),

  /** `query` já montada por `buildTaskQuery` (sem o `?`). */
  tasks: (query: string, signal?: AbortSignal) =>
    api.get<Page<Task>>(`/tasks${query ? `?${query}` : ''}`, signal),
  createTask: (input: TaskInput) => api.post<Task>('/tasks', input),
  updateTask: (id: string, input: TaskInput) => api.put<Task>(`/tasks/${id}`, input),
  setTaskStatus: (id: string, status: TaskStatus) =>
    api.patch<Task>(`/tasks/${id}/status`, { status }),
  deleteTask: (id: string) => api.delete<void>(`/tasks/${id}`),
};
