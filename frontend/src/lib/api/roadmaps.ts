import { api } from './client';

// Roadmaps (/api/roadmaps). Campos null não vêm no JSON (ficam opcionais aqui).

export type RoadmapAccess = 'FREE' | 'PREMIUM';
export type NodeKind = 'MAIN' | 'SUB';
export type NodeSide = 'LEFT' | 'RIGHT';
export type ProgressStatus = 'TODO' | 'DOING' | 'DONE';
export type ResourceKind = 'ARTICLE' | 'VIDEO' | 'BOOK' | 'COURSE' | 'EXERCISE' | 'OTHER';

export interface RoadmapSummary {
  totalTopics: number;
  doneTopics: number;
  percent: number;
  totalHours: number;
  hoursDone: number;
  hoursRemaining: number;
  weeklyGoalHours?: number;
  daysRemaining?: number;
  /** Data (AAAA-MM-DD, UTC). */
  estimatedCompletion?: string;
  completed: boolean;
}

export interface RoadmapItem {
  id: string;
  slug: string;
  title: string;
  description: string;
  area: string;
  language: string;
  access: RoadmapAccess;
  version: number;
  sample: boolean;
  stageCount: number;
  topicCount: number;
  totalHours: number;
  following: boolean;
  primary: boolean;
  percent?: number;
}

export interface RoadmapList {
  items: RoadmapItem[];
  areas: string[];
  premiumEnabled: boolean;
}

export interface RoadmapResource {
  id: string;
  title: string;
  url: string;
  kind: ResourceKind;
}

export interface RoadmapNode {
  id: string;
  parentId?: string;
  kind: NodeKind;
  position: number;
  title: string;
  description?: string;
  optional: boolean;
  estimatedHours: number;
  side?: NodeSide;
  resources: RoadmapResource[];
  status: ProgressStatus;
  hoursSpent: number;
  manualHours: number;
  focusHours: number;
  completedAt?: string;
  current: boolean;
}

export interface RoadmapStage {
  id: string;
  position: number;
  title: string;
  description?: string;
  access: RoadmapAccess;
  locked: boolean;
  topicCount: number;
  doneCount: number;
  nodes: RoadmapNode[];
}

export interface RoadmapFollowing {
  startedAt: string;
  primary: boolean;
  weeklyGoalHours?: number;
  currentNodeId?: string;
}

export interface RoadmapDetail {
  id: string;
  slug: string;
  title: string;
  description: string;
  area: string;
  language: string;
  access: RoadmapAccess;
  version: number;
  sample: boolean;
  premiumEnabled: boolean;
  following?: RoadmapFollowing;
  summary: RoadmapSummary;
  stages: RoadmapStage[];
}

export interface ProgressInput {
  status: ProgressStatus;
  manualHours?: number;
}

export interface SuggestionInput {
  title: string;
  area?: string;
  details?: string;
}

function query(params: Record<string, string | undefined>): string {
  const q = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v) q.set(k, v);
  const s = q.toString();
  return s ? `?${s}` : '';
}

const base = '/roadmaps';
const one = (slug: string) => `${base}/${encodeURIComponent(slug)}`;

export const roadmapsApi = {
  list: (filters: { area?: string; q?: string } = {}) =>
    api.get<RoadmapList>(`${base}${query(filters)}`),
  detail: (slug: string) => api.get<RoadmapDetail>(one(slug)),
  follow: (slug: string, primary?: boolean) =>
    api.post<RoadmapDetail>(`${one(slug)}/follow`, primary ? { primary: true } : {}),
  unfollow: (slug: string) => api.delete<void>(`${one(slug)}/follow`),
  progress: (slug: string, nodeId: string, input: ProgressInput) =>
    api.put<RoadmapDetail>(`${one(slug)}/nodes/${nodeId}/progress`, input),
  goal: (slug: string, weeklyGoalHours: number) =>
    api.put<RoadmapDetail>(`${one(slug)}/goal`, { weeklyGoalHours }),
  suggest: (input: SuggestionInput) => api.post<{ id: string }>(`${base}/suggestions`, input),
};
