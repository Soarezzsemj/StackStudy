import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, renderHook, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import userEvent from '@testing-library/user-event';
import { routes } from '@/app/router';
import type { AdminErrorRow, AdminHealth, AdminOverview, AdminSignup } from '@/lib/api/admin';
import type { User } from '@/lib/api/types';
import { useAuthStore } from '@/stores/authStore';
import { jsonResponse, mockApi, renderWithProviders, testUser } from '@/test/utils';
import {
  buildCsv,
  formatBytes,
  formatUptime,
  pct,
  pointsDelta,
  retentionRate,
  variation,
} from './adminLogic';
import { ADMIN_REFRESH_MS, useAdminOverview } from './queries';

const admin: User = { ...testUser, role: 'ADMIN' };
const common: User = { ...testUser, role: 'USER' };

const days = Array.from({ length: 7 }, (_, i) => ({
  day: `2026-11-${String(15 + i).padStart(2, '0')}`,
  activeUsers: [3, 5, 4, 6, 8, 7, 9][i]!,
  signups: [1, 0, 2, 1, 0, 3, 1][i]!,
}));

const overview: AdminOverview = {
  range: '7d',
  generatedAt: '2026-11-21T12:00:00Z',
  totalUsers: 128,
  newUsers: { value: 8, previous: 4 },
  onlineNow: 3,
  dailyActive: { value: 9, previous: 12 },
  weeklyActive: { value: 40, previous: 40 },
  retentionD7: { cohort: 20, retained: 9, previousCohort: 10, previousRetained: 4 },
  series: days,
  funnel: { registered: 20, firstSubject: 15, firstFocusSession: 10, returned7d: 9 },
  usage: { weeklyActive: 40, stack: 30, deepWork: 20, flashcards: 10, grades: 5 },
  focusHoursPerActive: 2.5,
};

const health: AdminHealth = {
  dbUp: true,
  dbLatencyMs: 12,
  dbSizeBytes: 9_437_184,
  postgresVersion: '17.2',
  uptimeSeconds: 3 * 3600 + 15 * 60,
  heapUsedBytes: 120 * 1024 * 1024,
  heapMaxBytes: 300 * 1024 * 1024,
  appVersion: '0.1.0-SNAPSHOT',
  commit: 'abc1234def',
  startsLast24h: 4,
  links: {
    render: 'https://dashboard.render.com/web/srv-x',
    backup: 'https://github.com/x/y/actions',
  },
};

const signups: AdminSignup[] = [
  { email: 'an***@uni.br', course: 'Direito', createdAt: '2026-11-21T10:00:00Z' },
  { email: 'b***@x.com', createdAt: '2026-11-20T10:00:00Z' },
];

const errors: AdminErrorRow[] = [
  { route: 'POST /api/auth/refresh', status: 401, count: 42, lastSeenAt: '2026-11-21T11:00:00Z' },
  { route: 'GET /api/tasks/{id}', status: 500, count: 2, lastSeenAt: '2026-11-21T09:00:00Z' },
];

function setup(user: User, overrides: Parameters<typeof mockApi>[0] = {}) {
  useAuthStore.getState().setSession('token', user);
  const fetchMock = mockApi({
    'GET /api/admin/status/overview': () => jsonResponse(200, overview),
    'GET /api/admin/status/health': () => jsonResponse(200, health),
    'GET /api/admin/status/signups': () => jsonResponse(200, signups),
    'GET /api/admin/status/errors': () => jsonResponse(200, errors),
    ...overrides,
  });
  const ui = userEvent.setup();
  const rendered = renderWithProviders(routes, { initialEntries: ['/admin/status'] });
  return { user: ui, fetchMock, ...rendered };
}

function adminCalls(fetchMock: ReturnType<typeof mockApi>, path = '/api/admin/') {
  return fetchMock.mock.calls.filter(([url]) => String(url).startsWith(path));
}

afterEach(() => {
  cleanup();
  useAuthStore.getState().clear();
  vi.restoreAllMocks();
});

describe('Admin: Status', () => {
  it('quem não é admin vê o 403 direto, sem chamar a API nem ver o link', async () => {
    const { fetchMock } = setup(common);
    expect(await screen.findByRole('heading', { level: 1, name: '403' })).toBeInTheDocument();
    expect(
      screen.getByText('Esta área é só para administradores do StackStudy.'),
    ).toBeInTheDocument();
    expect(screen.queryByText('Admin: Status')).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Administração' })).not.toBeInTheDocument();
    expect(adminCalls(fetchMock)).toHaveLength(0);
  });

  it('admin vê o painel completo com os dados agregados', async () => {
    setup(admin);
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Admin: Status' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Administração' })).toHaveAttribute(
      'href',
      '/admin/status',
    );
    expect(screen.getByRole('note')).toHaveTextContent('Acesso restrito');
    expect(screen.getByRole('note')).not.toHaveTextContent(/2FA|dois fatores/i);

    const online = await screen.findByRole('region', { name: 'Online agora' });
    expect(within(online).getByText('3')).toBeInTheDocument();
    expect(
      within(online).getByRole('img', {
        name: /Ativos por dia nos últimos 7 dias: mínimo 3, máximo 9/,
      }),
    ).toBeInTheDocument();

    expect(screen.getByText('128')).toBeInTheDocument();
    // Novos: 8 vs 4 → +100%; DAU 9 vs 12 → −25%; WAU igual; retenção 45% vs 40% → +5 p.p.
    expect(screen.getByText('+100%')).toBeInTheDocument();
    expect(screen.getByText('-25%')).toBeInTheDocument();
    expect(screen.getByText('0%')).toBeInTheDocument();
    expect(screen.getByText('45%')).toBeInTheDocument();
    expect(screen.getByText('+5 p.p.')).toBeInTheDocument();

    const activity = screen.getByRole('region', { name: 'Usuários ativos por dia' });
    expect(
      within(activity).getByRole('img', { name: /pico de 9\) e 8 novos cadastros/ }),
    ).toBeInTheDocument();
    expect(within(activity).getByText(/Média de 2,5 h de foco/)).toBeInTheDocument();

    const funnel = screen.getByRole('region', { name: 'Funil de ativação' });
    expect(
      within(funnel).getByRole('progressbar', { name: '1ª matéria: 75%' }),
    ).toBeInTheDocument();
    expect(
      within(funnel).getByRole('progressbar', { name: 'Voltou em até 7 dias: 45%' }),
    ).toBeInTheDocument();
    const usage = screen.getByRole('region', { name: 'Uso por função' });
    expect(within(usage).getByRole('progressbar', { name: 'The Stack: 75%' })).toBeInTheDocument();
    expect(
      within(usage).getByRole('progressbar', { name: 'Grade Predictor: 12.5%' }),
    ).toBeInTheDocument();

    const h = await screen.findByRole('region', { name: 'Saúde do sistema' });
    expect(await within(h).findByText('No ar')).toBeInTheDocument();
    expect(within(h).getByText('12 ms')).toBeInTheDocument();
    expect(within(h).getByText('9 MB')).toBeInTheDocument();
    expect(within(h).getByText('3 h 15 min')).toBeInTheDocument();
    expect(within(h).getByText(/abc1234/)).toBeInTheDocument();

    // Provedores: só link (nada de número inventado); sem configuração, diz qual variável falta.
    const providers = screen.getByRole('region', { name: 'Limites do plano grátis' });
    const render = within(providers).getByRole('link', { name: /Abrir painel Render/ });
    expect(render).toHaveAttribute('href', 'https://dashboard.render.com/web/srv-x');
    expect(render).toHaveAttribute('rel', 'noopener noreferrer');
    expect(
      within(providers).getByText('Link não configurado (ADMIN_LINK_NEON).'),
    ).toBeInTheDocument();

    const table = within(
      await screen.findByRole('region', { name: 'Últimos cadastros' }),
    ).getByRole('table');
    expect(within(table).getByText('an***@uni.br')).toBeInTheDocument();
    expect(within(table).getByText('Direito')).toBeInTheDocument();
    const errs = within(screen.getByRole('region', { name: 'Erros nas últimas 24 h' })).getByRole(
      'table',
    );
    expect(within(errs).getByText('POST /api/auth/refresh')).toBeInTheDocument();
    expect(within(errs).getByText('42')).toBeInTheDocument();
  });

  it('troca o período, atualiza na hora e atualiza sozinho só com a aba visível', async () => {
    const { user, fetchMock } = setup(admin);
    await screen.findByRole('region', { name: 'Online agora' });
    const group = screen.getByRole('group', { name: 'Período' });
    expect(within(group).getByRole('button', { name: '7 dias' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await user.click(within(group).getByRole('button', { name: '30 dias' }));
    await waitFor(() =>
      expect(adminCalls(fetchMock).some(([url]) => String(url).endsWith('range=30d'))).toBe(true),
    );

    const before = adminCalls(fetchMock, '/api/admin/status/health').length;
    await user.click(screen.getByRole('button', { name: 'Atualizar' }));
    await waitFor(() =>
      expect(adminCalls(fetchMock, '/api/admin/status/health').length).toBeGreaterThan(before),
    );
  });

  it('a atualização automática é de 60 s e pausa com a aba escondida', () => {
    useAuthStore.getState().setSession('t', admin);
    mockApi({ 'GET /api/admin/status/overview': () => jsonResponse(200, overview) });
    const client = new QueryClient();
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
    renderHook(() => useAdminOverview('7d'), { wrapper });
    const options = client.getQueryCache().find({ queryKey: ['admin', 'overview', '7d'] })!
      .observers[0]!.options;
    expect(ADMIN_REFRESH_MS).toBe(60_000);
    expect(options.refetchInterval).toBe(60_000);
    expect(options.refetchIntervalInBackground).toBe(false);
  });

  it('exporta CSV no cliente, sem chamar a API de novo', async () => {
    const createObjectURL = vi.fn(() => 'blob:x');
    Object.assign(URL, { createObjectURL, revokeObjectURL: vi.fn() });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    const { user, fetchMock } = setup(admin);
    await screen.findByRole('region', { name: 'Online agora' });
    await screen.findByText('an***@uni.br');
    const calls = adminCalls(fetchMock).length;
    await user.click(screen.getByRole('button', { name: 'Exportar CSV' }));
    expect(createObjectURL).toHaveBeenCalledTimes(1);
    expect(click).toHaveBeenCalledTimes(1);
    expect(adminCalls(fetchMock)).toHaveLength(calls);
  });

  it('estado vazio e erro', async () => {
    const empty: AdminOverview = {
      ...overview,
      totalUsers: 0,
      series: days.map((d) => ({ ...d, activeUsers: 0, signups: 0 })),
      funnel: { registered: 0, firstSubject: 0, firstFocusSession: 0, returned7d: 0 },
      usage: { weeklyActive: 0, stack: 0, deepWork: 0, flashcards: 0, grades: 0 },
    };
    setup(admin, {
      'GET /api/admin/status/overview': () => jsonResponse(200, empty),
      'GET /api/admin/status/signups': () => jsonResponse(200, []),
      'GET /api/admin/status/errors': () => jsonResponse(500, { code: 'INTERNAL_ERROR' }),
    });
    expect(await screen.findAllByText('Ainda sem dados.')).toHaveLength(4);
    const errs = screen.getByRole('region', { name: 'Erros nas últimas 24 h' });
    expect(
      await within(errs).findByText('Não foi possível carregar estes dados.'),
    ).toBeInTheDocument();
  });

  it('papel revogado no servidor (403) mostra o acesso negado', async () => {
    setup(admin, {
      'GET /api/admin/status/overview': () =>
        jsonResponse(403, { code: 'FORBIDDEN', message: 'x' }),
    });
    expect(await screen.findByRole('heading', { level: 1, name: '403' })).toBeInTheDocument();
    expect(screen.queryByText('Admin: Status')).not.toBeInTheDocument();
  });
});

describe('lógica do painel', () => {
  it('variação, porcentagem e retenção', () => {
    expect(variation({ value: 8, previous: 4 })).toBe(100);
    expect(variation({ value: 9, previous: 12 })).toBe(-25);
    expect(variation({ value: 0, previous: 0 })).toBe(0);
    expect(variation({ value: 3, previous: 0 })).toBeNull();
    expect(pct(1, 3)).toBe(33.3);
    expect(pct(5, 0)).toBe(0);
    expect(retentionRate(overview.retentionD7)).toEqual({ value: 45, previous: 40 });
    expect(
      retentionRate({ cohort: 0, retained: 0, previousCohort: 0, previousRetained: 0 }),
    ).toEqual({
      value: null,
      previous: null,
    });
    expect(pointsDelta(45, 40)).toBe(5);
    expect(pointsDelta(null, 40)).toBeNull();
  });

  it('formatos', () => {
    expect(formatBytes(512, 'pt-BR')).toBe('512 B');
    expect(formatBytes(9_437_184, 'pt-BR')).toBe('9 MB');
    expect(formatBytes(1536, 'en')).toBe('1.5 KB');
    expect(formatBytes(undefined, 'en')).toBe('—');
    expect(formatUptime(45 * 60)).toBe('45 min');
    expect(formatUptime(2 * 86400 + 3600)).toBe('2 d 1 h');
  });

  it('CSV: seções, aspas e proteção contra fórmula', () => {
    const csv = buildCsv({
      overview,
      health,
      signups: [{ email: '=cmd()', course: 'Direito, noturno', createdAt: '2026-11-21T10:00:00Z' }],
      errors,
    });
    expect(csv).toContain('total_users,128,');
    expect(csv).toContain('retention_d7_pct,45,40');
    expect(csv).toContain('2026-11-21,9,1');
    expect(csv).toContain('stack,30,75');
    expect(csv).toContain('db_size_bytes,9437184');
    expect(csv).toContain(`'=cmd(),"Direito, noturno",2026-11-21T10:00:00Z`);
    expect(csv).toContain('POST /api/auth/refresh,401,42,');
    expect(csv).not.toMatch(/@example\.com/);
  });
});
