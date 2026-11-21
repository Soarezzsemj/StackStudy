import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import type { GradeMethod, Standing, SubjectGrades } from '@/lib/api/grades';
import { evaluate, examRequired, type CalcRule } from './calculator';
import i18n from '@/i18n';
import { hasWhatIf, parseScore, plannedPointsOf, simulate } from './simulation';
import { gradeHeadline } from './summary';
import { attendanceTone, formatGrade, standingTone } from './format';

// Mesmos casos do teste do backend (GradeCalculatorTest): as duas implementações batem.
interface SharedCase {
  name: string;
  rule: {
    method: GradeMethod;
    scaleMax: string;
    passingGrade: string;
    exam: { minAverage: string; averageWeight: string; passingGrade: string } | null;
    sum?: { roundBeforeCompare: boolean; roundingScale: number; pointsCap: string | null };
  };
  assessments: {
    weight?: string;
    score: string | null;
    maxPoints?: string | null;
    bonus?: boolean;
  }[];
  examScore: string | null;
  expected: Record<string, unknown> & {
    standing: Standing;
    complete: boolean;
    sum?: Record<string, string | number | boolean | (string | null)[]>;
  };
}

// Os testes rodam a partir de frontend/ (npm test), aqui e no CI.
const casesPath = resolve(
  process.cwd(),
  '../backend/src/test/resources/grade-calculator-cases.json',
);
const cases = JSON.parse(readFileSync(casesPath, 'utf-8')) as SharedCase[];

const num = (v: string | null | undefined) => (v === null || v === undefined ? null : Number(v));

describe('evaluate (casos compartilhados com o backend)', () => {
  it('carrega os casos', () => {
    expect(cases.length).toBeGreaterThan(10);
    expect(cases.some((c) => c.rule.method === 'SUM')).toBe(true);
  });

  it.each(cases.map((c) => [c.name, c] as const))('%s', (_name, c) => {
    const rule: CalcRule = {
      method: c.rule.method,
      scaleMax: Number(c.rule.scaleMax),
      passingGrade: Number(c.rule.passingGrade),
      exam: c.rule.exam
        ? {
            minAverage: Number(c.rule.exam.minAverage),
            averageWeight: Number(c.rule.exam.averageWeight),
            passingGrade: Number(c.rule.exam.passingGrade),
          }
        : null,
      sum: c.rule.sum
        ? {
            roundBeforeCompare: c.rule.sum.roundBeforeCompare,
            roundingScale: c.rule.sum.roundingScale,
            pointsCap: num(c.rule.sum.pointsCap),
          }
        : undefined,
    };
    const result = evaluate(
      rule,
      c.assessments.map((a) => ({
        weight: num(a.weight),
        score: num(a.score),
        maxPoints: num(a.maxPoints),
        bonus: a.bonus ?? false,
      })),
      num(c.examScore),
    );
    const x = c.expected;
    expect(result.standing).toBe(x.standing);
    expect(result.complete).toBe(x.complete);
    for (const field of [
      'gradedWeightPct',
      'currentAverage',
      'minimumAverage',
      'maximumAverage',
      'requiredScore',
      'examRequired',
      'finalGrade',
    ] as const) {
      expect(result[field], field).toBe(num(x[field] as string | null));
    }
    if (!x.sum) {
      expect(result.sum).toBeNull();
      return;
    }
    const s = result.sum!;
    for (const [field, want] of Object.entries(x.sum)) {
      const got = s[field as keyof typeof s];
      if (field === 'requiredPerItem') {
        expect(got, field).toEqual((want as (string | null)[]).map(num));
      } else if (typeof want === 'string') {
        expect(got, field).toBe(Number(want));
      } else {
        expect(got, field).toBe(want);
      }
    }
  });
});

describe('evaluate', () => {
  const rule: CalcRule = { method: 'WEIGHTED', scaleMax: 10, passingGrade: 6, exam: null };

  it('a nota necessária aprova e 0,01 a menos reprova', () => {
    for (let c = 0; c <= 1000; c += 41) {
      const first = c / 100;
      const r = evaluate(rule, [
        { weight: 3, score: first },
        { weight: 7, score: null },
      ]);
      const x = r.requiredScore!;
      if (x === 0 || x > 10) continue;
      const ok = evaluate(rule, [
        { weight: 3, score: first },
        { weight: 7, score: x },
      ]);
      const below = evaluate(rule, [
        { weight: 3, score: first },
        { weight: 7, score: Math.round(x * 100 - 1) / 100 },
      ]);
      expect(ok.standing, `first=${first} x=${x}`).toBe('PASSED');
      expect(below.standing, `first=${first} x=${x}`).toBe('FAILED');
    }
  });

  it('não sofre com ponto flutuante (0,1 + 0,2)', () => {
    const r = evaluate({ ...rule, method: 'ARITHMETIC' }, [
      { weight: 1, score: 0.1 },
      { weight: 1, score: 0.2 },
    ]);
    expect(r.finalGrade).toBe(0.15);
  });

  it('exame: nota mínima', () => {
    expect(examRequired({ minAverage: 4, averageWeight: 50, passingGrade: 5 }, 4.5)).toBe(5.49);
    expect(examRequired({ minAverage: 0, averageWeight: 90, passingGrade: 5 }, 5.9)).toBe(0);
  });

  it('rejeita peso zero', () => {
    expect(() => evaluate(rule, [{ weight: 0, score: null }])).toThrow();
  });
});

const subject: SubjectGrades = {
  subjectId: 's',
  semesterId: 'sem',
  name: 'Cálculo',
  color: '#6aa7ff',
  credits: 4,
  status: 'IN_PROGRESS',
  rule: {
    method: 'WEIGHTED',
    scaleMax: 10,
    passingGrade: 6,
    maxAbsencePct: 25,
    finalExamEnabled: true,
    finalExamMinAverage: 4,
    finalExamAverageWeight: 50,
    finalExamPassingGrade: 5,
    roundBeforeCompare: false,
    roundingScale: 1,
  },
  overrides: {},
  assessments: [
    { id: 'a1', name: 'P1', weight: 50, score: 4 },
    { id: 'a2', name: 'P2', weight: 50 },
  ],
  result: {
    standing: 'AT_RISK',
    totalCount: 2,
    gradedCount: 1,
    totalWeight: 100,
    gradedWeightPct: 50,
    complete: false,
  },
  attendance: { used: 0, status: 'UNKNOWN' },
};

describe('simulate', () => {
  it('sem hipótese, usa só as notas lançadas', () => {
    const r = simulate(subject, {});
    expect(r.requiredScore).toBe(7.99);
    expect(r.standing).toBe('AT_RISK');
  });

  it('"e se eu tirar N" fecha a média e mostra o exame', () => {
    const r = simulate(subject, { a2: 5 });
    expect(r.finalGrade).toBe(4.5);
    expect(r.standing).toBe('EXAM');
    expect(r.examRequired).toBe(5.49);
    expect(simulate(subject, { a2: 5 }, 6).standing).toBe('PASSED');
  });

  it('hasWhatIf', () => {
    expect(hasWhatIf({})).toBe(false);
    expect(hasWhatIf({ a: undefined })).toBe(false);
    expect(hasWhatIf({ a: 3 })).toBe(true);
    expect(hasWhatIf({}, 0)).toBe(true);
  });
});

describe('parseScore', () => {
  it('aceita vírgula ou ponto, até 2 casas e dentro da escala', () => {
    expect(parseScore('', 10)).toBeNull();
    expect(parseScore(' 7,5 ', 10)).toBe(7.5);
    expect(parseScore('7.25', 10)).toBe(7.25);
    expect(parseScore('10', 10)).toBe(10);
    expect(parseScore('10.01', 10)).toBe('invalid');
    expect(parseScore('7,555', 10)).toBe('invalid');
    expect(parseScore('-1', 10)).toBe('invalid');
    expect(parseScore('abc', 10)).toBe('invalid');
  });
});

describe('format', () => {
  it('formata com 2 casas no idioma', () => {
    expect(formatGrade(7.5, 'pt-BR')).toBe('7,50');
    expect(formatGrade(7.5, 'en')).toBe('7.50');
    expect(formatGrade(undefined, 'pt-BR')).toBe('—');
  });

  it('cores de risco', () => {
    expect(standingTone('ON_TRACK')).toBe('action');
    expect(standingTone('AT_RISK')).toBe('warning');
    expect(standingTone('EXAM')).toBe('warning');
    expect(standingTone('IMPOSSIBLE')).toBe('danger');
    expect(standingTone('FAILED')).toBe('danger');
    expect(standingTone('NOT_STARTED')).toBe('neutral');
    expect(attendanceTone('WARNING')).toBe('warning');
    expect(attendanceTone('OVER')).toBe('danger');
    expect(attendanceTone('OK')).toBe('action');
    expect(attendanceTone('UNKNOWN')).toBe('neutral');
  });
});

describe('soma de pontos (frontend)', () => {
  const sumRule: CalcRule = { method: 'SUM', scaleMax: 10, passingGrade: 7, exam: null };

  it('0,1 + 0,2 + 6,7 = 7,0 aprova (centésimos inteiros, sem ponto flutuante)', () => {
    const r = evaluate(sumRule, [
      { score: 0.1, maxPoints: 1 },
      { score: 0.2, maxPoints: 1 },
      { score: 6.7, maxPoints: 8 },
    ]);
    expect(r.finalGrade).toBe(7);
    expect(r.standing).toBe('PASSED');
  });

  it('rejeita nota acima do que a avaliação vale', () => {
    expect(() => evaluate(sumRule, [{ score: 3.5, maxPoints: 3 }])).toThrow();
  });

  it('pontuação prevista ignora bônus e é null com valor inválido', () => {
    const rule = {
      ...subject.rule,
      method: 'SUM' as const,
      roundBeforeCompare: false,
      roundingScale: 1,
    };
    expect(
      plannedPointsOf(rule, [
        { maxPoints: 3, bonus: false },
        { maxPoints: 6, bonus: false },
        { maxPoints: 1, bonus: true },
      ]),
    ).toBe(9);
    expect(plannedPointsOf(rule, [{ maxPoints: 0, bonus: false }])).toBeNull();
  });
});

describe('gradeHeadline (mensagem única por matéria)', () => {
  const sumSubject = {
    method: 'SUM' as const,
    passingGrade: 7,
    scaleMax: 10,
    pendingNames: ['P2'],
  };
  const sum = { total: 5.5, pointsToPass: 1.5, pointsAvailable: 4 };

  it('soma: depende da situação', () => {
    expect(gradeHeadline(sumSubject, { standing: 'ON_TRACK', sum }, 'pt-BR')).toEqual({
      key: 'sumNeeds',
      values: { total: '5,50', passing: '7,00', points: '1,50', available: '4,00' },
    });
    expect(gradeHeadline(sumSubject, { standing: 'SECURED', sum }, 'pt-BR')?.key).toBe(
      'sumSecured',
    );
    expect(gradeHeadline(sumSubject, { standing: 'IMPOSSIBLE', sum }, 'pt-BR')?.key).toBe(
      'sumImpossible',
    );
    expect(
      gradeHeadline(sumSubject, { standing: 'EXAM', examRequired: 4.49, sum }, 'pt-BR'),
    ).toEqual({ key: 'exam', values: { value: '4,49' } });
    expect(gradeHeadline(sumSubject, { standing: 'NO_ASSESSMENTS' }, 'pt-BR')).toBeNull();
  });

  it('média: cita a avaliação quando só falta uma', () => {
    const avg = { method: 'WEIGHTED' as const, passingGrade: 6, scaleMax: 10 };
    expect(
      gradeHeadline(
        { ...avg, pendingNames: ['P2'] },
        { standing: 'AT_RISK', requiredScore: 7.2 },
        'pt-BR',
      ),
    ).toEqual({ key: 'averageNeedsOne', values: { value: '7,20', name: 'P2' } });
    expect(
      gradeHeadline(
        { ...avg, pendingNames: ['P2', 'P3'] },
        { standing: 'ON_TRACK', requiredScore: 5 },
        'pt-BR',
      )?.key,
    ).toBe('averageNeedsEach');
    expect(i18n.t('grades.headline.averageNeedsOne', { value: '7,20', name: 'P2' })).toBe(
      'Precisa de 7,20 na P2 para passar direto',
    );
    expect(i18n.t('grades.headline.sumNeeds', { points: '1,50' })).toBe(
      'Precisa de 1,50 pontos para passar',
    );
  });
});
