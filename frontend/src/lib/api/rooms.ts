import { api } from './client';

// Salas de estudo (/api/rooms). Campos null não vêm no JSON. Nenhum dado de outra pessoa além do
// apelido escolhido na sala.

export type RoomMode = 'POMODORO' | 'FREE';
export type RoomVisibility = 'PRIVATE' | 'PUBLIC';
export type MemberRole = 'OWNER' | 'MEMBER';
export type PresenceState = 'STUDYING' | 'PAUSED' | 'AUTO_PAUSED' | 'AWAY' | 'OFFLINE';
export type PomodoroPhase = 'FOCUS' | 'SHORT_BREAK' | 'LONG_BREAK';
export type Reaction = 'BORA' | 'FOCO' | 'BOA' | 'FORCA';
export type ReportReason =
  | 'SPAM'
  | 'HARASSMENT'
  | 'INAPPROPRIATE_NAME'
  | 'OFFENSIVE_CONTENT'
  | 'SAFETY_CONCERN'
  | 'OTHER';

export const REACTIONS: Reaction[] = ['BORA', 'FOCO', 'BOA', 'FORCA'];
export const REPORT_REASONS: ReportReason[] = [
  'SPAM',
  'HARASSMENT',
  'INAPPROPRIATE_NAME',
  'OFFENSIVE_CONTENT',
  'SAFETY_CONCERN',
  'OTHER',
];

export interface RoomItem {
  id: string;
  name: string;
  visibility: RoomVisibility;
  role: MemberRole;
  memberCount: number;
  maxMembers: number;
  studyingNow: number;
  defaultMode: RoomMode;
  weeklyGoalHours?: number;
}

export interface PublicRoom {
  id: string;
  name: string;
  memberCount: number;
  maxMembers: number;
  studyingNow: number;
  defaultMode: RoomMode;
  weeklyGoalHours?: number;
  member: boolean;
  full: boolean;
}

export interface PomodoroConfig {
  focusMinutes: number;
  shortBreakMinutes: number;
  longBreakMinutes: number;
  longBreakEvery: number;
  cycleStartedAt: string;
}

export interface Phase {
  phase: PomodoroPhase;
  cycle: number;
  of: number;
  startedAt: string;
  endsAt: string;
}

export interface RoomSession {
  sessionId: string;
  roomId: string;
  mode: RoomMode;
  state: PresenceState;
  startedAt: string;
  /** Contado pelo servidor até `serverTime`. */
  countedSeconds: number;
  /** O tempo está contando agora. */
  running: boolean;
  cyclesCompleted: number;
  /** "Ainda está estudando?" em aberto: responder até aqui. */
  confirmDeadline?: string;
  phase?: Phase;
  serverTime: string;
}

export interface RoomDetail {
  id: string;
  name: string;
  visibility: RoomVisibility;
  owner: boolean;
  inviteCode?: string;
  memberCount: number;
  maxMembers: number;
  weeklyGoalHours?: number;
  weekSeconds: number;
  defaultMode: RoomMode;
  pomodoro: PomodoroConfig;
  phase: Phase;
  me: {
    nickname: string;
    role: MemberRole;
    showSubject: boolean;
    hideFromRanking: boolean;
    muted: boolean;
  };
  myStats: { todaySeconds: number; weekSeconds: number; longestSeconds: number };
  session?: RoomSession;
  /** Minha sessão ativa está em outra sala (id) ou no Deep Work ("DEEP_WORK"). */
  activeElsewhere?: string;
  timing: {
    heartbeatSeconds: number;
    absentAfterSeconds: number;
    saveEveryMinutes: number;
    confirmEveryMinutes: number;
    confirmGraceMinutes: number;
  };
  serverTime: string;
}

export interface MemberPresence {
  userId: string;
  nickname: string;
  role: MemberRole;
  me: boolean;
  muted: boolean;
  state: PresenceState;
  mode?: RoomMode;
  since?: string;
  sessionSeconds: number;
  cyclesCompleted: number;
  subject?: string;
}

export interface Presence {
  studyingNow: number;
  phase: Phase;
  members: MemberPresence[];
  reactions: { fromNickname: string; kind: Reaction; at: string }[];
  serverTime: string;
}

export interface Ranking {
  period: 'today' | 'week';
  mode: RoomMode;
  entries: { position: number; nickname: string; me: boolean; seconds: number; cycles: number }[];
}

export interface CreateRoomInput {
  name: string;
  nickname: string;
  visibility?: RoomVisibility;
  defaultMode?: RoomMode;
  maxMembers?: number;
  weeklyGoalHours?: number;
}

export interface UpdateRoomInput {
  name?: string;
  visibility?: RoomVisibility;
  defaultMode?: RoomMode;
  maxMembers?: number;
  /** 0 tira a meta. */
  weeklyGoalHours?: number;
  focusMinutes?: number;
  shortBreakMinutes?: number;
  longBreakMinutes?: number;
  longBreakEvery?: number;
  restartCycle?: boolean;
}

export interface UpdateMeInput {
  nickname?: string;
  showSubject?: boolean;
  hideFromRanking?: boolean;
}

const tzq = (tz: string) => `?tz=${encodeURIComponent(tz)}`;
const room = (id: string) => `/rooms/${id}`;

export const roomsApi = {
  mine: () => api.get<RoomItem[]>('/rooms/mine'),
  publicRooms: (q: string) =>
    api.get<PublicRoom[]>(`/rooms/public${q ? `?q=${encodeURIComponent(q)}` : ''}`),
  detail: (id: string, tz: string) => api.get<RoomDetail>(`${room(id)}${tzq(tz)}`),
  create: (input: CreateRoomInput, tz: string) => api.post<RoomDetail>(`/rooms${tzq(tz)}`, input),
  join: (code: string, nickname: string, tz: string) =>
    api.post<RoomDetail>(`/rooms/join${tzq(tz)}`, { code, nickname }),
  joinPublic: (id: string, nickname: string, tz: string) =>
    api.post<RoomDetail>(`${room(id)}/join${tzq(tz)}`, { nickname }),
  leave: (id: string, deleteHistory: boolean) =>
    api.post<void>(`${room(id)}/leave`, { deleteHistory }),
  deleteHistory: (id: string) => api.delete<{ deleted: number }>(`${room(id)}/history`),
  update: (id: string, input: UpdateRoomInput, tz: string) =>
    api.patch<RoomDetail>(`${room(id)}${tzq(tz)}`, input),
  updateMe: (id: string, input: UpdateMeInput, tz: string) =>
    api.patch<RoomDetail>(`${room(id)}/me${tzq(tz)}`, input),
  remove: (id: string) => api.delete<void>(room(id)),
  regenerateCode: (id: string) => api.post<{ inviteCode: string }>(`${room(id)}/invite-code`),
  removeMember: (id: string, userId: string) => api.delete<void>(`${room(id)}/members/${userId}`),
  muteMember: (id: string, userId: string, muted: boolean) =>
    api.patch<void>(`${room(id)}/members/${userId}`, { muted }),

  start: (id: string, input: { mode?: RoomMode; subjectId?: string; takeover?: boolean }) =>
    api.post<RoomSession>(`${room(id)}/session/start`, input),
  heartbeat: (id: string) => api.post<RoomSession>(`${room(id)}/session/heartbeat`),
  pause: (id: string) => api.post<RoomSession>(`${room(id)}/session/pause`),
  resume: (id: string) => api.post<RoomSession>(`${room(id)}/session/resume`),
  confirm: (id: string) => api.post<RoomSession>(`${room(id)}/session/confirm-presence`),
  stop: (id: string) => api.post<RoomSession>(`${room(id)}/session/stop`),

  presence: (id: string) => api.get<Presence>(`${room(id)}/presence`),
  ranking: (id: string, period: 'today' | 'week', mode: RoomMode, tz: string) =>
    api.get<Ranking>(
      `${room(id)}/ranking?period=${period}&mode=${mode}&tz=${encodeURIComponent(tz)}`,
    ),
  react: (id: string, toUserId: string, kind: Reaction) =>
    api.post<void>(`${room(id)}/reactions`, { toUserId, kind }),
  report: (id: string, input: { reason: ReportReason; detail?: string; userId?: string }) =>
    api.post<void>(`${room(id)}/report`, input),
  block: (userId: string) => api.post<void>(`/users/${userId}/block`),
  unblock: (userId: string) => api.delete<void>(`/users/${userId}/block`),
  blocks: () => api.get<{ userId: string; nickname?: string }[]>('/users/blocks'),
};
