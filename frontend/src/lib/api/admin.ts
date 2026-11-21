import { api } from './client';

// Painel de status do admin (GET /api/admin/status/*). Só dados agregados; campos null omitidos.

export const ADMIN_RANGES = ['24h', '7d', '30d'] as const;
export type AdminRange = (typeof ADMIN_RANGES)[number];

export interface Metric {
  value: number;
  previous: number;
}

export interface AdminOverview {
  range: AdminRange;
  generatedAt: string;
  totalUsers: number;
  newUsers: Metric;
  onlineNow: number;
  dailyActive: Metric;
  weeklyActive: Metric;
  retentionD7: {
    cohort: number;
    retained: number;
    previousCohort: number;
    previousRetained: number;
  };
  series: { day: string; activeUsers: number; signups: number }[];
  funnel: {
    registered: number;
    firstSubject: number;
    firstFocusSession: number;
    returned7d: number;
  };
  usage: {
    weeklyActive: number;
    stack: number;
    deepWork: number;
    flashcards: number;
    grades: number;
  };
  focusHoursPerActive: number;
}

export interface AdminHealth {
  dbUp: boolean;
  dbLatencyMs?: number;
  dbSizeBytes?: number;
  postgresVersion?: string;
  uptimeSeconds: number;
  heapUsedBytes: number;
  heapMaxBytes: number;
  appVersion?: string;
  buildTime?: string;
  commit?: string;
  startsLast24h: number;
  /** Links dos painéis dos provedores (ausente = não configurado). */
  links: { render?: string; neon?: string; vercel?: string; backup?: string };
}

export interface AdminSignup {
  /** Mascarado no servidor (ex.: an***@uni.br). */
  email: string;
  course?: string;
  createdAt: string;
}

export interface AdminErrorRow {
  route: string;
  status: number;
  count: number;
  lastSeenAt: string;
}

export const adminApi = {
  overview: (range: AdminRange) => api.get<AdminOverview>(`/admin/status/overview?range=${range}`),
  health: () => api.get<AdminHealth>('/admin/status/health'),
  signups: () => api.get<AdminSignup[]>('/admin/status/signups'),
  errors: () => api.get<AdminErrorRow[]>('/admin/status/errors'),
};
