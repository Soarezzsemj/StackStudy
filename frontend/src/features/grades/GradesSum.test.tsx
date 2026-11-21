import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '@/app/router';
import i18n from '@/i18n';
import type { AssessmentView, GradeResult, GradesOverview, SubjectGrades } from '@/lib/api/grades';
import type { Semester } from '@/lib/api/stack';
import { useAuthStore } from '@/stores/authStore';
import { emptyOverview, jsonResponse, mockApi, renderWithProviders, testUser } from '@/test/utils';
import type { CalcResult } from './calculator';
import { simulate } from './simulation';

// Tela de notas com a regra SOMA de pontos (P1 vale 3, trabalho 2, P2 4, participação 1).

const semester: Semester = {
  id: 's1',
  name: '2026.2',
  startDate: '2026-08-01',
  endDate: '2026-12-15',
  archived: false,
  current: true,
};

const sumRule = {
  method: 'SUM' as const,
  scaleMax: 10,
  passingGrade: 7,
  maxAbsencePct: 25,
  attendanceBase: 60,
  finalExamEnabled: false,
  finalExamMinAverage: 4,
  finalExamAverageWeight: 50,
  finalExamPassingGrade: 5,
  roundBeforeCompare: false,
  roundingScale: 1,
};

/** Resultado da calculadora → formato da API (null vira ausente). */
function apiResult(r: CalcResult, totalWeight: number): GradeResult {
  const opt = (v: number | null) => (v === null ? undefined : v);
  return {
    standing: r.standing,
    totalCount: r.totalCount,
    gradedCount: r.gradedCount,
    totalWeight,
    gradedWeightPct: r.gradedWeightPct,
    currentAverage: opt(r.currentAverage),
    minimumAverage: opt(r.minimumAverage),
    maximumAverage: opt(r.maximumAverage),
    requiredScore: opt(r.requiredScore),
    examRequired: opt(r.examRequired),
    finalGrade: opt(r.finalGrade),
    complete: r.complete,
    sum: r.sum ?? undefined,
  };
}

function sumSubject(
  id: string,
  name: string,
  items: [string, number, number | undefined][],
): SubjectGrades {
  const assessments: AssessmentView[] = items.map(([n, maxPoints, score], i) => ({
    id: `${id}-a${i}`,
    name: n,
    weight: 1,
    maxPoints,
    score,
    bonus: false,
  }));
  const base: SubjectGrades = {
    subjectId: id,
    semesterId: 's1',
    name,
    color: '#bd6cf7',
    credits: 2,
    workloadHours: 60,
    status: 'IN_PROGRESS',
    rule: sumRule,
    overrides: { method: 'SUM', passingGrade: 7 },
    assessments,
    result: {
      standing: 'NO_ASSESSMENTS',
      totalCount: 0,
      gradedCount: 0,
      totalWeight: 0,
      gradedWeightPct: 0,
      complete: false,
    },
    attendance: { base: 60, used: 0, allowed: 15, remaining: 15, attendancePct: 100, status: 'OK' },
  };
  // O resultado vem da mesma calculadora do simulador (espelho do backend).
  return { ...base, result: apiResult(simulate(base, {}), 10) };
}

// Possível: 5,5 de 7; faltam 1,5 e a P2 (vale 4) está pendente.
const history = sumSubject('h', 'História', [
  ['P1', 3, 2.5],
  ['Trabalho', 2, 2],
  ['P2', 4, undefined],
  ['Participação', 1, 1],
]);
// Já passou: 7,5 com a participação pendente.
const arts = sumSubject('ar', 'Artes', [
  ['P1', 3, 3],
  ['Trabalho', 2, 2],
  ['P2', 4, 2.5],
  ['Participação', 1, undefined],
]);
// Impossível: faltam 5 e só restam 4.
const music = sumSubject('m', 'Música', [
  ['P1', 3, 1],
  ['Trabalho', 2, 0.5],
  ['P2', 4, undefined],
  ['Participação', 1, 0.5],
]);
// Pontuações que somam 9 (não fecham a escala).
const nine = sumSubject('n', 'Filosofia', [
  ['P1', 3, undefined],
  ['Trabalho', 2, undefined],
  ['P2', 4, undefined],
]);

function overviewWith(subjects: SubjectGrades[]): GradesOverview {
  return {
    ...emptyOverview,
    settings: {
      ...emptyOverview.settings,
      method: 'SUM',
      passingGrade: 7,
      gpaScale: 'TEN',
      gpaWeight: 'CREDITS',
    },
    semesterId: 's1',
    subjects,
  };
}

function setup(subjects: SubjectGrades[], overrides: Parameters<typeof mockApi>[0] = {}) {
  useAuthStore.getState().setSession('token', testUser);
  const fetchMock = mockApi({
    'GET /api/semesters': () => jsonResponse(200, [semester]),
    'GET /api/grades/overview': () => jsonResponse(200, overviewWith(subjects)),
    ...overrides,
  });
  const user = userEvent.setup();
  renderWithProviders(routes, { initialEntries: ['/grades'] });
  return { user, fetchMock };
}

function calls(fetchMock: ReturnType<typeof mockApi>, method: string, path: string) {
  return fetchMock.mock.calls.filter(
    ([url, init]) => String(url).startsWith(path) && (init?.method ?? 'GET') === method,
  );
}

afterEach(() => {
  cleanup();
  useAuthStore.getState().clear();
  void i18n.changeLanguage('pt-BR');
});

describe('Grade Predictor em soma de pontos', () => {
  it('cartão possível: pontos, quanto falta, quanto ainda dá para ganhar e a lista', async () => {
    setup([history]);
    const card = await screen.findByRole('article', { name: /História/ });
    expect(within(card).getByText('5,50 de 7,00 pontos')).toBeInTheDocument();
    expect(within(card).getByText(/faltam 1,50 pontos/)).toBeInTheDocument();
    expect(within(card).getByText(/pode ganhar mais até 4,00/)).toBeInTheDocument();
    expect(within(card).getByText('Precisa de 1,50 pontos para passar')).toBeInTheDocument();
    expect(within(card).getByText('Possível')).toBeInTheDocument();
    // Barra até a nota mínima, com nome acessível.
    expect(
      within(card).getByRole('progressbar', {
        name: '5,50 de 7,00 pontos até a nota mínima',
      }),
    ).toHaveAttribute('aria-valuenow', '79');
    // Lista: "vale X", nota e pílula de situação.
    const list = within(card).getByRole('list', { name: 'Avaliações' });
    expect(within(list).getByText('vale 3,00')).toBeInTheDocument();
    expect(within(list).getByText('precisa 1,50')).toBeInTheDocument();
    expect(within(list).getAllByText('lançada')).toHaveLength(3);
    // Sem peso nem "média" no modo soma.
    expect(within(card).queryByText('Média atual')).not.toBeInTheDocument();
  });

  it('cartão já passou e cartão impossível (ícone + texto, não só cor)', async () => {
    setup([arts, music]);
    const passed = await screen.findByRole('article', { name: /Artes/ });
    expect(within(passed).getByText('Já passou')).toBeInTheDocument();
    expect(
      within(passed).getByText('Passou com 7,50 pontos; o restante é bônus'),
    ).toBeInTheDocument();

    const impossible = screen.getByRole('article', { name: /Música/ });
    expect(within(impossible).getByText('Impossível passar')).toBeInTheDocument();
    expect(
      within(impossible).getByText('Faltam 5,00 pontos e só dá para ganhar mais 4,00'),
    ).toBeInTheDocument();
  });

  it('avisa quando as avaliações não fecham a escala', async () => {
    setup([nine]);
    const card = await screen.findByRole('article', { name: /Filosofia/ });
    expect(within(card).getByRole('note')).toHaveTextContent(
      'As avaliações somam 9,00 de 10,00 pontos',
    );
  });

  it('simula "e se eu tirar N" na hora, sem salvar nada', async () => {
    const { user, fetchMock } = setup([history]);
    const simulator = await screen.findByRole('region', { name: 'Simulador' });
    const input = within(simulator).getByLabelText('E se eu tirar em P2');
    expect(input).toHaveAttribute('placeholder', 'precisa 1,50');

    await user.type(input, '1,5');
    const panel = within(simulator).getByRole('region', { name: 'Simulado' });
    expect(within(panel).getByText('7,00 de 7,00 pontos')).toBeInTheDocument();
    expect(within(panel).getByText('Aprovado')).toBeInTheDocument();

    await user.clear(input);
    await user.type(input, '1,49');
    expect(within(panel).getByText('6,99 de 7,00 pontos')).toBeInTheDocument();
    expect(within(panel).getByText('Reprovado')).toBeInTheDocument();

    // Acima do que a P2 vale (4) é recusado.
    await user.clear(input);
    await user.type(input, '4,5');
    expect(within(simulator).getByText('Use uma nota de 0 a 4, com até 2 casas.')).toBeVisible();
    expect(
      fetchMock.mock.calls.filter(([, init]) => init?.method && init.method !== 'GET'),
    ).toHaveLength(0);
  });

  it('o formulário de avaliações pede "vale quantos pontos" e avisa sem bloquear', async () => {
    const { user, fetchMock } = setup([history], {
      'PUT /api/subjects/h/assessments': () => jsonResponse(200, history),
    });
    const card = await screen.findByRole('article', { name: /História/ });
    await user.click(within(card).getByRole('button', { name: 'Avaliações de História' }));
    const dialog = screen.getByRole('dialog', { name: 'Avaliações de História' });
    expect(within(dialog).getAllByLabelText('Vale quantos pontos')).toHaveLength(4);
    expect(within(dialog).queryByLabelText('Peso')).not.toBeInTheDocument();
    expect(within(dialog).getByText('Total previsto: 10,00 de 10,00 pontos')).toBeInTheDocument();
    expect(within(dialog).queryByRole('note')).not.toBeInTheDocument();

    // Participação passa a valer 0,5: soma 9,5 → aviso, mas salva.
    const worth = within(dialog).getAllByLabelText('Vale quantos pontos')[3]!;
    await user.clear(worth);
    await user.type(worth, '0.5');
    expect(within(dialog).getByRole('note')).toHaveTextContent(
      'As avaliações somam 9,50 de 10,00 pontos',
    );
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));
    await waitFor(() =>
      expect(calls(fetchMock, 'PUT', '/api/subjects/h/assessments')).toHaveLength(1),
    );
    const body = JSON.parse(
      String(calls(fetchMock, 'PUT', '/api/subjects/h/assessments')[0]![1]!.body),
    );
    expect(body.items[0]).toMatchObject({ name: 'P1', maxPoints: 3, score: 2.5, weight: null });
    expect(body.items[3]).toMatchObject({ maxPoints: 0.5, bonus: false });
  });

  it('seletor de regra: três opções com explicação e troca para soma', async () => {
    const weighted: SubjectGrades = {
      ...history,
      rule: { ...sumRule, method: 'WEIGHTED', passingGrade: 6 },
      overrides: {},
    };
    const { user, fetchMock } = setup([weighted], {
      'PUT /api/subjects/h/grading': () => jsonResponse(200, history),
    });
    const card = await screen.findByRole('article', { name: /História/ });
    await user.click(within(card).getByRole('button', { name: 'Regra de História' }));
    const dialog = screen.getByRole('dialog', { name: 'Regra de História' });
    const group = within(dialog).getByRole('group', {
      name: 'Como a sua faculdade calcula a nota?',
    });
    const sum = within(group).getByRole('radio', { name: 'Soma de pontos' });
    expect(sum).toHaveAccessibleDescription('Soma dos pontos das avaliações, aprova com 7 ou mais');
    expect(within(group).getByRole('radio', { name: 'Média ponderada' })).toBeInTheDocument();
    expect(within(group).getByRole('radio', { name: 'Média aritmética' })).toBeInTheDocument();
    expect(within(group).getByRole('radio', { name: /Padrão/ })).toBeChecked();

    await user.click(sum);
    expect(within(dialog).getByText(/Trocar a regra reinterpreta/)).toBeInTheDocument();
    // Opções da soma aparecem (arredondamento e teto).
    expect(
      within(dialog).getByLabelText('Arredondar o total antes de comparar com a mínima'),
    ).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));
    await waitFor(() => expect(calls(fetchMock, 'PUT', '/api/subjects/h/grading')).toHaveLength(1));
    const body = JSON.parse(
      String(calls(fetchMock, 'PUT', '/api/subjects/h/grading')[0]![1]!.body),
    );
    expect(body.method).toBe('SUM');
    expect(body.roundBeforeCompare).toBeNull();
    expect(body.pointsCap).toBeNull();
  });
});
