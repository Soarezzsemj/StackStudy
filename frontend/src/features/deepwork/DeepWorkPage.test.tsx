import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '@/app/router';
import i18n from '@/i18n';
import type { DeepWorkStats, HeatmapData, StudySession } from '@/lib/api/deepwork';
import type { Semester, Subject } from '@/lib/api/stack';
import { useAuthStore } from '@/stores/authStore';
import { useDeepWorkStore } from '@/stores/deepWorkStore';
import { jsonResponse, mockApi, renderWithProviders, testUser } from '@/test/utils';

const semester: Semester = {
  id: 's1',
  name: '2026.2',
  startDate: '2026-08-01',
  endDate: '2026-12-15',
  archived: false,
  current: true,
};
const anatomy: Subject = {
  id: 'sub1',
  semesterId: 's1',
  name: 'Anatomia',
  credits: 4,
  color: '#bd6cf7',
  status: 'IN_PROGRESS',
  pendingTasks: 0,
};

const stats: DeepWorkStats = {
  timeZone: 'UTC',
  today: '2026-11-20',
  todaySeconds: 3600,
  weekSeconds: 5 * 3600,
  monthSeconds: 20 * 3600,
  last30DaysSeconds: 30 * 3600,
  dailyAverageSeconds: 3600,
  currentStreak: 3,
  longestStreak: 9,
  weeklyGoalSeconds: 10 * 3600,
  weekBySubject: [{ subjectId: 'sub1', name: 'Anatomia', color: '#bd6cf7', seconds: 5 * 3600 }],
  monthBySubject: [],
};

const heatmap: HeatmapData = {
  from: '2025-11-21',
  to: '2026-11-20',
  timeZone: 'UTC',
  days: [{ date: '2026-11-18', seconds: 5400 }],
};

/** Sessão ativa que começou há `minutesAgo` minutos (relógio do servidor = agora). */
function activeSession(minutesAgo: number, partial: Partial<StudySession> = {}): StudySession {
  const now = Date.now();
  return {
    id: 'sess1',
    subjectId: 'sub1',
    mode: 'POMODORO',
    source: 'TIMER',
    plannedMinutes: 25,
    startedAt: new Date(now - minutesAgo * 60_000).toISOString(),
    pausedSeconds: 0,
    effectiveSeconds: minutesAgo * 60,
    active: true,
    serverTime: new Date(now).toISOString(),
    ...partial,
  };
}

function setup(overrides: Parameters<typeof mockApi>[0] = {}, path = '/deep-work') {
  useAuthStore.getState().setSession('token', testUser);
  const fetchMock = mockApi({
    'GET /api/semesters': () => jsonResponse(200, [semester]),
    'GET /api/subjects': () => jsonResponse(200, [anatomy]),
    'GET /api/deepwork/stats': () => jsonResponse(200, stats),
    'GET /api/deepwork/heatmap': () => jsonResponse(200, heatmap),
    'GET /api/deepwork/settings': () =>
      jsonResponse(200, {
        focusMinutes: 25,
        shortBreakMinutes: 5,
        longBreakMinutes: 15,
        longBreakEvery: 4,
        weeklyGoalMinutes: 600,
        soundEnabled: false,
        notificationsEnabled: false,
      }),
    ...overrides,
  });
  const utils = renderWithProviders(routes, { initialEntries: [path] });
  return { ...utils, fetchMock };
}

function calls(fetchMock: ReturnType<typeof mockApi>, method: string, path: string) {
  return fetchMock.mock.calls.filter(
    ([url, init]) => (init?.method ?? 'GET') === method && String(url).split('?')[0] === path,
  );
}

afterEach(async () => {
  cleanup();
  await i18n.changeLanguage('pt-BR');
});

describe('Deep Work page', () => {
  it('starts a pomodoro for the chosen subject', async () => {
    const { fetchMock } = setup({
      'POST /api/deepwork/sessions': () => jsonResponse(201, activeSession(0)),
    });
    expect(await screen.findByRole('timer')).toHaveTextContent('25:00');
    const start = screen.getByRole('button', { name: 'Iniciar foco' });
    expect(start).toBeDisabled();

    await userEvent.selectOptions(await screen.findByRole('combobox', { name: 'Matéria' }), 'sub1');
    await userEvent.click(start);

    await waitFor(() => expect(calls(fetchMock, 'POST', '/api/deepwork/sessions')).toHaveLength(1));
    const body = JSON.parse(
      String(calls(fetchMock, 'POST', '/api/deepwork/sessions')[0]![1]!.body),
    );
    expect(body).toEqual({ subjectId: 'sub1', taskId: null, mode: 'POMODORO', plannedMinutes: 25 });
    expect(await screen.findByRole('button', { name: 'Pausar' })).toBeInTheDocument();
  });

  it('restores the active session from the server timestamps (survives reload)', async () => {
    setup({ 'GET /api/deepwork/sessions/active': () => jsonResponse(200, activeSession(10)) });
    // 25 min planejados, 10 já passaram: faltam ~15:00.
    const timer = await screen.findByRole('timer');
    await waitFor(() => expect(timer.textContent).toMatch(/^1[45]:\d\d$/));
    expect(screen.getByRole('button', { name: 'Pausar' })).toBeInTheDocument();
    // Título da aba mostra o tempo restante.
    await waitFor(() => expect(document.title).toMatch(/^1[45]:\d\d · Foco$/));
    // A barra de status mostra a sessão em qualquer tela.
    const status = screen.getByRole('contentinfo', { name: 'Barra de status' });
    expect(within(status).getByText('Anatomia')).toBeInTheDocument();
  });

  it('Space pauses the active session and resumes a paused one', async () => {
    const { fetchMock } = setup({
      'GET /api/deepwork/sessions/active': () => jsonResponse(200, activeSession(5)),
      'POST /api/deepwork/sessions/sess1/pause': () =>
        jsonResponse(200, activeSession(5, { pausedAt: new Date().toISOString() })),
      'POST /api/deepwork/sessions/sess1/resume': () => jsonResponse(200, activeSession(5)),
    });
    await screen.findByRole('button', { name: 'Pausar' });
    await userEvent.keyboard(' ');
    await waitFor(() =>
      expect(calls(fetchMock, 'POST', '/api/deepwork/sessions/sess1/pause')).toHaveLength(1),
    );
    expect(await screen.findByRole('button', { name: 'Retomar' })).toBeInTheDocument();
    await userEvent.keyboard(' ');
    await waitFor(() =>
      expect(calls(fetchMock, 'POST', '/api/deepwork/sessions/sess1/resume')).toHaveLength(1),
    );
  });

  it('completes automatically when the pomodoro ends and starts the break', async () => {
    const { fetchMock } = setup({
      // Reabriu o app 30 min depois de iniciar um pomodoro de 25.
      'GET /api/deepwork/sessions/active': () => jsonResponse(200, activeSession(30)),
      'POST /api/deepwork/sessions/sess1/complete': () =>
        jsonResponse(200, activeSession(30, { active: false, effectiveSeconds: 1500 })),
    });
    await waitFor(() =>
      expect(calls(fetchMock, 'POST', '/api/deepwork/sessions/sess1/complete')).toHaveLength(1),
    );
    await waitFor(() => expect(useDeepWorkStore.getState().breakState?.kind).toBe('shortBreak'));
    expect(useDeepWorkStore.getState().completedFocus).toBe(1);
    expect(await screen.findByText('Pausa curta')).toBeInTheDocument();
    expect(screen.getByRole('timer')).toHaveTextContent(/^0[45]:\d\d$/);
  });

  it('completes with a commit message', async () => {
    const { fetchMock } = setup({
      'GET /api/deepwork/sessions/active': () => jsonResponse(200, activeSession(12)),
      'POST /api/deepwork/sessions/sess1/complete': () =>
        jsonResponse(200, activeSession(12, { active: false })),
    });
    await userEvent.click(await screen.findByRole('button', { name: 'Concluir' }));
    const dialog = await screen.findByRole('dialog', { name: 'Concluir sessão' });
    await userEvent.type(
      within(dialog).getByRole('textbox', { name: 'Commit message da sessão' }),
      'revisei ossos do crânio',
    );
    await userEvent.click(within(dialog).getByRole('button', { name: 'Concluir' }));
    await waitFor(() =>
      expect(calls(fetchMock, 'POST', '/api/deepwork/sessions/sess1/complete')).toHaveLength(1),
    );
    const body = JSON.parse(
      String(calls(fetchMock, 'POST', '/api/deepwork/sessions/sess1/complete')[0]![1]!.body),
    );
    expect(body).toEqual({ note: 'revisei ossos do crânio', focusRating: null });
  });

  it('shows stats, goal progress, by-subject hours and the heatmap', async () => {
    setup();
    expect(await screen.findByText('Sequência')).toBeInTheDocument();
    expect(screen.getByText('Maior: 9 dias')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: 'Progresso da meta semanal' })).toHaveAttribute(
      'aria-valuenow',
      '50',
    );
    const heat = await screen.findByRole('img', { name: /Heatmap de foco/ });
    const lit = heat.querySelectorAll('rect[data-level="3"]');
    expect(lit).toHaveLength(1);
    expect(lit[0]!.textContent).toMatch(/^1 h 30 min em /);
    expect(screen.getByText('Nenhuma sessão ainda')).toBeInTheDocument();
  });

  it('logs a manual session', async () => {
    const { fetchMock } = setup({
      'POST /api/deepwork/sessions/manual': () =>
        jsonResponse(201, activeSession(0, { active: false, source: 'MANUAL' })),
    });
    await userEvent.click(await screen.findByRole('button', { name: 'Registrar sessão' }));
    const dialog = await screen.findByRole('dialog', { name: 'Registrar sessão' });
    const minutes = within(dialog).getByRole('spinbutton', { name: 'Duração (min)' });
    await userEvent.clear(minutes);
    await userEvent.type(minutes, '45');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }));
    await waitFor(() =>
      expect(calls(fetchMock, 'POST', '/api/deepwork/sessions/manual')).toHaveLength(1),
    );
    const body = JSON.parse(
      String(calls(fetchMock, 'POST', '/api/deepwork/sessions/manual')[0]![1]!.body),
    );
    expect(body).toMatchObject({ subjectId: 'sub1', durationMinutes: 45 });
    expect(new Date(body.startedAt).getHours()).toBe(9);
  });

  it('Ctrl K starts focus on a subject from another page', async () => {
    const { fetchMock, router } = setup(
      { 'POST /api/deepwork/sessions': () => jsonResponse(201, activeSession(0)) },
      '/',
    );
    await screen.findByRole('heading', { level: 1, name: 'Cockpit' });
    await userEvent.keyboard('{Control>}k{/Control}');
    await userEvent.click(await screen.findByRole('option', { name: /Iniciar foco em Anatomia/ }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/deep-work'));
    await waitFor(() => expect(calls(fetchMock, 'POST', '/api/deepwork/sessions')).toHaveLength(1));
  });

  it('renders in English', async () => {
    await i18n.changeLanguage('en');
    setup();
    expect(await screen.findByRole('button', { name: 'Start focus' })).toBeInTheDocument();
    expect(await screen.findByText('Hours by subject')).toBeInTheDocument();
  });
});
