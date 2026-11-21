import type { GradeMethod, Standing } from '@/lib/api/grades';

/*
 * Espelho do GradeCalculator do backend, para o simulador "e se" recalcular na hora sem salvar.
 * Sem ponto flutuante nas contas: tudo vira inteiro em centésimos (BigInt) e as divisões
 * arredondam como no Java (média: meio para cima; nota necessária: para cima). Os casos de
 * backend/src/test/resources/grade-calculator-cases.json rodam nas duas implementações.
 * Também cobre a soma de pontos (SUM): ver evaluateSum.
 */

export interface CalcExamRule {
  minAverage: number;
  /** Peso (%) da média no resultado final, 0 a menos de 100. */
  averageWeight: number;
  passingGrade: number;
}

/** Opções da soma de pontos (ignoradas nas médias). */
export interface CalcSumOptions {
  roundBeforeCompare: boolean;
  /** Casas do arredondamento antes de comparar (0 a 2). */
  roundingScale: number;
  /** Teto do total; null = sem teto. */
  pointsCap: number | null;
}

export interface CalcRule {
  method: GradeMethod;
  scaleMax: number;
  passingGrade: number;
  /** null = sem exame final. */
  exam: CalcExamRule | null;
  /** Só na soma; ausente = compara o valor exato, sem teto. */
  sum?: CalcSumOptions;
}

export interface CalcAssessment {
  /** Ignorado na aritmética e na soma. */
  weight?: number | null;
  /** null = ainda sem nota. */
  score: number | null;
  /** Quanto vale (soma de pontos). */
  maxPoints?: number | null;
  bonus?: boolean;
}

/** Detalhe da soma (mesmo contrato do SumDetail do backend). */
export interface CalcSum {
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
  requiredPerItem: (number | null)[];
}

export interface CalcResult {
  standing: Standing;
  totalCount: number;
  gradedCount: number;
  gradedWeightPct: number;
  currentAverage: number | null;
  minimumAverage: number | null;
  maximumAverage: number | null;
  requiredScore: number | null;
  examRequired: number | null;
  finalGrade: number | null;
  complete: boolean;
  /** Só na soma de pontos. */
  sum: CalcSum | null;
}

/** Número com até 2 casas → centésimos inteiros. */
function cents(value: number): bigint {
  return BigInt(Math.round(value * 100));
}

function fromCents(value: bigint): number {
  return Number(value) / 100;
}

/** num ÷ den arredondado meio para cima (num ≥ 0, den > 0). */
function divHalfUp(num: bigint, den: bigint): bigint {
  return (2n * num + den) / (2n * den);
}

/** num ÷ den arredondado para cima (den > 0). */
function divCeil(num: bigint, den: bigint): bigint {
  if (num <= 0n) return -(-num / den);
  return (num + den - 1n) / den;
}

const atLeastZero = (v: bigint) => (v < 0n ? 0n : v);

/** Nota de exame necessária (centésimos) para a média M (centésimos). */
function examRequiredCents(exam: CalcExamRule, averageCents: bigint): bigint {
  // X ≥ (100·(F − 0,005) − p·M) ÷ (100 − p). Em centésimos: (1000·Fm − pc·Mc) ÷ (10000 − pc),
  // com Fm = F em milésimos − 5 e pc = p em centésimos.
  const fm = cents(exam.passingGrade) * 10n - 5n;
  const pc = cents(exam.averageWeight);
  return atLeastZero(divCeil(1000n * fm - pc * averageCents, 10000n - pc));
}

/** Resultado com o exame: (p·M + (100 − p)·X) ÷ 100, meio para cima. */
function examResultCents(exam: CalcExamRule, averageCents: bigint, examCents: bigint): bigint {
  const pc = cents(exam.averageWeight);
  return divHalfUp(pc * averageCents + (10000n - pc) * examCents, 10000n);
}

export function examRequired(exam: CalcExamRule, average: number): number {
  return fromCents(examRequiredCents(exam, cents(average)));
}

/** Mesmo contrato de `GradeCalculator.evaluate` (backend). */
export function evaluate(
  rule: CalcRule,
  items: CalcAssessment[],
  examScore: number | null = null,
): CalcResult {
  const total = items.length;
  if (total === 0) {
    return {
      standing: 'NO_ASSESSMENTS',
      totalCount: 0,
      gradedCount: 0,
      gradedWeightPct: 0,
      currentAverage: null,
      minimumAverage: null,
      maximumAverage: null,
      requiredScore: null,
      examRequired: null,
      finalGrade: null,
      complete: false,
      sum: null,
    };
  }
  if (rule.method === 'SUM') return evaluateSum(rule, items, examScore);

  // Pesos e notas em centésimos; pontos em centésimos².
  let weightSum = 0n;
  let gradedWeight = 0n;
  let points = 0n;
  let graded = 0;
  for (const a of items) {
    const w = rule.method === 'ARITHMETIC' ? 100n : cents(a.weight ?? 0);
    if (w <= 0n) throw new Error('weight must be positive');
    weightSum += w;
    if (a.score !== null) {
      if (a.score < 0) throw new Error('score must not be negative');
      graded++;
      gradedWeight += w;
      points += w * cents(a.score);
    }
  }
  const openWeight = weightSum - gradedWeight;
  const scale = cents(rule.scaleMax);
  const passing = cents(rule.passingGrade);
  const exam = rule.exam;

  const current = graded === 0 ? null : divHalfUp(points, gradedWeight);
  const minimum = divHalfUp(points, weightSum);
  const maximum = divHalfUp(points + scale * openWeight, weightSum);
  const gradedPct = divHalfUp(gradedWeight * 10000n, weightSum);
  const examApplies = (avg: bigint) => exam !== null && avg >= cents(exam.minAverage);

  const base = {
    totalCount: total,
    gradedCount: graded,
    gradedWeightPct: fromCents(gradedPct),
    currentAverage: current === null ? null : fromCents(current),
    minimumAverage: fromCents(minimum),
    maximumAverage: fromCents(maximum),
  };

  if (openWeight === 0n) {
    const average = minimum;
    let standing: Standing;
    let examNeeded: bigint | null = null;
    let finalGrade = average;
    let complete = true;
    if (average >= passing) {
      standing = 'PASSED';
    } else if (exam && examApplies(average)) {
      examNeeded = examRequiredCents(exam, average);
      if (examScore !== null) {
        finalGrade = examResultCents(exam, average, cents(examScore));
        standing = finalGrade >= cents(exam.passingGrade) ? 'PASSED' : 'FAILED';
      } else if (examNeeded > scale) {
        standing = 'FAILED';
      } else {
        standing = 'EXAM';
        complete = false;
      }
    } else {
      standing = 'FAILED';
    }
    return {
      ...base,
      standing,
      requiredScore: null,
      examRequired: examNeeded === null ? null : fromCents(examNeeded),
      finalGrade: fromCents(finalGrade),
      complete,
      sum: null,
    };
  }

  // x ≥ ((mínima − 0,005)·pesoTotal − pontos) ÷ pesoAberto, em centésimos:
  // (Pm·W − 10·pontos) ÷ (10·Wr), com Pm = mínima em milésimos − 5.
  const passingMilli = passing * 10n - 5n;
  const required = atLeastZero(divCeil(passingMilli * weightSum - 10n * points, 10n * openWeight));
  let standing: Standing;
  let examNeeded: bigint | null = null;
  if (required === 0n) {
    standing = 'SECURED';
  } else if (required <= scale) {
    if (current === null) standing = 'NOT_STARTED';
    else standing = current >= passing ? 'ON_TRACK' : 'AT_RISK';
  } else if (exam && examApplies(maximum)) {
    examNeeded = examRequiredCents(exam, maximum);
    standing = examNeeded <= scale ? 'EXAM' : 'IMPOSSIBLE';
  } else {
    standing = 'IMPOSSIBLE';
  }
  return {
    ...base,
    standing,
    requiredScore: fromCents(required),
    examRequired: examNeeded === null ? null : fromCents(examNeeded),
    finalGrade: current === null ? null : fromCents(current),
    complete: false,
    sum: null,
  };
}

const DEFAULT_SUM: CalcSumOptions = {
  roundBeforeCompare: false,
  roundingScale: 1,
  pointsCap: null,
};

/** 10^n em BigInt (n pequeno). */
const pow10 = (n: number) => 10n ** BigInt(n);

/**
 * Soma de pontos, espelho de GradeCalculator.evaluateSum. Valores em centésimos; o limiar de
 * aprovação em milésimos (com 2 casas de arredondamento, a meia unidade é 0,005).
 */
function evaluateSum(
  rule: CalcRule,
  items: CalcAssessment[],
  examScore: number | null,
): CalcResult {
  const opts = rule.sum ?? DEFAULT_SUM;
  if (opts.roundingScale < 0 || opts.roundingScale > 2) throw new Error('roundingScale');
  let earned = 0n;
  let planned = 0n;
  let bonusPoints = 0n;
  let available = 0n;
  let allPoints = 0n;
  let gradedPoints = 0n;
  let graded = 0;
  let missingMax = 0;
  for (const a of items) {
    const max = a.maxPoints == null ? null : cents(a.maxPoints);
    const score = a.score === null ? null : cents(a.score);
    if (max !== null && max <= 0n) throw new Error('maxPoints must be positive');
    if (score !== null) {
      if (score < 0n) throw new Error('score must not be negative');
      if (max !== null && score > max) throw new Error('score must not exceed maxPoints');
      graded++;
      earned += score;
    }
    // Sem "quanto vale", só conta o que já foi tirado.
    const worth = max ?? score ?? 0n;
    if (max === null) missingMax++;
    allPoints += worth;
    if (a.bonus) bonusPoints += worth;
    else if (max !== null) planned += max;
    if (score === null) available += worth;
    else gradedPoints += worth;
  }

  const cap = opts.pointsCap === null ? null : cents(opts.pointsCap);
  const capped = (v: bigint) => (cap !== null && v > cap ? cap : v);
  const total = capped(earned);
  const maxReachable = capped(earned + available);
  const passing = cents(rule.passingGrade);
  const scale = cents(rule.scaleMax);
  // Limiar em milésimos: a mínima, ou (mínima para cima na casa d) − meia unidade da casa d.
  let threshold = passing * 10n;
  if (opts.roundBeforeCompare) {
    const unit = pow10(3 - opts.roundingScale);
    threshold = divCeil(passing * 10n, unit) * unit - unit / 2n;
  }
  const passes = (v: bigint) => v * 10n >= threshold;
  const compare = (v: bigint) => {
    if (!opts.roundBeforeCompare) return v;
    const unit = pow10(2 - opts.roundingScale);
    return divHalfUp(v, unit) * unit;
  };
  const compared = compare(total);
  const toPass = atLeastZero(divCeil(threshold - total * 10n, 10n));
  const exam = rule.exam;
  const examApplies = (v: bigint) => exam !== null && compare(v) >= cents(exam.minAverage);
  const pending = graded < items.length;
  const gradedPct = allPoints === 0n ? 0n : divHalfUp(gradedPoints * 10000n, allPoints);

  let standing: Standing;
  let examNeeded: bigint | null = null;
  let finalGrade: bigint | null = null;
  let complete = false;
  if (!pending) {
    finalGrade = total;
    complete = true;
    if (passes(total)) {
      standing = 'PASSED';
    } else if (exam && examApplies(total)) {
      examNeeded = examRequiredCents(exam, total);
      if (examScore !== null) {
        finalGrade = examResultCents(exam, total, cents(examScore));
        standing = finalGrade >= cents(exam.passingGrade) ? 'PASSED' : 'FAILED';
      } else if (examNeeded > scale) {
        standing = 'FAILED';
      } else {
        standing = 'EXAM';
        complete = false;
      }
    } else {
      standing = 'FAILED';
    }
  } else if (toPass === 0n) {
    standing = 'SECURED';
  } else if (passes(maxReachable)) {
    if (graded === 0) standing = 'NOT_STARTED';
    else standing = toPass * scale > passing * available ? 'AT_RISK' : 'ON_TRACK';
  } else if (exam && examApplies(maxReachable)) {
    examNeeded = examRequiredCents(exam, maxReachable);
    standing = examNeeded <= scale ? 'EXAM' : 'IMPOSSIBLE';
  } else {
    standing = 'IMPOSSIBLE';
  }

  const possible = standing === 'NOT_STARTED' || standing === 'ON_TRACK' || standing === 'AT_RISK';
  const requiredPerItem = items.map((a) => {
    if (a.score !== null || a.maxPoints == null) return null;
    if (standing === 'SECURED') return 0;
    if (possible) return fromCents(divCeil(toPass * cents(a.maxPoints), available));
    return null;
  });

  return {
    standing,
    totalCount: items.length,
    gradedCount: graded,
    gradedWeightPct: fromCents(gradedPct),
    currentAverage: graded === 0 ? null : fromCents(total),
    minimumAverage: fromCents(total),
    maximumAverage: fromCents(maxReachable),
    requiredScore: pending ? fromCents(toPass) : null,
    examRequired: examNeeded === null ? null : fromCents(examNeeded),
    finalGrade: finalGrade === null ? null : fromCents(finalGrade),
    complete,
    sum: {
      earned: fromCents(earned),
      total: fromCents(total),
      comparedTotal: fromCents(compared),
      pointsToPass: fromCents(toPass),
      pointsAvailable: fromCents(available),
      maxReachable: fromCents(maxReachable),
      plannedPoints: fromCents(planned),
      bonusPoints: fromCents(bonusPoints),
      scaleMismatch: planned !== scale,
      missingMaxPoints: missingMax,
      requiredPerItem,
    },
  };
}
