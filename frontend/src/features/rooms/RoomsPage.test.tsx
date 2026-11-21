import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, renderHook, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { routes } from '@/app/router';
import type { Presence, RoomDetail, RoomSession } from '@/lib/api/rooms';
import { useAuthStore } from '@/stores/authStore';
import { jsonResponse, mockApi, renderWithProviders, testUser } from '@/test/utils';
import { usePresence } from './queries';
import {
  confirmSecondsLeft,
  formatCodeInput,
  goalPercent,
  memberSeconds,
  phaseAt,
  sessionSeconds,
  validNickname,
} from './roomLogic';

const iso = (offsetSeconds = 0) => new Date(Date.now() + offsetSeconds * 1000).toISOString();

function detail(partial: Partial<RoomDetail> = {}): RoomDetail {
  return {
    id: 'r1',
    name: 'Turma de Direito',
    visibility: 'PRIVATE',
    owner: true,
    inviteCode: 'ABCD-1234-EFGH',
    memberCount: 3,
    maxMembers: 20,
    weeklyGoalHours: 40,
    weekSeconds: 36_000,
    defaultMode: 'FREE',
    // Ciclo que começou há 10 min: foco, ciclo 1, faltam 15 min.
    pomodoro: {
      focusMinutes: 25,
      shortBreakMinutes: 5,
      longBreakMinutes: 15,
      longBreakEvery: 4,
      cycleStartedAt: iso(-600),
    },
    phase: { phase: 'FOCUS', cycle: 1, of: 4, startedAt: iso(-600), endsAt: iso(900) },
    me: {
      nickname: 'Ana',
      role: 'OWNER',
      showSubject: false,
      hideFromRanking: false,
      muted: false,
    },
    myStats: { todaySeconds: 1800, weekSeconds: 7200, longestSeconds: 5400 },
    timing: {
      heartbeatSeconds: 30,
      absentAfterSeconds: 120,
      saveEveryMinutes: 30,
      confirmEveryMinutes: 60,
      confirmGraceMinutes: 5,
    },
    serverTime: iso(),
    ...partial,
  };
}

const presence: Presence = {
  studyingNow: 1,
  phase: { phase: 'FOCUS', cycle: 1, of: 4, startedAt: iso(-600), endsAt: iso(900) },
  members: [
    {
      userId: 'u1',
      nickname: 'Ana',
      role: 'OWNER',
      me: true,
      muted: false,
      state: 'OFFLINE',
      sessionSeconds: 0,
      cyclesCompleted: 0,
    },
    {
      userId: 'u2',
      nickname: 'Bia',
      role: 'MEMBER',
      me: false,
      muted: false,
      state: 'STUDYING',
      mode: 'FREE',
      since: iso(-3600),
      sessionSeconds: 3600,
      cyclesCompleted: 0,
      subject: 'Direito Civil',
    },
    {
      userId: 'u3',
      nickname: 'Caio',
      role: 'MEMBER',
      me: false,
      muted: false,
      state: 'PAUSED',
      mode: 'POMODORO',
      since: iso(-3600),
      sessionSeconds: 1500,
      cyclesCompleted: 2,
    },
    {
      userId: 'u4',
      nickname: 'Duda',
      role: 'MEMBER',
      me: false,
      muted: false,
      state: 'AWAY',
      mode: 'FREE',
      since: iso(-3600),
      sessionSeconds: 600,
      cyclesCompleted: 0,
    },
  ],
  reactions: [],
  serverTime: iso(),
};

/** Sessão ativa do Deep Work consistente com a sala (o servidor mostra a sessão da sala ali). */
function roomActive(session: RoomSession) {
  return {
    'GET /api/deepwork/sessions/active': () =>
      jsonResponse(200, {
        id: session.sessionId,
        subjectId: '',
        roomId: session.roomId,
        mode: 'FLOW',
        source: 'ROOM',
        startedAt: session.startedAt,
        pausedSeconds: 0,
        effectiveSeconds: session.countedSeconds,
        active: true,
        serverTime: iso(),
      }),
    'POST /api/rooms/r1/session/heartbeat': () => jsonResponse(200, session),
  };
}

const ranking = {
  period: 'week',
  mode: 'FREE',
  entries: [{ position: 1, nickname: 'Bia', me: false, seconds: 7200, cycles: 0 }],
};

function setup(overrides: Parameters<typeof mockApi>[0] = {}, path = '/rooms/r1') {
  useAuthStore.getState().setSession('token', testUser);
  const fetchMock = mockApi({
    'GET /api/rooms/mine': () =>
      jsonResponse(200, [
        {
          id: 'r1',
          name: 'Turma de Direito',
          visibility: 'PRIVATE',
          role: 'OWNER',
          memberCount: 3,
          maxMembers: 20,
          studyingNow: 1,
          defaultMode: 'FREE',
        },
      ]),
    'GET /api/rooms/public': () => jsonResponse(200, []),
    'GET /api/rooms/r1': () => jsonResponse(200, detail()),
    'GET /api/rooms/r1/presence': () => jsonResponse(200, presence),
    'GET /api/rooms/r1/ranking': () => jsonResponse(200, ranking),
    'GET /api/users/blocks': () => jsonResponse(200, []),
    ...overrides,
  });
  const user = userEvent.setup();
  const rendered = renderWithProviders(routes, { initialEntries: [path] });
  return { user, fetchMock, ...rendered };
}

function calls(fetchMock: ReturnType<typeof mockApi>, method: string, path: string) {
  return fetchMock.mock.calls.filter(
    ([url, init]) => (init?.method ?? 'GET') === method && String(url).split('?')[0] === path,
  );
}

afterEach(() => {
  cleanup();
  useAuthStore.getState().clear();
  vi.restoreAllMocks();
});

describe('Salas de estudo', () => {
  it('sem salas: convida a criar ou entrar com código e avisa que não há chat', async () => {
    setup({ 'GET /api/rooms/mine': () => jsonResponse(200, []) }, '/rooms');
    expect(await screen.findByText('Você ainda não está em nenhuma sala')).toBeInTheDocument();
    expect(screen.getByRole('note')).toHaveTextContent('Aqui não existe chat');
    expect(screen.getByRole('button', { name: 'Criar sala' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Entrar com código' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Salas de estudo' })).toHaveAttribute('href', '/rooms');
  });

  it('alterna entre Tempo corrido e Pomodoro no topo do cartão', async () => {
    const { user, fetchMock } = setup();
    const card = await screen.findByRole('region', { name: 'Sala Turma de Direito' });
    const modes = within(card).getByRole('radiogroup', { name: 'Modo de contagem' });
    expect(within(modes).getByRole('radio', { name: 'Tempo corrido' })).toHaveAttribute(
      'aria-checked',
      'true',
    );
    expect(within(card).getByText(/Cronômetro contínuo/)).toBeInTheDocument();
    expect(within(card).getByRole('timer', { name: 'Tempo da sessão: 00:00' })).toBeInTheDocument();

    await user.click(within(modes).getByRole('radio', { name: 'Pomodoro' }));
    expect(within(modes).getByRole('radio', { name: 'Pomodoro' })).toHaveAttribute(
      'aria-checked',
      'true',
    );
    expect(within(card).getByText(/Ciclos de foco e pausa iguais para todos/)).toBeInTheDocument();
    expect(within(card).getByText('Foco · ciclo 1 de 4')).toBeInTheDocument();
    expect(
      within(card).getByRole('timer', { name: /Faltam 1[45]:\d\d nesta fase/ }),
    ).toBeInTheDocument();
    // O ranking passa a ser por ciclos.
    await waitFor(() =>
      expect(
        calls(fetchMock, 'GET', '/api/rooms/r1/ranking').some(([url]) =>
          String(url).includes('mode=POMODORO'),
        ),
      ).toBe(true),
    );
    expect(screen.getByText('Por ciclos concluídos (Pomodoro).')).toBeInTheDocument();
    // Meta da sala e a nota sobre salvar e confirmar.
    expect(within(card).getByText(/salvo a cada 30 minutos/)).toBeInTheDocument();
    expect(
      within(card).getByRole('progressbar', { name: /Meta semanal da sala: 10 h 00 min de 40 h/ }),
    ).toBeInTheDocument();
  });

  it('o cronômetro sai do timestamp do servidor, não de um contador local', async () => {
    const session: RoomSession = {
      sessionId: 's1',
      roomId: 'r1',
      mode: 'FREE',
      state: 'STUDYING',
      startedAt: iso(-3700),
      countedSeconds: 3600,
      running: true,
      cyclesCompleted: 0,
      // Contado até agora (hora do servidor na resposta).
      serverTime: iso(),
    };
    const { user, fetchMock } = setup({
      ...roomActive(session),
      'GET /api/rooms/r1': () => jsonResponse(200, detail({ session })),
      'POST /api/rooms/r1/session/pause': () =>
        jsonResponse(200, {
          ...session,
          state: 'PAUSED',
          running: false,
          countedSeconds: 3660,
          serverTime: iso(),
        }),
    });
    const card = await screen.findByRole('region', { name: 'Sala Turma de Direito' });
    expect(
      await within(card).findByRole('timer', { name: /Tempo da sessão: 1:00:0\d/ }),
    ).toBeInTheDocument();
    // 65 s depois (aba trocada, nenhum tick contado): a tela soma pelos timestamps.
    const realNow = Date.now.bind(Date);
    const jump = vi.spyOn(Date, 'now').mockImplementation(() => realNow() + 65_000);
    expect(
      await within(card).findByRole(
        'timer',
        { name: /Tempo da sessão: 1:01:0\d/ },
        { timeout: 3000 },
      ),
    ).toBeInTheDocument();
    jump.mockRestore();
    expect(within(card).getByText(/Na sala desde/)).toBeInTheDocument();
    // Com sessão, o modo fica travado e aparecem Pausar / Parar e salvar.
    expect(within(card).getByRole('radio', { name: 'Pomodoro' })).toBeDisabled();
    await user.click(within(card).getByRole('button', { name: 'Pausar' }));
    expect(
      await within(card).findByRole('timer', { name: 'Tempo da sessão: 1:01:00' }),
    ).toBeInTheDocument();
    expect(within(card).getByRole('button', { name: 'Retomar' })).toBeInTheDocument();
    // Nenhuma duração vai para o servidor.
    const pause = calls(fetchMock, 'POST', '/api/rooms/r1/session/pause')[0]!;
    expect(pause[1]?.body).toBeUndefined();
  });

  it('grade de membros com estado em texto e matéria só quando permitida', async () => {
    setup();
    const grid = await screen.findByRole('region', { name: 'Na sala' });
    const items = await within(grid).findAllByRole('listitem');
    expect(items).toHaveLength(4);
    expect(within(grid).getByText('Estudando')).toBeInTheDocument();
    expect(within(grid).getByText('Pausado')).toBeInTheDocument();
    expect(within(grid).getByText('Ausente')).toBeInTheDocument();
    expect(within(grid).getByText('Offline')).toBeInTheDocument();
    expect(within(grid).getByText(/Direito Civil/)).toBeInTheDocument();
    expect(within(grid).getByText('2 ciclos')).toBeInTheDocument();
    expect(screen.getByText('1 estudando agora')).toBeInTheDocument();
  });

  it('"Ainda está estudando?": contagem regressiva, foco no botão e Enter confirma', async () => {
    const asking: RoomSession = {
      sessionId: 's1',
      roomId: 'r1',
      mode: 'FREE',
      state: 'STUDYING',
      startedAt: iso(-3700),
      countedSeconds: 3600,
      running: true,
      cyclesCompleted: 0,
      confirmDeadline: iso(240),
      serverTime: iso(),
    };
    const { user, fetchMock } = setup({
      // A sessão ativa é de sala: o motor manda heartbeat e o servidor pede a confirmação.
      'GET /api/deepwork/sessions/active': () =>
        jsonResponse(200, {
          id: 's1',
          subjectId: '',
          roomId: 'r1',
          mode: 'FLOW',
          source: 'ROOM',
          startedAt: iso(-3700),
          pausedSeconds: 0,
          effectiveSeconds: 3600,
          active: true,
          serverTime: iso(),
        }),
      'POST /api/rooms/r1/session/heartbeat': () => jsonResponse(200, asking),
      'POST /api/rooms/r1/session/confirm-presence': () =>
        jsonResponse(200, { ...asking, confirmDeadline: undefined }),
    });
    const dialog = await screen.findByRole('dialog', { name: 'Ainda está estudando?' });
    expect(
      within(dialog).getByRole('timer', { name: /Tempo para responder: [34]:\d\d/ }),
    ).toBeInTheDocument();
    const yes = within(dialog).getByRole('button', { name: 'Sim, estou estudando' });
    await waitFor(() => expect(yes).toHaveFocus());
    await user.keyboard('{Enter}');
    await waitFor(() =>
      expect(
        screen.queryByRole('dialog', { name: 'Ainda está estudando?' }),
      ).not.toBeInTheDocument(),
    );
    expect(calls(fetchMock, 'POST', '/api/rooms/r1/session/confirm-presence')).toHaveLength(1);
    expect(calls(fetchMock, 'POST', '/api/rooms/r1/session/heartbeat').length).toBeGreaterThan(0);
  });

  it('conflito de sessão em outra aba oferece assumir aqui', async () => {
    const started: RoomSession = {
      sessionId: 's2',
      roomId: 'r1',
      mode: 'FREE',
      state: 'STUDYING',
      startedAt: iso(),
      countedSeconds: 0,
      running: true,
      cyclesCompleted: 0,
      serverTime: iso(),
    };
    let taken = false;
    const after = roomActive(started);
    const { user, fetchMock } = setup({
      // Antes de assumir, a sessão aberta é de outra aba (o Deep Work não a mostra aqui).
      'GET /api/deepwork/sessions/active': () =>
        taken ? after['GET /api/deepwork/sessions/active']() : new Response(null, { status: 204 }),
      'POST /api/rooms/r1/session/heartbeat': () => after['POST /api/rooms/r1/session/heartbeat'](),
      'POST /api/rooms/r1/session/start': (init) => {
        const body = JSON.parse(String(init?.body ?? '{}')) as { takeover?: boolean };
        if (!body.takeover) {
          return jsonResponse(409, { code: 'ROOM_SESSION_ACTIVE', message: 'x', details: [] });
        }
        taken = true;
        return jsonResponse(200, started);
      },
    });
    const card = await screen.findByRole('region', { name: 'Sala Turma de Direito' });
    await user.click(within(card).getByRole('button', { name: 'Começar a estudar' }));
    const alert = await within(card).findByRole('alert');
    expect(alert).toHaveTextContent('Sua sessão está aberta em outra aba');
    await user.click(
      within(alert).getByRole('button', { name: 'Encerrar a outra e começar aqui' }),
    );
    await waitFor(() =>
      expect(within(card).getByRole('button', { name: 'Parar e salvar' })).toBeInTheDocument(),
    );
    const bodies = calls(fetchMock, 'POST', '/api/rooms/r1/session/start').map(([, i]) =>
      JSON.parse(String(i?.body)),
    );
    expect(bodies).toEqual([{ mode: 'FREE' }, { mode: 'FREE', takeover: true }]);
  });

  it('erro ao carregar a sala', async () => {
    setup({ 'GET /api/rooms/r1': () => jsonResponse(500, { code: 'INTERNAL_ERROR' }) });
    expect(await screen.findByText('Não foi possível carregar.')).toBeInTheDocument();
  });
});

describe('polling da presença', () => {
  it('30 s com a aba visível; para com a aba oculta e volta com atualização imediata', async () => {
    useAuthStore.getState().setSession('t', testUser);
    const fetchMock = mockApi({ 'GET /api/rooms/r1/presence': () => jsonResponse(200, presence) });
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
    let state: DocumentVisibilityState = 'visible';
    vi.spyOn(document, 'visibilityState', 'get').mockImplementation(() => state);
    renderHook(() => usePresence('r1'), { wrapper });
    const observer = () =>
      client.getQueryCache().find({ queryKey: ['rooms', 'presence', 'r1'] })!.observers[0]!;
    await waitFor(() =>
      expect(calls(fetchMock, 'GET', '/api/rooms/r1/presence').length).toBeGreaterThan(0),
    );
    expect(observer().options.refetchInterval).toBe(30_000);
    expect(observer().options.refetchIntervalInBackground).toBe(false);

    act(() => {
      state = 'hidden';
      document.dispatchEvent(new Event('visibilitychange'));
    });
    await waitFor(() => expect(observer().options.refetchInterval).toBe(false));
    const before = calls(fetchMock, 'GET', '/api/rooms/r1/presence').length;

    act(() => {
      state = 'visible';
      document.dispatchEvent(new Event('visibilitychange'));
    });
    await waitFor(() => expect(observer().options.refetchInterval).toBe(30_000));
    await waitFor(() =>
      expect(calls(fetchMock, 'GET', '/api/rooms/r1/presence').length).toBeGreaterThan(before),
    );
  });
});

describe('lógica das salas', () => {
  const now = Date.parse('2026-11-21T10:00:00Z');
  it('cronômetro pelos timestamps, com o mesmo limite do servidor', () => {
    const base = { countedSeconds: 100, running: true, serverTime: '2026-11-21T09:59:30Z' };
    expect(sessionSeconds(base, 0, now)).toBe(130);
    // Relógio local 5 s atrasado em relação ao servidor.
    expect(sessionSeconds(base, 5000, now)).toBe(135);
    // Pausado não anda; intervalo enorme não passa de 2 min (o servidor também não credita).
    expect(sessionSeconds({ ...base, running: false }, 0, now)).toBe(100);
    expect(sessionSeconds({ ...base, serverTime: '2026-11-21T09:00:00Z' }, 0, now)).toBe(220);
    expect(
      memberSeconds({ sessionSeconds: 60, state: 'AWAY' }, '2026-11-21T09:59:00Z', 0, now),
    ).toBe(60);
    expect(
      memberSeconds({ sessionSeconds: 60, state: 'STUDYING' }, '2026-11-21T09:59:00Z', 0, now),
    ).toBe(120);
  });

  it('fase do Pomodoro compartilhado (espelho do backend)', () => {
    const config = {
      focusMinutes: 25,
      shortBreakMinutes: 5,
      longBreakMinutes: 15,
      longBreakEvery: 4,
      cycleStartedAt: '2026-11-21T08:00:00Z',
    };
    const at = (min: number) => Date.parse('2026-11-21T08:00:00Z') + min * 60_000;
    expect(phaseAt(config, at(10))).toMatchObject({
      phase: 'FOCUS',
      cycle: 1,
      of: 4,
      remainingSeconds: 900,
    });
    expect(phaseAt(config, at(27))).toMatchObject({
      phase: 'SHORT_BREAK',
      cycle: 1,
      remainingSeconds: 180,
    });
    expect(phaseAt(config, at(31))).toMatchObject({ phase: 'FOCUS', cycle: 2 });
    expect(phaseAt(config, at(120))).toMatchObject({ phase: 'LONG_BREAK', cycle: 4 });
    expect(phaseAt(config, at(131))).toMatchObject({ phase: 'FOCUS', cycle: 1 });
  });

  it('confirmação, meta, código e apelido', () => {
    expect(confirmSecondsLeft('2026-11-21T10:04:00Z', 0, now)).toBe(240);
    expect(confirmSecondsLeft('2026-11-21T09:00:00Z', 0, now)).toBe(0);
    expect(goalPercent(36_000, 40)).toBe(25);
    expect(goalPercent(999_999, 40)).toBe(100);
    expect(goalPercent(100, undefined)).toBeNull();
    expect(formatCodeInput('abcd1234efgh99')).toBe('ABCD-1234-EFGH');
    expect(formatCodeInput('ab-cd 12')).toBe('ABCD-12');
    expect(validNickname('Ana_2')).toBe(true);
    expect(validNickname('Zé Maria')).toBe(true);
    expect(validNickname('a')).toBe(false);
    expect(validNickname('<b>oi</b>')).toBe(false);
  });
});
