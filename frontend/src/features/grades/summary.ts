import type { GradeMethod, Standing } from '@/lib/api/grades';
import { formatGrade } from './format';

/*
 * Mensagem curta de cada matéria ("Precisa de 1,5 pontos para passar", "Precisa de 7,20 na P2
 * para passar direto"). É o ÚNICO lugar que transforma o resultado da calculadora em texto: o
 * cartão, a linha da The Stack, o simulador e (Fase 5) o Cockpit e o radar de risco usam isto.
 * Nada aqui faz conta: só lê o resultado (da API ou do simulador, que usa a mesma calculadora).
 */

/** Campos do resultado usados aqui (API usa undefined; o simulador, null). */
export interface ResultLike {
  standing: Standing;
  requiredScore?: number | null;
  examRequired?: number | null;
  finalGrade?: number | null;
  currentAverage?: number | null;
  sum?: {
    total: number;
    pointsToPass: number;
    pointsAvailable: number;
  } | null;
}

export interface HeadlineSubject {
  method: GradeMethod;
  passingGrade: number;
  scaleMax: number;
  /** Nomes das avaliações sem nota (para "na P2" quando só falta uma). */
  pendingNames: string[];
}

/** Chave de i18n (em `grades.headline.*`) e valores já formatados. */
export interface Headline {
  key: string;
  values: Record<string, string>;
}

export function gradeHeadline(
  subject: HeadlineSubject,
  result: ResultLike,
  lang: string,
): Headline | null {
  const f = (v: number | null | undefined) => formatGrade(v ?? null, lang);
  const { standing } = result;
  if (standing === 'NO_ASSESSMENTS') return null;
  if (standing === 'EXAM') return { key: 'exam', values: { value: f(result.examRequired) } };

  if (subject.method === 'SUM') {
    const s = result.sum;
    if (!s) return null;
    const values = {
      total: f(s.total),
      passing: f(subject.passingGrade),
      points: f(s.pointsToPass),
      available: f(s.pointsAvailable),
    };
    switch (standing) {
      case 'SECURED':
        return { key: 'sumSecured', values };
      case 'PASSED':
        return { key: 'sumPassed', values };
      case 'FAILED':
        return { key: 'sumFailed', values };
      case 'IMPOSSIBLE':
        return { key: 'sumImpossible', values };
      default:
        return { key: 'sumNeeds', values };
    }
  }

  const average = f(result.finalGrade ?? result.currentAverage);
  switch (standing) {
    case 'SECURED':
      return { key: 'averageSecured', values: {} };
    case 'PASSED':
      return { key: 'averagePassed', values: { value: average } };
    case 'FAILED':
      return { key: 'averageFailed', values: { value: average } };
    case 'IMPOSSIBLE':
      return { key: 'averageImpossible', values: {} };
    default: {
      const value = f(result.requiredScore);
      const only = subject.pendingNames.length === 1 ? subject.pendingNames[0] : undefined;
      return only !== undefined
        ? { key: 'averageNeedsOne', values: { value, name: only } }
        : { key: 'averageNeedsEach', values: { value } };
    }
  }
}

/** Dados de uma matéria da API para `gradeHeadline`. */
export function headlineSubjectOf(s: {
  rule: { method: GradeMethod; passingGrade: number; scaleMax: number };
  assessments: { name: string; score?: number }[];
}): HeadlineSubject {
  return {
    method: s.rule.method,
    passingGrade: s.rule.passingGrade,
    scaleMax: s.rule.scaleMax,
    pendingNames: s.assessments.filter((a) => a.score === undefined).map((a) => a.name),
  };
}
