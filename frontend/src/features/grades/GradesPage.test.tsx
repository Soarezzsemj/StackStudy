import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '@/app/router';
import i18n from '@/i18n';
import type { GradesOverview, SubjectGrades } from '@/lib/api/grades';
import type { Semester, Subject } from '@/lib/api/stack';
import { useAuthStore } from '@/stores/authStore';
import { emptyOverview, jsonResponse, mockApi, renderWithProviders, testUser } from '@/test/utils';

const semester: Semester = {
  id: 's1',
  name: '2026.2',
  startDate: '2026-08-01',
  endDate: '2026-12-15',
  archived: false,
  current: true,
};

const rule = {
  method: 'WEIGHTED' as const,
  scaleMax: 10,
  passingGrade: 6,
  maxAbsencePct: 25,
  attendanceBase: 60,
  finalExamEnabled: true,
  finalExamMinAverage: 4,
  finalExamAverageWeight: 50,
  finalExamPassingGrade: 5,
  roundBeforeCompare: false,
  roundingScale: 1,
};

// Cálculo: P1 = 4 (peso 50), P2 sem nota (peso 50) → precisa 7,99; em risco.
const calculus: SubjectGrades = {
  subjectId: 'sub1',
  semesterId: 's1',
  name: 'Cálculo I',
  color: '#6aa7ff',
  credits: 4,
  workloadHours: 60,
  status: 'IN_PROGRESS',
  rule,
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
    currentAverage: 4,
    minimumAverage: 2,
    maximumAverage: 7,
    requiredScore: 7.99,
    finalGrade: 4,
    complete: false,
  },
  attendance: {
    base: 60,
    used: 12,
    allowed: 15,
    remaining: 3,
    attendancePct: 80,
    status: 'WARNING',
  },
};

// Direito: já não dá para passar direto nem com exame.
const law: SubjectGrades = {
  ...calculus,
  subjectId: 'sub2',
  name: 'Direito',
  color: '#f2b861',
  rule: { ...rule, finalExamEnabled: false },
  assessments: [
    { id: 'b1', name: 'P1', weight: 50, score: 1 },
    { id: 'b2', name: 'P2', weight: 50 },
  ],
  result: {
    ...calculus.result,
    standing: 'IMPOSSIBLE',
    currentAverage: 1,
    requiredScore: 10.99,
    finalGrade: 1,
  },
  attendance: {
    base: 60,
    used: 16,
    allowed: 15,
    remaining: -1,
    attendancePct: 73.3,
    status: 'OVER',
  },
};

const overview: GradesOverview = {
  ...emptyOverview,
  settings: {
    ...emptyOverview.settings,
    method: 'WEIGHTED',
    gpaScale: 'TEN',
    gpaWeight: 'CREDITS',
  },
  semesterId: 's1',
  subjects: [calculus, law],
  gpa: {
    scale: 'TEN',
    weightBasis: 'CREDITS',
    cumulative: 7.25,
    cumulativeSubjects: 3,
    cumulativeProvisional: 2,
    semesters: [
      {
        semesterId: 's1',
        name: '2026.2',
        startDate: '2026-08-01',
        current: true,
        value: 2.5,
        subjects: 2,
        totalWeight: 8,
        provisional: 2,
        excludedNoWeight: 1,
      },
    ],
  },
};

function setup(overrides: Parameters<typeof mockApi>[0] = {}, path = '/grades') {
  useAuthStore.getState().setSession('token', testUser);
  const fetchMock = mockApi({
    'GET /api/semesters': () => jsonResponse(200, [semester]),
    'GET /api/grades/overview': () => jsonResponse(200, overview),
    'GET /api/subjects/sub1/absences': () =>
      jsonResponse(200, [{ id: 'ab1', subjectId: 'sub1', date: '2026-09-01', count: 2 }]),
    ...overrides,
  });
  const utils = renderWithProviders(routes, { initialEntries: [path] });
  return { ...utils, fetchMock, user: userEvent.setup() };
}

function calls(fetchMock: ReturnType<typeof mockApi>, method: string, path: string) {
  return fetchMock.mock.calls.filter(
    ([url, init]) => String(url).split('?')[0] === path && (init?.method ?? 'GET') === method,
  );
}

afterEach(async () => {
  cleanup();
  useAuthStore.getState().clear();
  await i18n.changeLanguage('pt-BR');
});

describe('GradesPage', () => {
  it('mostra os cartões com média, nota necessária, situação e faltas', async () => {
    setup();
    const card = await screen.findByRole('article', { name: /Cálculo I/ });
    expect(within(card).getByText('Em risco')).toBeInTheDocument();
    expect(within(card).getByText('4,00')).toBeInTheDocument();
    expect(within(card).getByText('7,99')).toBeInTheDocument();
    expect(within(card).getByText('12 de 15')).toBeInTheDocument();
    expect(within(card).getByText('Perto do limite')).toBeInTheDocument();

    const lawCard = screen.getByRole('article', { name: /Direito/ });
    expect(within(lawCard).getByText('Impossível passar')).toBeInTheDocument();
    expect(within(lawCard).getByText('máx. 10')).toBeInTheDocument();
    expect(
      within(lawCard).getByText('Nem com nota máxima em tudo o que falta dá para passar.'),
    ).toBeInTheDocument();
    expect(within(lawCard).getByText('Limite ultrapassado')).toBeInTheDocument();
  });

  it('mostra o CR do semestre e o acumulado', async () => {
    setup();
    const gpa = await screen.findByRole('region', { name: 'Coeficiente de rendimento' });
    expect(within(gpa).getByText('2,50')).toBeInTheDocument();
    expect(within(gpa).getByText('7,25')).toBeInTheDocument();
    expect(within(gpa).getByText('1 matéria sem peso não entra')).toBeInTheDocument();
    expect(within(gpa).getByText('Matérias trancadas não entram no CR.')).toBeInTheDocument();
  });

  it('simula "e se" na hora, sem salvar', async () => {
    const { user, fetchMock } = setup();
    const simulator = await screen.findByRole('region', { name: 'Simulador' });
    const input = within(simulator).getByLabelText('E se eu tirar em P2');
    expect(input).toHaveAttribute('placeholder', 'precisa 7,99');

    // P2 = 5 → média 4,50 → vai para o exame, que precisa de 5,49.
    await user.type(input, '5');
    const result = within(simulator).getByRole('region', { name: 'Simulado' });
    expect(within(result).getByText('4,50')).toBeInTheDocument();
    expect(within(result).getByText('Exame final')).toBeInTheDocument();
    expect(within(result).getByText('5,49')).toBeInTheDocument();

    // Exame hipotético 6 → aprovado.
    await user.type(within(simulator).getByLabelText('Exame final (hipotético)'), '6');
    expect(within(result).getByText('Aprovado')).toBeInTheDocument();

    // Nota inválida avisa.
    await user.clear(input);
    await user.type(input, '11');
    expect(within(simulator).getByText('Use uma nota de 0 a 10, com até 2 casas.')).toBeVisible();

    await user.click(within(simulator).getByRole('button', { name: 'Limpar simulação' }));
    expect(within(simulator).getByLabelText('E se eu tirar em P2')).toHaveValue('');
    // Nada foi enviado ao servidor.
    expect(fetchMock.mock.calls.every(([, init]) => (init?.method ?? 'GET') === 'GET')).toBe(true);
  });

  it('troca a matéria simulada pelo cartão', async () => {
    const { user } = setup();
    const lawCard = await screen.findByRole('article', { name: /Direito/ });
    await user.click(within(lawCard).getByRole('button', { name: 'Simular Direito' }));
    const simulator = screen.getByRole('region', { name: 'Simulador' });
    expect(within(simulator).getByLabelText('Matéria')).toHaveValue('sub2');
    expect(within(simulator).getByLabelText('E se eu tirar em P2')).not.toHaveAttribute(
      'placeholder',
    );
  });

  it('salva as avaliações da matéria', async () => {
    const { user, fetchMock } = setup({
      'PUT /api/subjects/sub1/assessments': () => jsonResponse(200, calculus),
    });
    const card = await screen.findByRole('article', { name: /Cálculo I/ });
    await user.click(within(card).getByRole('button', { name: 'Avaliações de Cálculo I' }));
    const dialog = screen.getByRole('dialog', { name: 'Avaliações de Cálculo I' });
    await user.click(within(dialog).getByRole('button', { name: 'Adicionar avaliação' }));
    const names = within(dialog).getAllByLabelText('Nome');
    await user.type(names[2]!, 'Trabalho');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    await waitFor(() =>
      expect(calls(fetchMock, 'PUT', '/api/subjects/sub1/assessments')).toHaveLength(1),
    );
    const body = JSON.parse(
      String(calls(fetchMock, 'PUT', '/api/subjects/sub1/assessments')[0]![1]!.body),
    );
    expect(body.items).toEqual([
      { id: 'a1', name: 'P1', weight: 50, score: 4 },
      { id: 'a2', name: 'P2', weight: 50, score: null },
      { name: 'Trabalho', weight: 1, score: null },
    ]);
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('mostra o erro de validação por campo vindo do backend', async () => {
    const { user } = setup({
      'PUT /api/subjects/sub1/assessments': () =>
        jsonResponse(400, {
          code: 'VALIDATION_ERROR',
          message: 'Request validation failed',
          details: [{ field: 'items[0].score', message: 'must not exceed the scale (10)' }],
        }),
    });
    const card = await screen.findByRole('article', { name: /Cálculo I/ });
    await user.click(within(card).getByRole('button', { name: 'Avaliações de Cálculo I' }));
    const dialog = screen.getByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));
    expect(await within(dialog).findByText('must not exceed the scale (10)')).toBeInTheDocument();
    expect(within(dialog).getByText('Confira os campos destacados.')).toBeInTheDocument();
  });

  it('registra falta por data e lista os registros', async () => {
    const { user, fetchMock } = setup({
      'POST /api/subjects/sub1/absences': () =>
        jsonResponse(201, { id: 'ab2', subjectId: 'sub1', date: '2026-09-08', count: 2 }),
    });
    const panel = await screen.findByRole('region', { name: 'Faltas' });
    await user.click(within(panel).getByRole('button', { name: 'Faltas de Cálculo I' }));
    const dialog = screen.getByRole('dialog', { name: 'Registrar falta' });
    expect(await within(dialog).findByText('2 aulas')).toBeInTheDocument();

    const date = within(dialog).getByLabelText('Data');
    await user.clear(date);
    await user.type(date, '2026-09-08');
    await user.click(within(dialog).getByRole('button', { name: 'Registrar' }));
    await waitFor(() =>
      expect(calls(fetchMock, 'POST', '/api/subjects/sub1/absences')).toHaveLength(1),
    );
    const body = JSON.parse(
      String(calls(fetchMock, 'POST', '/api/subjects/sub1/absences')[0]![1]!.body),
    );
    expect(body).toEqual({ date: '2026-09-08', count: 2 });
  });

  it('traduz o conflito de falta repetida', async () => {
    const { user } = setup({
      'POST /api/subjects/sub1/absences': () =>
        jsonResponse(409, { code: 'ABSENCE_ALREADY_REGISTERED', message: 'x' }),
    });
    const panel = await screen.findByRole('region', { name: 'Faltas' });
    await user.click(within(panel).getByRole('button', { name: 'Faltas de Cálculo I' }));
    const dialog = screen.getByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Registrar' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'Já existe falta registrada nesta data.',
    );
  });

  it('salva a regra da matéria só com os campos preenchidos', async () => {
    const { user, fetchMock } = setup({
      'PUT /api/subjects/sub1/grading': () => jsonResponse(200, calculus),
    });
    const card = await screen.findByRole('article', { name: /Cálculo I/ });
    await user.click(within(card).getByRole('button', { name: 'Regra de Cálculo I' }));
    const dialog = screen.getByRole('dialog', { name: 'Regra de Cálculo I' });
    expect(within(dialog).getByLabelText('Nota mínima para aprovação')).toHaveAttribute(
      'placeholder',
      'Padrão: 6',
    );
    await user.type(within(dialog).getByLabelText('Nota mínima para aprovação'), '7');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));
    await waitFor(() =>
      expect(calls(fetchMock, 'PUT', '/api/subjects/sub1/grading')).toHaveLength(1),
    );
    const body = JSON.parse(
      String(calls(fetchMock, 'PUT', '/api/subjects/sub1/grading')[0]![1]!.body),
    );
    expect(body.passingGrade).toBe(7);
    expect(body.scaleMax).toBeNull();
    expect(body.method).toBeNull();
    expect(body.finalExamEnabled).toBeNull();
  });

  it('estado vazio sem semestre', async () => {
    setup({ 'GET /api/grades/overview': () => jsonResponse(200, emptyOverview) });
    expect(await screen.findByText('Nenhum semestre ainda')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir para The Stack' })).toHaveAttribute(
      'href',
      '/stack',
    );
  });

  it('estado vazio sem matérias e sem avaliações', async () => {
    setup({
      'GET /api/grades/overview': () =>
        jsonResponse(200, { ...overview, subjects: [] } satisfies GradesOverview),
    });
    expect(await screen.findByText('Nenhuma matéria neste semestre')).toBeInTheDocument();
  });

  it('erro com tentar de novo', async () => {
    let fail = true;
    const { user } = setup({
      'GET /api/grades/overview': () =>
        fail
          ? jsonResponse(500, { code: 'INTERNAL_ERROR', message: 'x' })
          : jsonResponse(200, overview),
    });
    expect(await screen.findByText('Não foi possível carregar as notas.')).toBeInTheDocument();
    fail = false;
    await user.click(screen.getByRole('button', { name: 'Tentar novamente' }));
    expect(await screen.findByRole('article', { name: /Cálculo I/ })).toBeInTheDocument();
  });

  it('em inglês', async () => {
    await i18n.changeLanguage('en');
    setup();
    const card = await screen.findByRole('article', { name: /Cálculo I/ });
    expect(within(card).getByText('At risk')).toBeInTheDocument();
    expect(within(card).getByText('7.99')).toBeInTheDocument();
  });
});

describe('integração com a The Stack', () => {
  it('a lista de matérias mostra média e faltas com a cor de risco', async () => {
    const subject: Subject = {
      id: 'sub1',
      semesterId: 's1',
      name: 'Cálculo I',
      credits: 4,
      color: '#6aa7ff',
      status: 'IN_PROGRESS',
      pendingTasks: 0,
    };
    setup({ 'GET /api/subjects': () => jsonResponse(200, [subject]) }, '/stack');
    const panel = await screen.findByRole('region', { name: 'Matérias' });
    const button = await within(panel).findByRole('button', { name: /^Cálculo I/, pressed: false });
    await waitFor(() => expect(button).toHaveTextContent('Média 4,00'));
    expect(button).toHaveTextContent('12 faltas');
    expect(button).toHaveTextContent('Perto do limite');
  });
});
