import type { ChipTone } from '@/components/ui';
import type { GradeMethod, Standing, SubjectGrades, SubjectRuleInput } from '@/lib/api/grades';

/*
 * Leitura do resultado para as telas da Fase 14 (aba da matéria, cartões pequenos, grade da barra
 * lateral). Nada aqui faz conta de nota: a média, os pontos e a situação vêm da calculadora (API ou
 * `simulate`, o espelho do backend). Sem importar a calculadora: este arquivo vai no pacote
 * principal (barra lateral); os cenários ficam em simulation.ts.
 */

/** Situação resumida em 4 faixas (mais "sem avaliações"), para cartões pequenos. */
export type StatusBucket = 'NONE' | 'POSSIBLE' | 'AT_RISK' | 'PASSED' | 'FAILED';

/**
 * Faixa da situação. "Em risco" junta quem ainda pode passar com esforço (AT_RISK), quem foi para o
 * exame (EXAM) e quem já não passa direto nem com nota máxima (IMPOSSIBLE): ainda não reprovou,
 * mas precisa de atenção. "Reprovado" é só o resultado final (FAILED).
 */
export function statusBucket(standing: Standing): StatusBucket {
  switch (standing) {
    case 'NOT_STARTED':
    case 'ON_TRACK':
      return 'POSSIBLE';
    case 'SECURED':
    case 'PASSED':
      return 'PASSED';
    case 'AT_RISK':
    case 'EXAM':
    case 'IMPOSSIBLE':
      return 'AT_RISK';
    case 'FAILED':
      return 'FAILED';
    default:
      return 'NONE';
  }
}

/** Cor da faixa (com ícone e texto ao lado: nunca só a cor). */
export function bucketTone(bucket: StatusBucket): ChipTone {
  switch (bucket) {
    case 'PASSED':
    case 'POSSIBLE':
      return 'action';
    case 'AT_RISK':
      return 'warning';
    case 'FAILED':
      return 'danger';
    default:
      return 'neutral';
  }
}

/** Número principal da matéria: pontos somados (soma) ou média/resultado (médias). */
export type MainFigure =
  | { kind: 'sum'; total: number; passing: number }
  | { kind: 'average'; value: number | null; final: boolean; passing: number }
  | { kind: 'none' };

/** Campos do resultado usados aqui (a API usa undefined; o simulador, null). */
interface FigureResult {
  standing: Standing;
  complete: boolean;
  currentAverage?: number | null;
  finalGrade?: number | null;
  sum?: { total: number } | null;
}

export function mainFigure(
  rule: { method: GradeMethod; passingGrade: number },
  result: FigureResult,
): MainFigure {
  if (result.standing === 'NO_ASSESSMENTS') return { kind: 'none' };
  if (rule.method === 'SUM' && result.sum) {
    return { kind: 'sum', total: result.sum.total, passing: rule.passingGrade };
  }
  return {
    kind: 'average',
    value: (result.complete ? result.finalGrade : result.currentAverage) ?? null,
    final: result.complete,
    passing: rule.passingGrade,
  };
}

/** Valor curto da matéria na grade lateral: pontos (soma) ou média; sem nota, null ("—"). */
export function sidebarValue(s: SubjectGrades): number | null {
  const figure = mainFigure(s.rule, s.result);
  if (figure.kind === 'sum') return s.result.gradedCount > 0 ? figure.total : null;
  if (figure.kind === 'average') return figure.value;
  return null;
}

/** Limite de quadradinhos na barra lateral antes do "ver todas". */
export const SIDEBAR_LIMIT = 8;

/**
 * Quais matérias aparecem na grade lateral. A API devolve em ordem de criação; até o limite, a
 * ordem é a mesma da The Stack. Passando do limite, as mais recentes vêm primeiro e o resto fica
 * atrás do "ver todas".
 */
export function sidebarSubjects<T>(
  subjects: T[],
  limit = SIDEBAR_LIMIT,
): { shown: T[]; hidden: number } {
  if (subjects.length <= limit) return { shown: subjects, hidden: 0 };
  return { shown: [...subjects].reverse().slice(0, limit), hidden: subjects.length - limit };
}

/**
 * PUT da regra trocando só o método: os valores próprios da matéria continuam como estão (ausente =
 * null = herda), igual ao diálogo "Regra" salvo sem outras mudanças. Trocar de regra não apaga
 * pesos nem "vale quantos pontos" (Decisão 55).
 */
export function ruleInputWithMethod(s: SubjectGrades, method: GradeMethod): SubjectRuleInput {
  const o = s.overrides;
  const n = (v: number | undefined) => v ?? null;
  const b = (v: boolean | undefined) => v ?? null;
  return {
    method,
    scaleMax: n(o.scaleMax),
    passingGrade: n(o.passingGrade),
    maxAbsencePct: n(o.maxAbsencePct),
    totalClasses: n(o.totalClasses),
    finalExamEnabled: b(o.finalExamEnabled),
    finalExamMinAverage: n(o.finalExamMinAverage),
    finalExamAverageWeight: n(o.finalExamAverageWeight),
    finalExamPassingGrade: n(o.finalExamPassingGrade),
    finalExamScore: s.rule.finalExamEnabled ? n(s.finalExamScore) : null,
    roundBeforeCompare: b(o.roundBeforeCompare),
    roundingScale: n(o.roundingScale),
    pointsCap: n(o.pointsCap),
  };
}

/** Matéria aberta por padrão: a primeira com avaliações (dá para simular), senão a primeira. */
export function defaultSubjectId(subjects: SubjectGrades[]): string | undefined {
  return (subjects.find((s) => s.assessments.length > 0) ?? subjects[0])?.subjectId;
}
