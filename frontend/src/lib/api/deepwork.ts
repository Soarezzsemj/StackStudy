import { api } from './client';
import type { Page } from './stack';

// Tipos e chamadas do Deep Work: sessões, preferências, heatmap e estatísticas.

export type SessionMode = 'POMODORO' | 'FLOW';

export interface StudySession {
  id: string;
  subjectId: string;
  taskId?: string;
  /** Tópico de roadmap ligado (as horas somam no progresso dele). */
  roadmapNodeId?: string;
  mode: SessionMode;
  source: 'TIMER' | 'MANUAL';
  plannedMinutes?: number;
  startedAt: string;
  endedAt?: string;
  pausedAt?: string;
  pausedSeconds: number;
  effectiveSeconds: number;
  active: boolean;
  note?: string;
  focusRating?: number;
  /** Relógio do servidor na resposta (para corrigir a diferença do relógio local). */
  serverTime: string;
}

/** Sessão ativa + diferença entre o relógio do servidor e o local (ms). */
export interface ActiveSession extends StudySession {
  offsetMs: number;
}

export interface DeepWorkSettings {
  focusMinutes: number;
  shortBreakMinutes: number;
  longBreakMinutes: number;
  longBreakEvery: number;
  weeklyGoalMinutes: number;
  soundEnabled: boolean;
  notificationsEnabled: boolean;
}

export interface DayTotal {
  date: string;
  seconds: number;
}

export interface HeatmapData {
  from: string;
  to: string;
  timeZone: string;
  days: DayTotal[];
}

export interface SubjectTotal {
  subjectId: string;
  name: string;
  color: string;
  seconds: number;
}

export interface DeepWorkStats {
  timeZone: string;
  today: string;
  todaySeconds: number;
  weekSeconds: number;
  monthSeconds: number;
  last30DaysSeconds: number;
  dailyAverageSeconds: number;
  currentStreak: number;
  longestStreak: number;
  weeklyGoalSeconds: number;
  weekBySubject: SubjectTotal[];
  monthBySubject: SubjectTotal[];
}

export interface StartInput {
  subjectId: string;
  taskId?: string | null;
  mode: SessionMode;
  plannedMinutes?: number;
  roadmapNodeId?: string | null;
}

export interface CompleteInput {
  note?: string | null;
  focusRating?: number | null;
}

export interface ManualInput {
  subjectId: string;
  taskId?: string | null;
  startedAt: string;
  durationMinutes: number;
  note?: string | null;
}

/** Fuso IANA do navegador (o backend agrupa os dias nele). */
export function browserTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  } catch {
    return 'UTC';
  }
}

/** Anexa a diferença de relógio medida agora. */
export function withOffset(session: StudySession, receivedAt = Date.now()): ActiveSession {
  const server = Date.parse(session.serverTime);
  return { ...session, offsetMs: Number.isFinite(server) ? server - receivedAt : 0 };
}

const base = '/deepwork';
const tzParam = (tz: string) => `tz=${encodeURIComponent(tz)}`;

export const deepWorkApi = {
  /** `undefined` quando não há sessão ativa (204). */
  active: () => api.get<StudySession | undefined>(`${base}/sessions/active`),
  start: (input: StartInput) => api.post<StudySession>(`${base}/sessions`, input),
  pause: (id: string) => api.post<StudySession>(`${base}/sessions/${id}/pause`),
  resume: (id: string) => api.post<StudySession>(`${base}/sessions/${id}/resume`),
  complete: (id: string, input: CompleteInput = {}) =>
    api.post<StudySession>(`${base}/sessions/${id}/complete`, input),
  cancel: (id: string) => api.post<void>(`${base}/sessions/${id}/cancel`),
  history: (page: number, size = 10) =>
    api.get<Page<StudySession>>(`${base}/sessions?page=${page}&size=${size}`),
  createManual: (input: ManualInput) => api.post<StudySession>(`${base}/sessions/manual`, input),
  update: (id: string, input: ManualInput) =>
    api.put<StudySession>(`${base}/sessions/${id}`, input),
  remove: (id: string) => api.delete<void>(`${base}/sessions/${id}`),

  settings: () => api.get<DeepWorkSettings>(`${base}/settings`),
  saveSettings: (input: DeepWorkSettings) => api.put<DeepWorkSettings>(`${base}/settings`, input),
  heatmap: (tz: string) => api.get<HeatmapData>(`${base}/heatmap?${tzParam(tz)}`),
  stats: (tz: string) => api.get<DeepWorkStats>(`${base}/stats?${tzParam(tz)}`),
};
