import type { AssessmentView, EffectiveRule, GradeMethod, SubjectGrades } from '@/lib/api/grades';
import { evaluate, type CalcAssessment, type CalcResult, type CalcRule } from './calculator';

/** Regra efetiva da API → regra da calculadora. */
export function calcRuleOf(rule: EffectiveRule): CalcRule {
  return {
    method: rule.method,
    scaleMax: rule.scaleMax,
    passingGrade: rule.passingGrade,
    exam: rule.finalExamEnabled
      ? {
          minAverage: rule.finalExamMinAverage,
          averageWeight: rule.finalExamAverageWeight,
          passingGrade: rule.finalExamPassingGrade,
        }
      : null,
    sum: {
      roundBeforeCompare: rule.roundBeforeCompare,
      roundingScale: rule.roundingScale,
      pointsCap: rule.pointsCap ?? null,
    },
  };
}

/**
 * Avaliação da API → entrada da calculadora, como o backend faz (GradeService): na soma, uma nota
 * acima do "quanto vale" (dado de quando a matéria usava média) conta como "sem quanto vale".
 */
export function calcAssessmentOf(
  method: GradeMethod,
  a: Pick<AssessmentView, 'weight' | 'maxPoints' | 'bonus'>,
  score: number | null,
): CalcAssessment {
  let maxPoints = a.maxPoints ?? null;
  if (method === 'SUM' && maxPoints !== null && score !== null && score > maxPoints) {
    maxPoints = null;
  }
  return { weight: a.weight, score, maxPoints, bonus: a.bonus ?? false };
}

/** Notas hipotéticas por id de avaliação (só as sem nota lançada). */
export type WhatIf = Record<string, number | undefined>;

/**
 * Recalcula a matéria com as notas hipotéticas, sem salvar. Notas lançadas continuam valendo; o
 * exame hipotético só entra se não houver nota de exame gravada.
 */
export function simulate(subject: SubjectGrades, whatIf: WhatIf, examWhatIf?: number): CalcResult {
  const method = subject.rule.method;
  const items = subject.assessments.map((a) =>
    calcAssessmentOf(method, a, a.score ?? whatIf[a.id] ?? null),
  );
  return evaluate(calcRuleOf(subject.rule), items, subject.finalExamScore ?? examWhatIf ?? null);
}

/** Maior nota aceita numa avaliação: na soma, o quanto ela vale; nas médias, a escala. */
export function maxScoreOf(subject: SubjectGrades, a: AssessmentView): number {
  return subject.rule.method === 'SUM' && a.maxPoints !== undefined
    ? a.maxPoints
    : subject.rule.scaleMax;
}

/**
 * Pontuação prevista (soma do que valem as avaliações que não são bônus), pela calculadora, para o
 * aviso "não fecha a escala" enquanto o formulário é editado. Null se algum valor for inválido.
 */
export function plannedPointsOf(
  rule: EffectiveRule,
  items: { maxPoints: number | null; bonus: boolean }[],
): number | null {
  try {
    const r = evaluate(
      { ...calcRuleOf(rule), method: 'SUM' },
      items.map((i) => ({ score: null, maxPoints: i.maxPoints, bonus: i.bonus })),
    );
    return r.sum?.plannedPoints ?? null;
  } catch {
    return null;
  }
}

export function hasWhatIf(whatIf: WhatIf, examWhatIf?: number): boolean {
  return examWhatIf !== undefined || Object.values(whatIf).some((v) => v !== undefined);
}

/**
 * Lê uma nota digitada ("7", "7,5", "7.25"). Vazio → null; formato ruim, mais de 2 casas ou acima
 * do máximo → 'invalid'.
 */
export function parseScore(text: string, max: number): number | null | 'invalid' {
  const trimmed = text.trim();
  if (trimmed === '') return null;
  if (!/^\d{1,4}([.,]\d{1,2})?$/.test(trimmed)) return 'invalid';
  const value = Number(trimmed.replace(',', '.'));
  if (!Number.isFinite(value) || value > max) return 'invalid';
  return value;
}
