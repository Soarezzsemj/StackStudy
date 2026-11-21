import type { FocusRhythm, SignupInput, StudyArea } from '@/lib/api/auth';

/**
 * Regras puras do cadastro em 3 passos. Espelham a validação do backend (o servidor confere de
 * novo); as mensagens são chaves de i18n.
 */

export const STEPS = ['account', 'profile', 'routine'] as const;
export type Step = (typeof STEPS)[number];

export const STUDY_AREAS: StudyArea[] = [
  'TECH_EXACT',
  'HEALTH',
  'LAW',
  'HUMANITIES',
  'BUSINESS',
  'ARTS_DESIGN',
  'HIGH_SCHOOL',
  'OTHER',
];

export const RHYTHMS: FocusRhythm[] = ['POMODORO_25_5', 'POMODORO_50_10', 'FREE'];

/** Rascunho do cadastro: só na memória do componente (a senha nunca vai para o navegador). */
export interface SignupDraft {
  email: string;
  password: string;
  name: string;
  nickname: string;
  area: StudyArea | '';
  course: string;
  period: string;
  institution: string;
  weeklyGoalHours: number;
  focusRhythm: FocusRhythm;
  deadlineReminders: boolean;
  followRoadmap: boolean;
  roomsVisible: boolean;
  acceptTerms: boolean;
  confirmAge: boolean;
}

export const EMPTY_DRAFT: SignupDraft = {
  email: '',
  password: '',
  name: '',
  nickname: '',
  area: '',
  course: '',
  period: '',
  institution: '',
  weeklyGoalHours: 10,
  focusRhythm: 'POMODORO_25_5',
  deadlineReminders: true,
  followRoadmap: true,
  // Aparecer nas salas: desligado por padrão.
  roomsVisible: false,
  acceptTerms: false,
  confirmAge: false,
};

export type Field = keyof SignupDraft;
export type Errors = Partial<Record<Field, string>>;

/** Campos de cada passo (para marcar como tocados ao tentar avançar). */
export const STEP_FIELDS: Record<Step, Field[]> = {
  account: ['email', 'password'],
  profile: ['name', 'nickname', 'area', 'course', 'period', 'institution'],
  routine: ['weeklyGoalHours', 'acceptTerms', 'confirmAge'],
};

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const NICKNAME = /^[\p{L}\p{N} ._-]{2,24}$/u;
const HTML = /[<>]/;

/** Mesma lista curta do backend (PasswordPolicy), sem diferenciar maiúsculas. */
const COMMON = new Set([
  '1234567890',
  '12345678910',
  '123456789a',
  '0123456789',
  '9876543210',
  'qwertyuiop',
  'qwerty1234',
  'password12',
  'password123',
  'password1234',
  'senha12345',
  'senha123456',
  'minhasenha',
  'minhasenha1',
  'minhasenha123',
  '1q2w3e4r5t',
  'abc1234567',
  'stackstudy',
  'stackstudy123',
  'estudar123',
  'faculdade123',
  'vestibular',
  'admin12345',
]);

export function isCommonPassword(password: string): boolean {
  const p = password.trim().toLowerCase();
  return COMMON.has(p) || new Set(p).size <= 2;
}

/**
 * Força da senha em 4 níveis (0 = vazia/inválida). 1 fraca, 2 razoável, 3 boa, 4 forte. A dica diz o
 * próximo passo para melhorar.
 */
export function passwordStrength(password: string): { level: 0 | 1 | 2 | 3 | 4; hint: string } {
  if (!password) return { level: 0, hint: 'signup.password.hint.empty' };
  if (password.length < 10) return { level: 1, hint: 'signup.password.hint.short' };
  if (isCommonPassword(password)) return { level: 1, hint: 'signup.password.hint.common' };
  const classes = [/[a-z]/, /[A-Z]/, /\d/, /[^A-Za-z0-9]/].filter((r) => r.test(password)).length;
  if (password.length >= 16 && classes >= 2)
    return { level: 4, hint: 'signup.password.hint.strong' };
  if (password.length >= 12 && classes >= 3)
    return { level: 4, hint: 'signup.password.hint.strong' };
  if (classes >= 3 || password.length >= 14) return { level: 3, hint: 'signup.password.hint.good' };
  return { level: 2, hint: 'signup.password.hint.fair' };
}

function plain(value: string, max: number, required: boolean, key: string): string | undefined {
  const v = value.trim();
  if (required && !v) return `signup.errors.${key}Required`;
  if (v.length > max || HTML.test(v)) return `signup.errors.${key}Invalid`;
  return undefined;
}

/** Erros por campo (chaves de i18n) de um passo. */
export function validateStep(step: Step, d: SignupDraft): Errors {
  const e: Errors = {};
  if (step === 'account') {
    if (!d.email.trim()) e.email = 'signup.errors.emailRequired';
    else if (!EMAIL.test(d.email.trim()) || d.email.length > 254)
      e.email = 'signup.errors.emailInvalid';
    if (!d.password) e.password = 'signup.errors.passwordRequired';
    else if (d.password.length < 10 || d.password.length > 72)
      e.password = 'signup.errors.passwordLength';
    else if (isCommonPassword(d.password)) e.password = 'signup.errors.passwordCommon';
    else if (d.password.trim().toLowerCase() === d.email.trim().toLowerCase().split('@')[0])
      e.password = 'signup.errors.passwordEmail';
  }
  if (step === 'profile') {
    const name = plain(d.name, 120, true, 'name');
    if (name) e.name = name;
    if (!d.nickname.trim()) e.nickname = 'signup.errors.nicknameRequired';
    else if (!NICKNAME.test(d.nickname.trim())) e.nickname = 'signup.errors.nicknameInvalid';
    if (!d.area) e.area = 'signup.errors.areaRequired';
    const course = plain(d.course, 120, false, 'course');
    if (course) e.course = course;
    const period = plain(d.period, 40, false, 'period');
    if (period) e.period = period;
    const institution = plain(d.institution, 120, false, 'institution');
    if (institution) e.institution = institution;
  }
  if (step === 'routine') {
    if (d.weeklyGoalHours < 1 || d.weeklyGoalHours > 60) e.weeklyGoalHours = 'signup.errors.goal';
    if (!d.acceptTerms) e.acceptTerms = 'signup.errors.terms';
    if (!d.confirmAge) e.confirmAge = 'signup.errors.age';
  }
  return e;
}

export function stepValid(step: Step, d: SignupDraft): boolean {
  return Object.keys(validateStep(step, d)).length === 0;
}

/** Até onde dá para ir pela barra de etapas: só passa de uma etapa se as anteriores valem. */
export function canGoTo(target: Step, d: SignupDraft): boolean {
  const index = STEPS.indexOf(target);
  return STEPS.slice(0, index).every((s) => stepValid(s, d));
}

/**
 * Faixa de meta semanal sugerida pelo período (só uma referência; a pessoa escolhe). Começo do
 * curso ou ensino médio: menos horas; fim do curso: mais.
 */
export function suggestedGoal(area: StudyArea | '', period: string): { min: number; max: number } {
  if (area === 'HIGH_SCHOOL') return { min: 8, max: 15 };
  const n = Number(/\d+/.exec(period)?.[0] ?? NaN);
  if (!Number.isFinite(n)) return { min: 8, max: 20 };
  if (n <= 2) return { min: 6, max: 12 };
  if (n <= 6) return { min: 10, max: 20 };
  return { min: 12, max: 25 };
}

/** Iniciais para o avatar da prévia (até 2 letras). */
export function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  const first = parts[0]![0] ?? '';
  const last = parts.length > 1 ? (parts[parts.length - 1]![0] ?? '') : '';
  return (first + last).toUpperCase();
}

/** Corpo do POST /api/auth/register. */
export function buildPayload(
  d: SignupDraft,
  locale: string,
  suggestedSlug: string | undefined,
): SignupInput {
  const opt = (v: string) => (v.trim() ? v.trim() : undefined);
  return {
    email: d.email.trim(),
    password: d.password,
    name: d.name.trim(),
    nickname: d.nickname.trim(),
    area: d.area as StudyArea,
    course: opt(d.course),
    period: opt(d.period),
    institution: opt(d.institution),
    weeklyGoalHours: d.weeklyGoalHours,
    focusRhythm: d.focusRhythm,
    deadlineReminders: d.deadlineReminders,
    followRoadmap: d.followRoadmap && suggestedSlug ? suggestedSlug : undefined,
    roomsVisible: d.roomsVisible,
    acceptTerms: d.acceptTerms,
    confirmAge: d.confirmAge,
    locale,
  };
}
