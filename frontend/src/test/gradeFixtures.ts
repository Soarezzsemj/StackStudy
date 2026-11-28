import type { GradeMethod, Standing, SubjectGrades } from '@/lib/api/grades';
import type { StatusBucket } from '@/features/grades/gradeView';
import { simulate } from '@/features/grades/simulation';

// Fixtures do Grade Predictor para os testes (Fase 14): resultado sempre vindo da calculadora.

/**
 * Matéria de teste com o resultado vindo da calculadora (o mesmo espelho do backend), para cada
 * regra. Médias: pesos 50/50, mínima 6. Soma: duas avaliações valendo 5, mínima 7. Sem exame.
 */
export function subjectWith(
  method: GradeMethod,
  scores: (number | undefined)[],
  id = `${method}-${scores.join('-')}`,
): SubjectGrades {
  const passingGrade = method === 'SUM' ? 7 : 6;
  const base: SubjectGrades = {
    subjectId: id,
    semesterId: 's1',
    name: id,
    color: '#6aa7ff',
    credits: 4,
    status: 'IN_PROGRESS',
    rule: {
      method,
      scaleMax: 10,
      passingGrade,
      maxAbsencePct: 25,
      finalExamEnabled: false,
      finalExamMinAverage: 4,
      finalExamAverageWeight: 50,
      finalExamPassingGrade: 5,
      roundBeforeCompare: false,
      roundingScale: 1,
    },
    overrides: { method },
    assessments: scores.map((score, i) => ({
      id: `${id}-a${i}`,
      name: `P${i + 1}`,
      weight: 50,
      maxPoints: 5,
      score,
      bonus: false,
    })),
    result: {
      standing: 'NO_ASSESSMENTS',
      totalCount: 0,
      gradedCount: 0,
      totalWeight: 0,
      gradedWeightPct: 0,
      complete: false,
    },
    attendance: { used: 0, status: 'UNKNOWN' },
  };
  const r = simulate(base, {});
  const opt = (v: number | null) => (v === null ? undefined : v);
  return {
    ...base,
    result: {
      standing: r.standing,
      totalCount: r.totalCount,
      gradedCount: r.gradedCount,
      totalWeight: 100,
      gradedWeightPct: r.gradedWeightPct,
      currentAverage: opt(r.currentAverage),
      requiredScore: opt(r.requiredScore),
      examRequired: opt(r.examRequired),
      finalGrade: opt(r.finalGrade),
      complete: r.complete,
      sum: r.sum ?? undefined,
    },
  };
}

/** Por regra: notas que levam a cada faixa (e a situação exata que a calculadora dá). */
export const SITUATIONS: Record<
  GradeMethod,
  Record<Exclude<StatusBucket, 'NONE'>, { scores: (number | undefined)[]; standing: Standing }>
> = {
  SUM: {
    POSSIBLE: { scores: [4, undefined], standing: 'ON_TRACK' },
    // Faltam 6 e a P2 só vale 5: nem com nota máxima dá.
    AT_RISK: { scores: [1, undefined], standing: 'IMPOSSIBLE' },
    PASSED: { scores: [4, 4], standing: 'PASSED' },
    FAILED: { scores: [3, 3], standing: 'FAILED' },
  },
  WEIGHTED: {
    POSSIBLE: { scores: [8, undefined], standing: 'ON_TRACK' },
    AT_RISK: { scores: [1, undefined], standing: 'IMPOSSIBLE' },
    PASSED: { scores: [7, 8], standing: 'PASSED' },
    FAILED: { scores: [4, 4], standing: 'FAILED' },
  },
  ARITHMETIC: {
    POSSIBLE: { scores: [8, undefined], standing: 'ON_TRACK' },
    AT_RISK: { scores: [1, undefined], standing: 'IMPOSSIBLE' },
    PASSED: { scores: [7, 8], standing: 'PASSED' },
    FAILED: { scores: [4, 4], standing: 'FAILED' },
  },
};
