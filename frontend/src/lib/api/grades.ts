import { api } from './client';
import type { SubjectStatus } from './stack';

// Tipos e chamadas do Grade Predictor. Campos null são omitidos pelo backend (viram undefined).

// Ordem de exibição no seletor: soma de pontos primeiro. Regra nova: ver grade.GradingRule (backend).
export const GRADE_METHODS = ['SUM', 'WEIGHTED', 'ARITHMETIC'] as const;
export const GPA_SCALES = ['TEN', 'FOUR'] as const;
export const GPA_WEIGHTS = ['CREDITS', 'WORKLOAD'] as const;

export type GradeMethod = (typeof GRADE_METHODS)[number];
export type GpaScale = (typeof GPA_SCALES)[number];
export type GpaWeight = (typeof GPA_WEIGHTS)[number];

export type Standing =
  | 'NO_ASSESSMENTS'
  | 'NOT_STARTED'
  | 'SECURED'
  | 'ON_TRACK'
  | 'AT_RISK'
  | 'EXAM'
  | 'IMPOSSIBLE'
  | 'PASSED'
  | 'FAILED';

export type AttendanceStatus = 'UNKNOWN' | 'OK' | 'WARNING' | 'OVER';

export interface GradeSettings {
  method: GradeMethod;
  scaleMax: number;
  passingGrade: number;
  maxAbsencePct: number;
  finalExamEnabled: boolean;
  finalExamMinAverage: number;
  finalExamAverageWeight: number;
  finalExamPassingGrade: number;
  gpaScale: GpaScale;
  gpaWeight: GpaWeight;
  /** Soma de pontos: arredonda o total antes de comparar com a mínima. */
  roundBeforeCompare: boolean;
  /** Casas desse arredondamento (0 a 2). */
  roundingScale: number;
}

/** Regra que vale para a matéria (própria ou herdada das preferências). */
export interface EffectiveRule {
  method: GradeMethod;
  scaleMax: number;
  passingGrade: number;
  maxAbsencePct: number;
  attendanceBase?: number;
  finalExamEnabled: boolean;
  finalExamMinAverage: number;
  finalExamAverageWeight: number;
  finalExamPassingGrade: number;
  roundBeforeCompare: boolean;
  roundingScale: number;
  /** Teto do total na soma de pontos (ausente = sem teto). */
  pointsCap?: number;
}

/** Valores gravados na matéria; ausente = herda a preferência. */
export interface RuleOverrides {
  method?: GradeMethod;
  scaleMax?: number;
  passingGrade?: number;
  maxAbsencePct?: number;
  totalClasses?: number;
  finalExamEnabled?: boolean;
  finalExamMinAverage?: number;
  finalExamAverageWeight?: number;
  finalExamPassingGrade?: number;
  roundBeforeCompare?: boolean;
  roundingScale?: number;
  pointsCap?: number;
}

export interface AssessmentView {
  id: string;
  name: string;
  weight: number;
  score?: number;
  /** Quanto vale (soma de pontos). */
  maxPoints?: number;
  bonus?: boolean;
}

/** Detalhe da soma de pontos (só quando a regra é SUM). */
export interface SumView {
  earned: number;
  total: number;
  comparedTotal: number;
  pointsToPass: number;
  pointsAvailable: number;
  maxReachable: number;
  plannedPoints: number;
  bonusPoints: number;
  scaleMismatch: boolean;
  missingMaxPoints: number;
  /** Por avaliação (mesma ordem): pontos mínimos nela; null nas lançadas ou sem sentido. */
  requiredPerItem: (number | null)[];
}

export interface GradeResult {
  standing: Standing;
  totalCount: number;
  gradedCount: number;
  totalWeight: number;
  gradedWeightPct: number;
  currentAverage?: number;
  minimumAverage?: number;
  maximumAverage?: number;
  requiredScore?: number;
  examRequired?: number;
  finalGrade?: number;
  complete: boolean;
  sum?: SumView;
}

export interface AttendanceView {
  base?: number;
  used: number;
  allowed?: number;
  remaining?: number;
  attendancePct?: number;
  status: AttendanceStatus;
}

export interface SubjectGrades {
  subjectId: string;
  semesterId: string;
  name: string;
  code?: string;
  color: string;
  credits: number;
  workloadHours?: number;
  status: SubjectStatus;
  rule: EffectiveRule;
  overrides: RuleOverrides;
  finalExamScore?: number;
  assessments: AssessmentView[];
  result: GradeResult;
  attendance: AttendanceView;
}

export interface SemesterGpa {
  semesterId: string;
  name: string;
  startDate: string;
  current: boolean;
  value?: number;
  subjects: number;
  totalWeight: number;
  provisional: number;
  excludedNoWeight: number;
}

export interface Gpa {
  scale: GpaScale;
  weightBasis: GpaWeight;
  cumulative?: number;
  cumulativeSubjects: number;
  cumulativeProvisional: number;
  semesters: SemesterGpa[];
}

export interface GradesOverview {
  settings: GradeSettings;
  semesterId?: string;
  subjects: SubjectGrades[];
  gpa: Gpa;
}

/** Regra própria da matéria; null = herda a preferência. */
export interface SubjectRuleInput {
  method: GradeMethod | null;
  scaleMax: number | null;
  passingGrade: number | null;
  maxAbsencePct: number | null;
  totalClasses: number | null;
  finalExamEnabled: boolean | null;
  finalExamMinAverage: number | null;
  finalExamAverageWeight: number | null;
  finalExamPassingGrade: number | null;
  finalExamScore: number | null;
  roundBeforeCompare: boolean | null;
  roundingScale: number | null;
  pointsCap: number | null;
}

export interface AssessmentInput {
  id?: string;
  name: string;
  /** Null fora da média ponderada: o backend mantém o peso que já estava. */
  weight: number | null;
  score: number | null;
  /** Quanto vale (obrigatório na soma de pontos). */
  maxPoints?: number | null;
  bonus?: boolean;
}

export interface Absence {
  id: string;
  subjectId: string;
  date: string;
  count: number;
}

export interface AbsenceInput {
  date: string;
  count: number;
}

export const gradesApi = {
  overview: (semesterId?: string) =>
    api.get<GradesOverview>(
      semesterId ? `/grades/overview?semesterId=${semesterId}` : '/grades/overview',
    ),
  settings: () => api.get<GradeSettings>('/grades/settings'),
  updateSettings: (input: GradeSettings) => api.put<GradeSettings>('/grades/settings', input),
  updateRule: (subjectId: string, input: SubjectRuleInput) =>
    api.put<SubjectGrades>(`/subjects/${subjectId}/grading`, input),
  replaceAssessments: (subjectId: string, items: AssessmentInput[]) =>
    api.put<SubjectGrades>(`/subjects/${subjectId}/assessments`, { items }),
  absences: (subjectId: string) => api.get<Absence[]>(`/subjects/${subjectId}/absences`),
  createAbsence: (subjectId: string, input: AbsenceInput) =>
    api.post<Absence>(`/subjects/${subjectId}/absences`, input),
  deleteAbsence: (id: string) => api.delete<void>(`/absences/${id}`),
};
