import type { GradeMethod, Standing } from '@/lib/api/grades';

/*
 * Espelho do GradeCalculator do backend, para o simulador "e se" recalcular na hora sem salvar.
 * Sem ponto flutuante nas contas: tudo vira inteiro em centésimos (BigInt) e as divisões
 * arredondam como no Java (média: meio para cima; nota necessária: para cima). Os casos de
 * backend/src/test/resources/grade-calculator-cases.json rodam nas duas implementações.
 */

export interface CalcExamRule {
  minAverage: number;
  /** Peso (%) da média no resultado final, 0 a menos de 100. */
  averageWeight: number;
  passingGrade: number;
}

export interface CalcRule {
  method: GradeMethod;
  scaleMax: number;
  passingGrade: number;
  /** null = sem exame final. */
  exam: CalcExamRule | null;
}

export interface CalcAssessment {
  weight: number;
  /** null = ainda sem nota. */
  score: number | null;
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
    };
  }

  // Pesos e notas em centésimos; pontos em centésimos².
  let weightSum = 0n;
  let gradedWeight = 0n;
  let points = 0n;
  let graded = 0;
  for (const a of items) {
    const w = rule.method === 'ARITHMETIC' ? 100n : cents(a.weight);
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
  };
}
