import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '@/app/router';
import i18n from '@/i18n';
import type { CockpitData, CockpitTask } from '@/lib/api/cockpit';
import type { SubjectGrades } from '@/lib/api/grades';
import { useAuthStore } from '@/stores/authStore';
import { useStackStore } from '@/stores/stackStore';
import { useUiStore } from '@/stores/uiStore';
import { jsonResponse, mockApi, renderWithProviders, testUser } from '@/test/utils';
import {
  daysUntil,
  firstName,
  greetingKey,
  isBrandNew,
  nextStep,
  onboardingSteps,
} from './nextStep';

const NOW = new Date('2026-11-20T15:00:00Z');
const inDays = (n: number) => new Date(NOW.getTime() + n * 86_400_000).toISOString();

function task(overrides: Partial<CockpitTask> = {}): CockpitTask {
  return {
    id: 't1',
    title: 'Lista 3',
    type: 'EXERCISE_LIST',
    priority: 'MEDIUM',
    status: 'TODO',
    dueAt: inDays(2),
    dueAllDay: false,
    subjectId: 's1',
    subjectName: 'Anatomia',
    subjectColor: '#6aa7ff',
    ...overrides,
  };
}

const empty: CockpitData = {
  timeZone: 'UTC',
  today: '2026-11-20',
  focus: {
    todaySeconds: 0,
    weekSeconds: 0,
    dailyGoalSeconds: 86 * 60,
    weeklyGoalSeconds: 600 * 60,
    currentStreak: 0,
    longestStreak: 0,
  },
  heatmap: { from: '2026-09-01', to: '2026-11-20', days: [] },
  tasks: { overdueCount: 0, upcomingCount: 0, overdue: [], upcoming: [] },
  flashcards: { dueToday: 0, newToday: 0, reviewsToday: 0, totalCards: 0, decks: 0 },
  atRisk: [],
  onboarding: {
    hasSemester: false,
    hasSubject: false,
    hasTask: false,
    hasFocusSession: false,
    hasDeck: false,
  },
};

const anatomy: SubjectGrades = {
  subjectId: 's1',
  semesterId: 'sem',
  name: 'Anatomia',
  color: '#6aa7ff',
  credits: 4,
  workloadHours: 60,
  status: 'IN_PROGRESS',
  rule: {
    method: 'WEIGHTED',
    scaleMax: 10,
    passingGrade: 6,
    maxAbsencePct: 25,
    attendanceBase: 60,
    finalExamEnabled: false,
    finalExamMinAverage: 4,
    finalExamAverageWeight: 50,
    finalExamPassingGrade: 5,
    roundBeforeCompare: false,
    roundingScale: 1,
  },
  overrides: {},
  assessments: [
    { id: 'a1', name: 'P1', weight: 50, score: 0 },
    { id: 'a2', name: 'P2', weight: 50 },
  ],
  result: {
    standing: 'IMPOSSIBLE',
    totalCount: 2,
    gradedCount: 1,
    totalWeight: 100,
    gradedWeightPct: 50,
    currentAverage: 0,
    requiredScore: 12,
    complete: false,
  },
  attendance: { base: 60, used: 16, allowed: 15, remaining: -1, status: 'OVER' },
};

const full: CockpitData = {
  ...empty,
  focus: {
    ...empty.focus,
    todaySeconds: 30 * 60,
    weekSeconds: 4 * 3600,
    currentStreak: 3,
    longestStreak: 9,
  },
  heatmap: { ...empty.heatmap, days: [{ date: '2026-11-20', seconds: 1800 }] },
  tasks: {
    overdueCount: 3,
    upcomingCount: 1,
    overdue: [task({ id: 'o1', title: 'Resumo atrasado', dueAt: inDays(-1) })],
    upcoming: [task({ id: 'u1', title: 'Trabalho de História', subjectName: 'História' })],
  },
  nextExam: task({ id: 'e1', title: 'Prova de Anatomia', type: 'EXAM', dueAt: inDays(10) }),
  flashcards: { dueToday: 12, newToday: 2, reviewsToday: 10, totalCards: 40, decks: 2 },
  atRisk: [{ gradeRisk: true, attendanceRisk: true, subject: anatomy }],
  onboarding: {
    hasSemester: true,
    hasSubject: true,
    hasTask: true,
    hasFocusSession: true,
    hasDeck: true,
  },
};

function setup(data: CockpitData | (() => Response | Promise<Response>)) {
  useAuthStore.getState().setSession('token', testUser);
  const fetchMock = mockApi({
    'GET /api/cockpit': typeof data === 'function' ? data : () => jsonResponse(200, data),
  });
  const user = userEvent.setup();
  const rendered = renderWithProviders(routes, { initialEntries: ['/'] });
  return { user, fetchMock, ...rendered };
}

afterEach(() => {
  cleanup();
  useAuthStore.getState().clear();
  useStackStore.setState({ dialog: null });
  useUiStore.getState().setPaletteOpen(false);
  void i18n.changeLanguage('pt-BR');
});

describe('próximo passo (lógica pura)', () => {
  const opts = { activeSession: false, now: NOW };

  it('segue a ordem de prioridade', () => {
    expect(nextStep(full, { ...opts, activeSession: true }).kind).toBe('activeSession');
    expect(nextStep(empty, opts)).toEqual({ kind: 'setup', step: 'semester' });
    expect(
      nextStep({ ...empty, onboarding: { ...empty.onboarding, hasSemester: true } }, opts),
    ).toEqual({ kind: 'setup', step: 'subject' });

    expect(nextStep(full, opts)).toMatchObject({ kind: 'overdue', count: 3 });
    const noOverdue = { ...full, tasks: { ...full.tasks, overdue: [], overdueCount: 0 } };
    // Prova a 10 dias não passa na frente das cartas; a 2 dias, passa.
    expect(nextStep(noOverdue, opts)).toEqual({ kind: 'review', cards: 12 });
    expect(
      nextStep({ ...noOverdue, nextExam: task({ type: 'EXAM', dueAt: inDays(2) }) }, opts),
    ).toMatchObject({ kind: 'exam', days: 2 });

    const noCards = { ...noOverdue, flashcards: { ...full.flashcards, dueToday: 0 } };
    // Tarefa daqui a 2 dias não é "para já"; daqui a 5 h é.
    expect(nextStep(noCards, opts)).toEqual({ kind: 'focus', remainingMinutes: 56 });
    expect(
      nextStep(
        {
          ...noCards,
          tasks: {
            ...noCards.tasks,
            upcoming: [task({ dueAt: new Date(NOW.getTime() + 5 * 3600_000).toISOString() })],
          },
        },
        opts,
      ).kind,
    ).toBe('dueSoon');
    const goalMet = { ...noCards, focus: { ...noCards.focus, todaySeconds: 2 * 3600 } };
    expect(nextStep(goalMet, opts)).toEqual({ kind: 'allDone' });
  });

  it('saudação, primeiro nome, dias e checklist', () => {
    expect(greetingKey(new Date(2026, 0, 1, 8))).toBe('morning');
    expect(greetingKey(new Date(2026, 0, 1, 13))).toBe('afternoon');
    expect(greetingKey(new Date(2026, 0, 1, 21))).toBe('evening');
    expect(greetingKey(new Date(2026, 0, 1, 3))).toBe('evening');
    expect(firstName('  Ada   Lovelace ')).toBe('Ada');
    expect(daysUntil(inDays(-2), NOW)).toBe(0);
    expect(daysUntil(inDays(1.5), NOW)).toBe(2);
    expect(onboardingSteps(empty).map((s) => s.done)).toEqual([false, false, false, false]);
    expect(isBrandNew(empty)).toBe(true);
    expect(isBrandNew(full)).toBe(false);
  });
});

describe('Cockpit', () => {
  it('mostra esqueleto enquanto o servidor responde (nunca tela em branco)', async () => {
    let release: (r: Response) => void = () => {};
    setup(() => new Promise<Response>((resolve) => (release = resolve)));
    expect(await screen.findByText('Carregando o Cockpit…')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: /Ada$/ })).toBeInTheDocument();
    release(jsonResponse(200, full));
    expect(await screen.findByRole('region', { name: 'Próximo passo' })).toBeInTheDocument();
    expect(screen.queryByText('Carregando o Cockpit…')).not.toBeInTheDocument();
  });

  it('primeiro acesso: checklist no lugar de cartões zerados', async () => {
    const { user, router } = setup(empty);
    const hero = await screen.findByRole('region', { name: 'Próximo passo' });
    expect(within(hero).getByText('Comece criando o seu semestre')).toBeInTheDocument();
    const checklist = screen.getByRole('region', { name: 'Primeiros passos' });
    expect(within(checklist).getByText('0 de 4 feitos', { selector: 'p' })).toBeInTheDocument();
    expect(within(checklist).getAllByRole('listitem')).toHaveLength(4);
    expect(screen.queryByRole('region', { name: 'Resumo do dia' })).not.toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Prazos' })).not.toBeInTheDocument();

    await user.click(within(hero).getByRole('button', { name: /Criar semestre/ }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/stack'));
    expect(useStackStore.getState().dialog).toEqual({ kind: 'semesterForm' });
  });

  it('com dados: próximo passo, resumo, prazos, radar, prova e heatmap de uma chamada só', async () => {
    const { fetchMock } = setup(full);
    const hero = await screen.findByRole('region', { name: 'Próximo passo' });
    expect(within(hero).getByText('Resolver: Resumo atrasado')).toBeInTheDocument();
    expect(within(hero).getByText(/3 tarefas atrasadas/)).toBeInTheDocument();

    const summary = screen.getByRole('region', { name: 'Resumo do dia' });
    expect(within(summary).getByText('30')).toBeInTheDocument();
    expect(within(summary).getByText('de 86 min da meta do dia')).toBeInTheDocument();
    expect(within(summary).getByText('Recorde: 9 dias')).toBeInTheDocument();
    expect(within(summary).getByText('12')).toBeInTheDocument();
    expect(
      within(summary).getByRole('progressbar', { name: 'Meta de foco de hoje' }),
    ).toHaveAttribute('aria-valuenow', '35');

    const deadlines = screen.getByRole('region', { name: 'Prazos' });
    expect(within(deadlines).getByRole('region', { name: '3 atrasadas' })).toHaveTextContent(
      'Resumo atrasado',
    );
    expect(within(deadlines).getByText('e mais 2 na The Stack')).toBeInTheDocument();
    expect(within(deadlines).getByText('Trabalho de História')).toBeInTheDocument();

    // Radar: mensagem do Grade Predictor (mesmo summary.ts) + faltas.
    const risk = screen.getByRole('region', { name: 'Radar de risco' });
    expect(
      within(risk).getByText('Nem com nota máxima no que falta dá para passar direto'),
    ).toBeInTheDocument();
    expect(within(risk).getByText('Faltas acima do limite: 16 de 15')).toBeInTheDocument();

    const exam = screen.getByRole('region', { name: 'Próxima prova' });
    expect(within(exam).getByText('Prova de Anatomia')).toBeInTheDocument();
    expect(within(exam).getByText(/^Em \d+ dias$/)).toBeInTheDocument();

    expect(screen.getByRole('region', { name: 'Últimas semanas' })).toBeInTheDocument();
    // Checklist completo some.
    expect(screen.queryByRole('region', { name: 'Primeiros passos' })).not.toBeInTheDocument();

    expect(
      fetchMock.mock.calls.filter(([url]) => String(url).startsWith('/api/cockpit')),
    ).toHaveLength(1);
    expect(
      String(fetchMock.mock.calls.find(([u]) => String(u).startsWith('/api/cockpit'))![0]),
    ).toMatch(/tz=/);
    // A home não chama as telas das fases (tarefas, decks) por conta própria. As notas aparecem
    // uma vez só, pela grade de matérias da barra lateral (Fase 14), não pela home.
    for (const path of ['/api/tasks', '/api/flashcards/decks']) {
      expect(
        fetchMock.mock.calls.some(([url]) => String(url).startsWith(path)),
        path,
      ).toBe(false);
    }
    expect(
      fetchMock.mock.calls.filter(([url]) => String(url).startsWith('/api/grades/overview')).length,
    ).toBeLessThanOrEqual(1);
  });

  it('ações rápidas: nova tarefa abre o diálogo na The Stack', async () => {
    const { user, router } = setup(full);
    const actions = await screen.findByRole('navigation', { name: 'Ações rápidas' });
    await user.click(within(actions).getByRole('button', { name: 'Nova tarefa' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/stack'));
    expect(useStackStore.getState().dialog).toEqual({ kind: 'task' });
  });

  it('erro com botão de tentar de novo', async () => {
    let calls = 0;
    const { user } = setup(() => {
      calls++;
      return calls === 1
        ? jsonResponse(500, { code: 'INTERNAL_ERROR', message: 'x' })
        : jsonResponse(200, full);
    });
    expect(await screen.findByText('Não foi possível carregar o Cockpit.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Tentar novamente' }));
    expect(await screen.findByRole('region', { name: 'Próximo passo' })).toBeInTheDocument();
  });

  it('Ctrl K: navega para todas as telas, inclusive Perfil e seções da conta', async () => {
    const { user, router } = setup(full);
    await screen.findByRole('region', { name: 'Próximo passo' });
    await user.keyboard('{Control>}k{/Control}');
    const palette = await screen.findByRole('dialog');
    for (const page of [
      'Cockpit',
      'The Stack',
      'Deep Work',
      'Flashcards',
      'Grade Predictor',
      'Perfil',
    ]) {
      expect(within(palette).getByText(`Ir para ${page}`)).toBeInTheDocument();
    }
    expect(within(palette).getByText('Revisar flashcards')).toBeInTheDocument();
    expect(within(palette).getByText('Nova tarefa')).toBeInTheDocument();
    await user.click(within(palette).getByText('Exportar meus dados'));
    await waitFor(() => expect(router.state.location.pathname).toBe('/profile'));
    expect(router.state.location.hash).toBe('#privacidade');
  });
});
