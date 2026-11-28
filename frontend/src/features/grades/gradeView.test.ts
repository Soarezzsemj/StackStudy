import { describe, expect, it } from 'vitest';
import type { GradeMethod, Standing } from '@/lib/api/grades';
import { SITUATIONS, subjectWith } from '@/test/gradeFixtures';
import { scenariosOf } from './simulation';
import {
  mainFigure,
  ruleInputWithMethod,
  sidebarSubjects,
  sidebarValue,
  statusBucket,
  type StatusBucket,
} from './gradeView';

describe('statusBucket', () => {
  it.each([
    ['NO_ASSESSMENTS', 'NONE'],
    ['NOT_STARTED', 'POSSIBLE'],
    ['ON_TRACK', 'POSSIBLE'],
    ['SECURED', 'PASSED'],
    ['PASSED', 'PASSED'],
    ['AT_RISK', 'AT_RISK'],
    ['EXAM', 'AT_RISK'],
    ['IMPOSSIBLE', 'AT_RISK'],
    ['FAILED', 'FAILED'],
  ] as [Standing, StatusBucket][])('%s → %s', (standing, bucket) => {
    expect(statusBucket(standing)).toBe(bucket);
  });

  for (const method of ['SUM', 'WEIGHTED', 'ARITHMETIC'] as GradeMethod[]) {
    it.each(Object.entries(SITUATIONS[method]))(
      `${method}: faixa %s com a situação da calculadora`,
      (bucket, { scores, standing }) => {
        const s = subjectWith(method, scores);
        expect(s.result.standing).toBe(standing);
        expect(statusBucket(s.result.standing)).toBe(bucket);
      },
    );
  }
});

describe('mainFigure e sidebarValue', () => {
  it('soma mostra os pontos; média mostra a média atual ou o resultado final', () => {
    const sum = subjectWith('SUM', [4, undefined]);
    expect(mainFigure(sum.rule, sum.result)).toEqual({ kind: 'sum', total: 4, passing: 7 });
    expect(sidebarValue(sum)).toBe(4);

    const open = subjectWith('WEIGHTED', [8, undefined]);
    expect(mainFigure(open.rule, open.result)).toMatchObject({ kind: 'average', final: false });
    const done = subjectWith('WEIGHTED', [7, 8]);
    expect(mainFigure(done.rule, done.result)).toEqual({
      kind: 'average',
      value: 7.5,
      final: true,
      passing: 6,
    });
    expect(sidebarValue(done)).toBe(7.5);
  });

  it('sem nota lançada, a grade lateral mostra "—" (null)', () => {
    expect(sidebarValue(subjectWith('SUM', [undefined, undefined]))).toBeNull();
    expect(sidebarValue(subjectWith('WEIGHTED', [undefined, undefined]))).toBeNull();
    expect(sidebarValue(subjectWith('ARITHMETIC', []))).toBeNull();
  });
});

describe('scenariosOf', () => {
  it('0%, 60% e 100% do máximo no que falta, pela calculadora', () => {
    const s = subjectWith('WEIGHTED', [4, undefined]);
    const [worst, realistic, best] = scenariosOf(s);
    expect(worst!.result.finalGrade).toBe(2);
    expect(realistic!.result.finalGrade).toBe(5);
    expect(best!.result.finalGrade).toBe(7);
    expect(best!.result.standing).toBe('PASSED');
  });

  it('na soma, o cenário usa o quanto cada avaliação vale', () => {
    const s = subjectWith('SUM', [4, undefined]);
    expect(scenariosOf(s).map((sc) => sc.result.sum?.total)).toEqual([4, 7, 9]);
  });

  it('sem avaliação pendente, nenhum cenário', () => {
    expect(scenariosOf(subjectWith('WEIGHTED', [7, 8]))).toEqual([]);
  });
});

describe('sidebarSubjects', () => {
  it('até 8, mantém a ordem de criação e não esconde nada', () => {
    const list = ['a', 'b', 'c'];
    expect(sidebarSubjects(list)).toEqual({ shown: ['a', 'b', 'c'], hidden: 0 });
  });

  it('mais de 8: as 8 mais recentes primeiro e o resto escondido', () => {
    const list = Array.from({ length: 11 }, (_, i) => `m${i + 1}`);
    const { shown, hidden } = sidebarSubjects(list);
    expect(shown).toEqual(['m11', 'm10', 'm9', 'm8', 'm7', 'm6', 'm5', 'm4']);
    expect(hidden).toBe(3);
    // Não altera a lista original.
    expect(list[0]).toBe('m1');
  });
});

describe('ruleInputWithMethod', () => {
  it('troca só o método e mantém os valores próprios (ausente = null)', () => {
    const s = subjectWith('WEIGHTED', [4, undefined]);
    const input = ruleInputWithMethod(
      { ...s, overrides: { passingGrade: 7, totalClasses: 40 } },
      'SUM',
    );
    expect(input).toEqual({
      method: 'SUM',
      scaleMax: null,
      passingGrade: 7,
      maxAbsencePct: null,
      totalClasses: 40,
      finalExamEnabled: null,
      finalExamMinAverage: null,
      finalExamAverageWeight: null,
      finalExamPassingGrade: null,
      finalExamScore: null,
      roundBeforeCompare: null,
      roundingScale: null,
      pointsCap: null,
    });
  });

  it('nota de exame gravada continua quando a regra tem exame', () => {
    const s = subjectWith('WEIGHTED', [4, 4]);
    const withExam = { ...s, rule: { ...s.rule, finalExamEnabled: true }, finalExamScore: 6 };
    expect(ruleInputWithMethod(withExam, 'ARITHMETIC').finalExamScore).toBe(6);
  });
});
