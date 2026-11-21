import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, renderHook, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '@/app/router';
import i18n from '@/i18n';
import type { Preferences, SessionView } from '@/lib/api/account';
import type { User } from '@/lib/api/types';
import { useTimeZone } from '@/lib/useTimeZone';
import { initials } from '@/lib/initials';
import { useAuthStore } from '@/stores/authStore';
import { authBody, jsonResponse, mockApi, renderWithProviders, testUser } from '@/test/utils';
import {
  describeUserAgent,
  invalidNumberFields,
  toPreferencesForm,
  toPreferencesPatch,
} from './helpers';

const user: User = {
  ...testUser,
  course: 'Direito',
  institution: 'Universidade Exemplo',
};

const prefs: Preferences = {
  locale: 'pt-BR',
  gradeMethod: 'WEIGHTED',
  gradeScaleMax: 10,
  passingGrade: 6,
  maxAbsencePct: 25,
  focusMinutes: 25,
  shortBreakMinutes: 5,
  longBreakMinutes: 15,
  longBreakEvery: 4,
  weeklyGoalMinutes: 600,
  newCardsPerDay: 20,
  reviewsPerDay: 100,
};

const sessions: SessionView[] = [
  {
    id: 'sess-1',
    userAgent: 'Mozilla/5.0 (Windows NT 10.0) Chrome/140.0',
    createdAt: '2026-11-01T10:00:00Z',
    lastUsedAt: '2026-11-20T10:00:00Z',
    current: true,
  },
  {
    id: 'sess-2',
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 18) Safari/604.1',
    createdAt: '2026-11-02T10:00:00Z',
    lastUsedAt: '2026-11-19T10:00:00Z',
    current: false,
  },
];

function setup(overrides: Parameters<typeof mockApi>[0] = {}) {
  useAuthStore.getState().setSession('token', user);
  const fetchMock = mockApi({
    'GET /api/me/preferences': () => jsonResponse(200, prefs),
    'GET /api/me/sessions': () => jsonResponse(200, sessions),
    ...overrides,
  });
  const ui = userEvent.setup();
  const rendered = renderWithProviders(routes, { initialEntries: ['/profile'] });
  return { user: ui, fetchMock, ...rendered };
}

function calls(fetchMock: ReturnType<typeof mockApi>, method: string, path: string) {
  return fetchMock.mock.calls.filter(
    ([url, init]) => String(url).split('?')[0] === path && (init?.method ?? 'GET') === method,
  );
}

function bodyOf(fetchMock: ReturnType<typeof mockApi>, method: string, path: string) {
  return JSON.parse(String(calls(fetchMock, method, path).at(-1)![1]!.body)) as Record<
    string,
    unknown
  >;
}

beforeEach(() => {
  void i18n.changeLanguage('pt-BR');
});

afterEach(() => {
  cleanup();
  useAuthStore.getState().clear();
  void i18n.changeLanguage('pt-BR');
  vi.restoreAllMocks();
});

describe('Perfil', () => {
  it('mostra as quatro seções e salva o perfil com feedback', async () => {
    const { user: ui, fetchMock } = setup({
      'PUT /api/me/profile': (init) =>
        jsonResponse(200, { ...user, ...JSON.parse(String(init?.body)) }),
    });
    for (const name of ['Perfil', 'Preferências', 'Conta', 'Privacidade e dados']) {
      expect(await screen.findByRole('region', { name })).toBeInTheDocument();
    }
    const profile = screen.getByRole('region', { name: 'Perfil' });
    expect(within(profile).getByLabelText('Curso')).toHaveValue('Direito');

    await ui.clear(within(profile).getByLabelText('Nome de exibição'));
    await ui.type(within(profile).getByLabelText('Nome de exibição'), 'Ada Byron');
    await ui.type(within(profile).getByLabelText('Período atual'), '3º período');
    await ui.click(within(profile).getByRole('radio', { name: 'Roxo' }));
    await ui.click(within(profile).getByRole('button', { name: 'Salvar perfil' }));

    expect(await within(profile).findByRole('status')).toHaveTextContent('Perfil salvo');
    expect(bodyOf(fetchMock, 'PUT', '/api/me/profile')).toMatchObject({
      name: 'Ada Byron',
      course: 'Direito',
      currentPeriod: '3º período',
      avatarColor: '#bd6cf7',
    });
    // A barra lateral usa o perfil novo (mesmo store).
    expect(screen.getByRole('link', { name: 'Abrir perfil de Ada Byron' })).toBeInTheDocument();
  });

  it('valida o nome no cliente e mostra o erro do servidor no campo', async () => {
    const { user: ui, fetchMock } = setup({
      'PUT /api/me/profile': () =>
        jsonResponse(400, {
          code: 'VALIDATION_ERROR',
          message: 'x',
          details: [{ field: 'bio', message: 'size must be between 0 and 280' }],
        }),
    });
    const profile = await screen.findByRole('region', { name: 'Perfil' });
    await ui.clear(within(profile).getByLabelText('Nome de exibição'));
    await ui.click(within(profile).getByRole('button', { name: 'Salvar perfil' }));
    expect(within(profile).getByLabelText('Nome de exibição')).toHaveAccessibleDescription(
      'Informe um nome.',
    );
    expect(calls(fetchMock, 'PUT', '/api/me/profile')).toHaveLength(0);

    await ui.type(within(profile).getByLabelText('Nome de exibição'), 'Ada');
    await ui.click(within(profile).getByRole('button', { name: 'Salvar perfil' }));
    await waitFor(() =>
      expect(within(profile).getByLabelText('Bio curta')).toHaveAccessibleDescription(
        'size must be between 0 and 280',
      ),
    );
  });

  it('salva as preferências: horas viram minutos, fuso automático e idioma aplicado', async () => {
    const { user: ui, fetchMock } = setup({
      'PATCH /api/me/preferences': (init) => {
        const patch = JSON.parse(String(init?.body)) as Partial<Preferences>;
        return jsonResponse(200, { ...prefs, ...patch, timeZone: undefined });
      },
    });
    const section = await screen.findByRole('region', { name: 'Preferências' });
    const focus = await within(section).findByLabelText('Foco (min)');
    await ui.clear(focus);
    await ui.type(focus, '50');
    const goal = within(section).getByLabelText('Meta semanal de foco (horas)');
    expect(goal).toHaveValue(10);
    await ui.clear(goal);
    await ui.type(goal, '12.5');
    await ui.selectOptions(within(section).getByLabelText('Idioma'), 'en');
    await ui.click(within(section).getByRole('button', { name: 'Salvar preferências' }));

    await waitFor(() => expect(calls(fetchMock, 'PATCH', '/api/me/preferences')).toHaveLength(1));
    expect(bodyOf(fetchMock, 'PATCH', '/api/me/preferences')).toMatchObject({
      locale: 'en',
      timeZone: '',
      focusMinutes: 50,
      weeklyGoalMinutes: 750,
      passingGrade: 6,
    });
    await waitFor(() => expect(i18n.language).toBe('en'));
    expect(useAuthStore.getState().user?.locale).toBe('en');
    expect(await screen.findByText('Preferences saved')).toBeInTheDocument();
  });

  it('não envia número inválido e mostra o erro do servidor no campo certo', async () => {
    const { user: ui, fetchMock } = setup({
      'PATCH /api/me/preferences': () =>
        jsonResponse(400, {
          code: 'VALIDATION_ERROR',
          message: 'x',
          details: [{ field: 'weeklyGoalMinutes', message: 'must be less than or equal to 10080' }],
        }),
    });
    const section = await screen.findByRole('region', { name: 'Preferências' });
    const focus = await within(section).findByLabelText('Foco (min)');
    await ui.clear(focus);
    await ui.click(within(section).getByRole('button', { name: 'Salvar preferências' }));
    expect(focus).toHaveAccessibleDescription('Informe um número.');
    expect(calls(fetchMock, 'PATCH', '/api/me/preferences')).toHaveLength(0);

    await ui.type(focus, '25');
    await ui.click(within(section).getByRole('button', { name: 'Salvar preferências' }));
    await waitFor(() =>
      expect(
        within(section).getByLabelText('Meta semanal de foco (horas)'),
      ).toHaveAccessibleDescription('must be less than or equal to 10080'),
    );
  });

  it('troca de senha: confere a repetição e mostra senha atual incorreta', async () => {
    const { user: ui, fetchMock } = setup({
      'PUT /api/me/password': () =>
        jsonResponse(400, {
          code: 'INVALID_CURRENT_PASSWORD',
          message: 'x',
          details: [{ field: 'currentPassword', message: 'is incorrect' }],
        }),
    });
    const account = await screen.findByRole('region', { name: 'Conta' });
    const form = within(account).getByRole('form', { name: 'Senha' });
    await ui.type(within(form).getByLabelText('Senha atual'), 'errada-123');
    await ui.type(within(form).getByLabelText('Nova senha'), 'nova-senha-1');
    await ui.type(within(form).getByLabelText('Repita a nova senha'), 'outra-coisa');
    await ui.click(within(form).getByRole('button', { name: 'Alterar senha' }));
    expect(within(form).getByLabelText('Repita a nova senha')).toHaveAccessibleDescription(
      'As senhas não são iguais.',
    );
    expect(calls(fetchMock, 'PUT', '/api/me/password')).toHaveLength(0);

    await ui.clear(within(form).getByLabelText('Repita a nova senha'));
    await ui.type(within(form).getByLabelText('Repita a nova senha'), 'nova-senha-1');
    await ui.click(within(form).getByRole('button', { name: 'Alterar senha' }));
    await waitFor(() =>
      expect(within(form).getByLabelText('Senha atual')).toHaveAccessibleDescription(
        'Senha atual incorreta.',
      ),
    );
  });

  it('troca de senha com sucesso guarda o token novo da sessão', async () => {
    const { user: ui } = setup({
      'PUT /api/me/password': () => jsonResponse(200, authBody('rotated-token', user)),
    });
    const account = await screen.findByRole('region', { name: 'Conta' });
    const form = within(account).getByRole('form', { name: 'Senha' });
    await ui.type(within(form).getByLabelText('Senha atual'), 'senha-atual');
    await ui.type(within(form).getByLabelText('Nova senha'), 'nova-senha-1');
    await ui.type(within(form).getByLabelText('Repita a nova senha'), 'nova-senha-1');
    await ui.click(within(form).getByRole('button', { name: 'Alterar senha' }));
    expect(await within(form).findByRole('status')).toHaveTextContent('Senha alterada');
    expect(useAuthStore.getState().accessToken).toBe('rotated-token');
  });

  it('lista dispositivos, desconecta um e sai de todos com confirmação', async () => {
    const {
      user: ui,
      fetchMock,
      router,
    } = setup({
      'DELETE /api/me/sessions/sess-2': () => new Response(null, { status: 204 }),
      'DELETE /api/me/sessions': () => new Response(null, { status: 204 }),
    });
    const list = await screen.findByRole('region', { name: 'Dispositivos conectados' });
    expect(await within(list).findByText('Chrome no Windows')).toBeInTheDocument();
    expect(within(list).getByText('Este dispositivo')).toBeInTheDocument();
    // A sessão atual não tem botão de desconectar (use "sair de todos").
    expect(within(list).getAllByRole('button', { name: /^Desconectar/ })).toHaveLength(1);

    await ui.click(within(list).getByRole('button', { name: 'Desconectar Safari no iOS' }));
    await waitFor(() =>
      expect(calls(fetchMock, 'DELETE', '/api/me/sessions/sess-2')).toHaveLength(1),
    );

    await ui.click(within(list).getByRole('button', { name: 'Sair de todos os dispositivos' }));
    expect(calls(fetchMock, 'DELETE', '/api/me/sessions')).toHaveLength(0);
    expect(within(list).getByText(/Inclui este/)).toBeInTheDocument();
    await ui.click(within(list).getByRole('button', { name: 'Confirmar: sair de todos' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    expect(useAuthStore.getState().status).toBe('anonymous');
  });

  it('exporta os dados como arquivo JSON', async () => {
    const createObjectURL = vi.fn(() => 'blob:x');
    const revokeObjectURL = vi.fn();
    Object.assign(URL, { createObjectURL, revokeObjectURL });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    const { user: ui, fetchMock } = setup({
      'GET /api/me/export': () => jsonResponse(200, { format: 'stackstudy-export', tasks: [] }),
    });
    const privacy = await screen.findByRole('region', { name: 'Privacidade e dados' });
    // Documentos legais em vigor, ao lado da exportação e da exclusão.
    const docs = within(privacy).getByRole('navigation', { name: 'Documentos' });
    expect(within(docs).getByRole('link', { name: 'Termos de Uso' })).toHaveAttribute(
      'href',
      '/termos',
    );
    expect(within(docs).getByRole('link', { name: 'Política de Privacidade' })).toHaveAttribute(
      'href',
      '/privacidade',
    );
    await ui.click(within(privacy).getByRole('button', { name: 'Exportar em JSON' }));
    expect(await within(privacy).findByText('Arquivo baixado')).toBeInTheDocument();
    expect(calls(fetchMock, 'GET', '/api/me/export')).toHaveLength(1);
    expect(createObjectURL).toHaveBeenCalledTimes(1);
    expect(click).toHaveBeenCalledTimes(1);
  });

  it('excluir a conta só libera com o e-mail digitado e depois sai', async () => {
    const {
      user: ui,
      fetchMock,
      router,
    } = setup({
      'DELETE /api/me': () => new Response(null, { status: 204 }),
    });
    const privacy = await screen.findByRole('region', { name: 'Privacidade e dados' });
    await ui.click(within(privacy).getByRole('button', { name: 'Excluir conta' }));
    const dialog = screen.getByRole('dialog', { name: 'Excluir a conta para sempre?' });
    const confirm = within(dialog).getByRole('button', { name: 'Excluir tudo' });
    expect(confirm).toBeDisabled();

    await ui.type(within(dialog).getByLabelText(/Digite ada@example.com/), 'ada@exemplo.com');
    await ui.type(within(dialog).getByLabelText('Senha atual'), 'senha-atual');
    expect(confirm).toBeDisabled();
    await ui.clear(within(dialog).getByLabelText(/Digite ada@example.com/));
    await ui.type(within(dialog).getByLabelText(/Digite ada@example.com/), 'ADA@example.com');
    expect(confirm).toBeEnabled();
    await ui.click(confirm);

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    expect(bodyOf(fetchMock, 'DELETE', '/api/me')).toEqual({
      email: 'ADA@example.com',
      currentPassword: 'senha-atual',
    });
    expect(useAuthStore.getState().status).toBe('anonymous');
  });
});

describe('preferências nas outras telas', () => {
  it('useTimeZone: o fuso do perfil vale; sem ele, o do navegador', () => {
    useAuthStore.getState().setSession('t', { ...testUser, timeZone: 'America/Manaus' });
    const { result } = renderHook(() => useTimeZone());
    expect(result.current).toBe('America/Manaus');
    act(() => useAuthStore.getState().setUser({ ...testUser, timeZone: undefined }));
    expect(result.current).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC');
  });

  it('o heatmap pede os dias no fuso escolhido no perfil', async () => {
    useAuthStore.getState().setSession('t', { ...testUser, timeZone: 'Asia/Tokyo' });
    const fetchMock = mockApi({});
    renderWithProviders(routes, { initialEntries: ['/deep-work'] });
    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some(([url]) => String(url).includes('/api/deepwork/heatmap')),
      ).toBe(true),
    );
    const heatmapUrl = fetchMock.mock.calls
      .map(([url]) => String(url))
      .find((url) => url.includes('/api/deepwork/heatmap'))!;
    expect(heatmapUrl).toContain('tz=Asia%2FTokyo');
  });
});

describe('helpers do perfil', () => {
  it('iniciais do avatar', () => {
    expect(initials('Ada Lovelace')).toBe('AL');
    expect(initials('  maria  da   silva ')).toBe('MS');
    expect(initials('Zé')).toBe('Z');
    expect(initials('')).toBe('?');
  });

  it('reconhece navegador e sistema', () => {
    expect(describeUserAgent('Mozilla/5.0 (Windows NT 10.0) Chrome/140 Safari/537')).toEqual({
      browser: 'Chrome',
      os: 'Windows',
    });
    expect(describeUserAgent('Mozilla/5.0 (Android 15) Firefox/140')).toEqual({
      browser: 'Firefox',
      os: 'Android',
    });
    expect(describeUserAgent(undefined)).toEqual({});
  });

  it('formulário ↔ PATCH', () => {
    const form = toPreferencesForm({ ...prefs, timeZone: 'America/Recife', weeklyGoalMinutes: 90 });
    expect(form.weeklyGoalHours).toBe('1.5');
    expect(form.timeZone).toBe('America/Recife');
    const patch = toPreferencesPatch({ ...form, weeklyGoalHours: '2,25', passingGrade: '7,5' });
    expect(patch.weeklyGoalMinutes).toBe(135);
    expect(patch.passingGrade).toBe(7.5);
    expect(invalidNumberFields({ ...form, focusMinutes: '', reviewsPerDay: 'abc' })).toEqual([
      'focusMinutes',
      'reviewsPerDay',
    ]);
  });
});
