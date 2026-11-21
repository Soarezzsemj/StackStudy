import { api } from './client';
import type { DayTotal } from './deepwork';
import type { SubjectGrades } from './grades';
import type { TaskPriority, TaskStatus, TaskType } from './stack';

// Tela inicial numa chamada só (GET /api/cockpit). Campos null do backend vêm omitidos.

export interface CockpitTask {
  id: string;
  title: string;
  type: TaskType;
  priority: TaskPriority;
  status: TaskStatus;
  dueAt: string;
  dueAllDay: boolean;
  subjectId?: string;
  subjectName?: string;
  subjectColor?: string;
}

export interface CockpitData {
  timeZone: string;
  /** Dia local (YYYY-MM-DD) no fuso pedido. */
  today: string;
  focus: {
    todaySeconds: number;
    weekSeconds: number;
    /** Meta semanal ÷ 7. */
    dailyGoalSeconds: number;
    weeklyGoalSeconds: number;
    currentStreak: number;
    longestStreak: number;
  };
  heatmap: { from: string; to: string; days: DayTotal[] };
  tasks: {
    overdueCount: number;
    upcomingCount: number;
    overdue: CockpitTask[];
    upcoming: CockpitTask[];
  };
  nextExam?: CockpitTask;
  flashcards: {
    dueToday: number;
    newToday: number;
    reviewsToday: number;
    totalCards: number;
    decks: number;
  };
  atRisk: { gradeRisk: boolean; attendanceRisk: boolean; subject: SubjectGrades }[];
  onboarding: {
    hasSemester: boolean;
    hasSubject: boolean;
    hasTask: boolean;
    hasFocusSession: boolean;
    hasDeck: boolean;
  };
}

export const cockpitApi = {
  get: (tz: string) => api.get<CockpitData>(`/cockpit?tz=${encodeURIComponent(tz)}`),
};
