import type { EffectiveRule, SubjectGrades } from '@/lib/api/grades';
import { evaluate, type CalcResult, type CalcRule } from './calculator';

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
  };
}

/** Notas hipotéticas por id de avaliação (só as sem nota lançada). */
export type WhatIf = Record<string, number | undefined>;

/**
 * Recalcula a matéria com as notas hipotéticas, sem salvar. Notas lançadas continuam valendo; o
 * exame hipotético só entra se não houver nota de exame gravada.
 */
export function simulate(subject: SubjectGrades, whatIf: WhatIf, examWhatIf?: number): CalcResult {
  const items = subject.assessments.map((a) => ({
    weight: a.weight,
    score: a.score ?? whatIf[a.id] ?? null,
  }));
  return evaluate(calcRuleOf(subject.rule), items, subject.finalExamScore ?? examWhatIf ?? null);
}

export function hasWhatIf(whatIf: WhatIf, examWhatIf?: number): boolean {
  return examWhatIf !== undefined || Object.values(whatIf).some((v) => v !== undefined);
}

/**
 * Lê uma nota digitada ("7", "7,5", "7.25"). Vazio → null; formato ruim, mais de 2 casas ou acima
 * da escala → 'invalid'.
 */
export function parseScore(text: string, max: number): number | null | 'invalid' {
  const trimmed = text.trim();
  if (trimmed === '') return null;
  if (!/^\d{1,4}([.,]\d{1,2})?$/.test(trimmed)) return 'invalid';
  const value = Number(trimmed.replace(',', '.'));
  if (!Number.isFinite(value) || value > max) return 'invalid';
  return value;
}
