import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useTranslation } from 'react-i18next';
import { renderHook } from '@testing-library/react';
import { routes } from '@/app/router';
import type { RoadmapDetail, RoadmapList, RoadmapNode } from '@/lib/api/roadmaps';
import { useAuthStore } from '@/stores/authStore';
import { useDeepWorkStore } from '@/stores/deepWorkStore';
import { useStackStore } from '@/stores/stackStore';
import { jsonResponse, mockApi, renderWithProviders, testUser } from '@/test/utils';
import {
  buildRows,
  defaultSlug,
  keyboardOrder,
  moveFocus,
  nodeLabel,
  nodeState,
  remainingHours,
  topicTag,
} from './roadmapLogic';

const base = {
  description: undefined as string | undefined,
  optional: false,
  resources: [],
  manualHours: 0,
  focusHours: 0,
  current: false,
};

function node(partial: Partial<RoadmapNode> & Pick<RoadmapNode, 'id' | 'title'>): RoadmapNode {
  return {
    ...base,
    kind: 'MAIN',
    position: 1,
    estimatedHours: 10,
    status: 'TODO',
    hoursSpent: 0,
    ...partial,
  };
}

const detail: RoadmapDetail = {
  id: 'r1',
  slug: 'ciencia-da-computacao',
  title: 'Ciência da Computação',
  description: 'Trilha de exemplo.',
  area: 'Ciência da Computação',
  language: 'pt-BR',
  access: 'FREE',
  version: 1,
  sample: true,
  premiumEnabled: true,
  following: {
    startedAt: '2026-11-01T00:00:00Z',
    primary: true,
    weeklyGoalHours: 6,
    currentNodeId: 'ed',
  },
  summary: {
    totalTopics: 4,
    doneTopics: 1,
    percent: 25,
    totalHours: 44,
    hoursDone: 14,
    hoursRemaining: 31,
    weeklyGoalHours: 6,
    daysRemaining: 37,
    estimatedCompletion: '2026-12-28',
    completed: false,
  },
  stages: [
    {
      id: 's1',
      position: 1,
      title: 'Fundamentos',
      access: 'FREE',
      locked: false,
      topicCount: 2,
      doneCount: 1,
      nodes: [
        node({
          id: 'lp',
          title: 'Lógica de programação',
          status: 'DONE',
          hoursSpent: 12,
          estimatedHours: 12,
        }),
        node({
          id: 'py',
          title: 'Primeira linguagem',
          kind: 'SUB',
          parentId: 'lp',
          side: 'RIGHT',
          optional: true,
          estimatedHours: 8,
        }),
      ],
    },
    {
      id: 's2',
      position: 2,
      title: 'Estruturas e algoritmos',
      access: 'FREE',
      locked: false,
      topicCount: 2,
      doneCount: 0,
      nodes: [
        node({
          id: 'ed',
          title: 'Estruturas de dados',
          status: 'DOING',
          current: true,
          estimatedHours: 5,
          hoursSpent: 2,
          description: 'Como guardar e acessar dados.',
          resources: [
            {
              id: 'x1',
              title: 'Estrutura de dados (Wikipédia)',
              url: 'https://pt.wikipedia.org/wiki/Estrutura_de_dados',
              kind: 'ARTICLE',
            },
          ],
        }),
        node({
          id: 'lpf',
          title: 'Listas, pilhas e filas',
          kind: 'SUB',
          parentId: 'ed',
          side: 'LEFT',
          estimatedHours: 6,
        }),
        node({ id: 'aa', title: 'Análise de algoritmos', position: 2, estimatedHours: 10 }),
      ],
    },
    {
      id: 's4',
      position: 4,
      title: 'Prática profissional',
      access: 'PREMIUM',
      locked: true,
      topicCount: 1,
      doneCount: 0,
      nodes: [node({ id: 'es', title: 'Engenharia de software', estimatedHours: 15 })],
    },
  ],
};

const list: RoadmapList = {
  premiumEnabled: true,
  areas: ['Ciência da Computação', 'Direito'],
  items: [
    {
      id: 'r1',
      slug: 'ciencia-da-computacao',
      title: 'Ciência da Computação',
      description: '',
      area: 'Ciência da Computação',
      language: 'pt-BR',
      access: 'FREE',
      version: 1,
      sample: true,
      stageCount: 4,
      topicCount: 14,
      totalHours: 200,
      following: true,
      primary: true,
      percent: 25,
    },
    {
      id: 'r2',
      slug: 'direito',
      title: 'Direito',
      description: '',
      area: 'Direito',
      language: 'pt-BR',
      access: 'FREE',
      version: 1,
      sample: true,
      stageCount: 4,
      topicCount: 14,
      totalHours: 180,
      following: false,
      primary: false,
    },
  ],
};

function setup(
  path = '/roadmaps/ciencia-da-computacao',
  overrides: Parameters<typeof mockApi>[0] = {},
) {
  useAuthStore.getState().setSession('token', testUser);
  const fetchMock = mockApi({
    'GET /api/roadmaps': () => jsonResponse(200, list),
    'GET /api/roadmaps/ciencia-da-computacao': () => jsonResponse(200, detail),
    ...overrides,
  });
  const user = userEvent.setup();
  const rendered = renderWithProviders(routes, { initialEntries: [path] });
  return { user, fetchMock, ...rendered };
}

afterEach(() => {
  cleanup();
  useAuthStore.getState().clear();
  vi.restoreAllMocks();
});

describe('Roadmaps', () => {
  it('desenha o mapa com os 4 estados, rótulos acessíveis e selos das etapas', async () => {
    setup();
    const map = await screen.findByRole('group', { name: 'Tópicos da trilha' });
    const done = within(map).getByRole('button', { name: 'Lógica de programação, concluído' });
    expect(done).toHaveAttribute('data-state', 'done');
    const current = within(map).getByRole('button', {
      name: 'Estruturas de dados, em andamento, 3 horas restantes',
    });
    expect(current).toHaveAttribute('data-state', 'current');
    expect(current).toHaveAttribute('aria-current', 'step');
    const pending = within(map).getByRole('button', {
      name: 'Primeira linguagem, a fazer, 8 horas restantes, opcional',
    });
    expect(pending).toHaveAttribute('data-state', 'pending');
    // Subtópico opcional com borda tracejada.
    expect(pending.className).toContain('border-dashed');
    const locked = within(map).getByRole('button', { name: 'Engenharia de software, bloqueado' });
    expect(locked).toHaveAttribute('data-state', 'locked');
    // Estado também em texto visível (não só cor).
    expect(within(locked).getByText('bloqueado')).toBeInTheDocument();

    expect(within(map).getByText('Etapa 2 · Estruturas e algoritmos')).toBeInTheDocument();
    expect(screen.getAllByText('Grátis').length).toBeGreaterThan(0);
    // Flag do Premium ligada: sem a faixa de lançamento.
    expect(
      screen.queryByText('Lançamento: tudo grátis por tempo limitado.'),
    ).not.toBeInTheDocument();

    // Só um nó no Tab (o atual); setas andam pela trilha.
    expect(current).toHaveAttribute('tabindex', '0');
    expect(done).toHaveAttribute('tabindex', '-1');
  });

  it('setas do teclado movem o foco entre os nós', async () => {
    const { user } = setup();
    const map = await screen.findByRole('group', { name: 'Tópicos da trilha' });
    const current = within(map).getByRole('button', { name: /^Estruturas de dados/ });
    current.focus();
    await user.keyboard('{ArrowDown}');
    expect(within(map).getByRole('button', { name: /^Listas, pilhas e filas/ })).toHaveFocus();
    await user.keyboard('{Home}');
    expect(within(map).getByRole('button', { name: /^Lógica de programação/ })).toHaveFocus();
  });

  it('mostra resumo, tópico atual e a faixa de lançamento com a flag desligada', async () => {
    setup('/roadmaps/ciencia-da-computacao', {
      'GET /api/roadmaps/ciencia-da-computacao': () =>
        jsonResponse(200, { ...detail, premiumEnabled: false }),
    });
    expect(
      await screen.findByText('Lançamento: tudo grátis por tempo limitado.'),
    ).toBeInTheDocument();
    const summary = screen.getByRole('region', { name: 'Resumo de Ciência da Computação' });
    expect(within(summary).getByText('25%')).toBeInTheDocument();
    expect(within(summary).getByText('1 de 4 tópicos')).toBeInTheDocument();
    expect(within(summary).getByText('14 h')).toBeInTheDocument();
    expect(within(summary).getByText('31 h')).toBeInTheDocument();
    expect(within(summary).getByText('Exemplo')).toBeInTheDocument();

    const topic = screen.getByRole('region', { name: 'Tópico atual' });
    expect(within(topic).getByRole('heading', { name: 'Estruturas de dados' })).toBeInTheDocument();
    expect(
      within(topic).getByRole('link', { name: /Estrutura de dados \(Wikipédia\)/ }),
    ).toHaveAttribute('rel', 'noopener noreferrer');
    const actions = within(topic).getByRole('group', { name: 'Estudar este tópico' });
    expect(within(actions).getAllByRole('button')).toHaveLength(3);
  });

  it('alterna entre Mapa e Lista com as mesmas informações', async () => {
    const { user } = setup();
    await screen.findByRole('group', { name: 'Tópicos da trilha' });
    const view = screen.getByRole('group', { name: 'Visualização' });
    await user.click(within(view).getByRole('button', { name: 'Lista' }));
    expect(within(view).getByRole('button', { name: 'Lista' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(screen.queryByRole('group', { name: 'Tópicos da trilha' })).not.toBeInTheDocument();

    // A etapa do tópico atual começa aberta; as outras, fechadas.
    const stage2 = screen.getByRole('button', { name: /Etapa 2 · Estruturas e algoritmos/ });
    expect(stage2).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('button', { name: /Etapa 1 · Fundamentos/ })).toHaveAttribute(
      'aria-expanded',
      'false',
    );
    expect(
      screen.getByRole('button', { name: 'Análise de algoritmos, a fazer, 10 horas restantes' }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Etapa 1 · Fundamentos/ }));
    expect(
      screen.getByRole('button', { name: 'Lógica de programação, concluído' }),
    ).toBeInTheDocument();
  });

  it('abre o painel do nó e grava o status', async () => {
    const { user, fetchMock } = setup('/roadmaps/ciencia-da-computacao', {
      'PUT /api/roadmaps/ciencia-da-computacao/nodes/ed/progress': () => jsonResponse(200, detail),
    });
    const map = await screen.findByRole('group', { name: 'Tópicos da trilha' });
    await user.click(within(map).getByRole('button', { name: /^Estruturas de dados/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Estruturas de dados' });
    expect(within(dialog).getByText('Como guardar e acessar dados.')).toBeInTheDocument();
    expect(
      within(dialog).getByRole('link', { name: /Estrutura de dados \(Wikipédia\)/ }),
    ).toHaveAttribute('target', '_blank');
    const status = within(dialog).getByRole('group', { name: 'Status do tópico' });
    expect(within(status).getByRole('button', { name: 'Em andamento' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await user.click(within(status).getByRole('button', { name: 'Concluído' }));
    await waitFor(() => {
      const put = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT');
      expect(put).toBeDefined();
      expect(JSON.parse(String(put![1]!.body))).toEqual({ status: 'DONE', manualHours: 0 });
    });
  });

  it('nó bloqueado mostra o cadeado e "Ver planos" desativado', async () => {
    const { user } = setup();
    const map = await screen.findByRole('group', { name: 'Tópicos da trilha' });
    await user.click(
      within(map).getByRole('button', { name: 'Engenharia de software, bloqueado' }),
    );
    const dialog = await screen.findByRole('dialog', { name: 'Engenharia de software' });
    expect(within(dialog).getByText('Etapa Premium')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Ver planos' })).toBeDisabled();
    expect(
      within(dialog).queryByRole('group', { name: 'Status do tópico' }),
    ).not.toBeInTheDocument();
  });

  it('"Iniciar foco" e "Criar tarefa" levam o tópico para as outras telas', async () => {
    const { user, router } = setup();
    const topic = await screen.findByRole('region', { name: 'Tópico atual' });
    await user.click(within(topic).getByRole('button', { name: 'Iniciar foco' }));
    expect(useDeepWorkStore.getState().topic).toEqual({
      id: 'ed',
      title: 'Estruturas de dados',
      slug: 'ciencia-da-computacao',
    });
    await waitFor(() => expect(router.state.location.pathname).toBe('/deep-work'));

    await router.navigate('/roadmaps/ciencia-da-computacao');
    const again = await screen.findByRole('region', { name: 'Tópico atual' });
    await user.click(within(again).getByRole('button', { name: 'Criar tarefa' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/stack'));
    expect(await screen.findByDisplayValue('Estruturas de dados')).toBeInTheDocument();
    expect(useStackStore.getState().dialog).toEqual({ kind: 'task', title: 'Estruturas de dados' });
  });

  it('sem roadmap seguido convida a escolher um; com principal, abre ele', async () => {
    setup('/roadmaps', {
      'GET /api/roadmaps': () =>
        jsonResponse(200, {
          ...list,
          premiumEnabled: false,
          items: list.items.map((i) => ({
            ...i,
            following: false,
            primary: false,
            percent: undefined,
          })),
        }),
    });
    expect(await screen.findByText('Você ainda não segue nenhum roadmap')).toBeInTheDocument();
    expect(screen.getByText('Lançamento: tudo grátis por tempo limitado.')).toBeInTheDocument();
    const explore = screen.getByRole('region', { name: 'Explorar roadmaps' });
    expect(within(explore).getByRole('link', { name: /Direito/ })).toHaveAttribute(
      'href',
      '/roadmaps/direito',
    );
    expect(screen.getByRole('link', { name: 'Roadmaps' })).toHaveAttribute('href', '/roadmaps');
  });

  it('redireciona /roadmaps para o principal', async () => {
    const { router } = setup('/roadmaps');
    await waitFor(() =>
      expect(router.state.location.pathname).toBe('/roadmaps/ciencia-da-computacao'),
    );
  });

  it('erro ao carregar e envio de sugestão', async () => {
    const { user, fetchMock } = setup('/roadmaps/ciencia-da-computacao', {
      'GET /api/roadmaps/ciencia-da-computacao': () =>
        jsonResponse(500, { code: 'INTERNAL_ERROR' }),
      'POST /api/roadmaps/suggestions': () => jsonResponse(201, { id: 'x' }),
    });
    expect(await screen.findByText('Não foi possível carregar os roadmaps.')).toBeInTheDocument();
    const suggest = screen.getByRole('region', { name: 'Sugerir roadmap' });
    await user.type(within(suggest).getByLabelText('Curso ou tema'), 'Arquitetura');
    await user.click(within(suggest).getByRole('button', { name: 'Enviar sugestão' }));
    expect(
      await within(suggest).findByText('Obrigado! Sua sugestão foi registrada.'),
    ).toBeInTheDocument();
    const post = fetchMock.mock.calls.find(
      ([url, init]) => String(url).endsWith('/suggestions') && init?.method === 'POST',
    );
    expect(JSON.parse(String(post![1]!.body))).toEqual({ title: 'Arquitetura' });
  });
});

describe('lógica do mapa', () => {
  const stage = detail.stages[1]!;
  it('estado, horas e rótulo', () => {
    const { result } = renderHook(() => useTranslation());
    const t = result.current.t;
    expect(nodeState(stage.nodes[0]!, stage)).toBe('current');
    expect(nodeState(stage.nodes[2]!, stage)).toBe('pending');
    expect(nodeState(detail.stages[0]!.nodes[0]!, detail.stages[0]!)).toBe('done');
    expect(nodeState(stage.nodes[0]!, { locked: true })).toBe('locked');
    expect(remainingHours({ estimatedHours: 2, hoursSpent: 5 })).toBe(0);
    expect(remainingHours({ estimatedHours: 5, hoursSpent: 3.75 })).toBe(1.3);
    expect(
      nodeLabel(t, { ...stage.nodes[0]!, estimatedHours: 4, hoursSpent: 3 }, 'current', 'pt-BR'),
    ).toBe('Estruturas de dados, em andamento, 1 hora restante');
  });

  it('linhas do mapa e ordem do teclado', () => {
    const rows = buildRows(stage);
    expect(rows.map((r) => r.main.id)).toEqual(['ed', 'aa']);
    expect(rows[0]!.left.map((n) => n.id)).toEqual(['lpf']);
    const order = keyboardOrder(detail.stages);
    expect(order).toEqual(['lp', 'py', 'ed', 'lpf', 'aa', 'es']);
    expect(moveFocus(order, 'ed', 'ArrowDown')).toBe('lpf');
    expect(moveFocus(order, 'lp', 'ArrowUp')).toBe('lp');
    expect(moveFocus(order, 'aa', 'End')).toBe('es');
    expect(moveFocus(order, 'aa', 'Enter')).toBeUndefined();
  });

  it('tag do tópico e roadmap padrão', () => {
    expect(topicTag('Estruturas de dados: árvores & grafos')).toBe(
      'estruturas-de-dados-arvores-grafos',
    );
    expect(defaultSlug(list.items)).toBe('ciencia-da-computacao');
    expect(defaultSlug(list.items.map((i) => ({ ...i, primary: false })))).toBe(
      'ciencia-da-computacao',
    );
    expect(defaultSlug([])).toBeUndefined();
  });
});
