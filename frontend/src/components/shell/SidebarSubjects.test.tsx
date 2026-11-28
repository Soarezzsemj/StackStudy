import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '@/app/router';
import type { GradesOverview } from '@/lib/api/grades';
import type { Semester, Subject } from '@/lib/api/stack';
import { useAuthStore } from '@/stores/authStore';
import { useStackStore } from '@/stores/stackStore';
import { subjectWith } from '@/test/gradeFixtures';
import { emptyOverview, jsonResponse, mockApi, renderWithProviders, testUser } from '@/test/utils';

// Grade de matérias da barra lateral (Fase 14).

const semester: Semester = {
  id: 's1',
  name: '2026.2',
  startDate: '2026-08-01',
  endDate: '2026-12-15',
  archived: false,
  current: true,
};

function subject(id: string, name: string, color = '#6aa7ff'): Subject {
  return {
    id,
    semesterId: 's1',
    name,
    credits: 4,
    color,
    status: 'IN_PROGRESS',
    pendingTasks: 0,
  };
}

/** Matérias em ordem de criação (como a API devolve) e as notas de algumas delas. */
function setup(subjects: Subject[], grades: GradesOverview['subjects'] = []) {
  useAuthStore.getState().setSession('token', testUser);
  const fetchMock = mockApi({
    'GET /api/semesters': () => jsonResponse(200, [semester]),
    'GET /api/subjects': () => jsonResponse(200, subjects),
    'GET /api/grades/overview': () =>
      jsonResponse(200, { ...emptyOverview, semesterId: 's1', subjects: grades }),
  });
  const utils = renderWithProviders(routes, { initialEntries: ['/flashcards'] });
  return { ...utils, fetchMock, user: userEvent.setup() };
}

function sidebarGrid() {
  return screen.findByRole('region', { name: 'Matérias · 2026.2' });
}

afterEach(() => {
  cleanup();
  useAuthStore.getState().clear();
  useStackStore.getState().resetFilters();
  vi.unstubAllGlobals();
});

describe('Matérias na barra lateral', () => {
  it('quadradinho com nome completo no aria-label/title e a situação (nota ou —)', async () => {
    const calc = subject('m1', 'Cálculo Diferencial e Integral I');
    const law = subject('m2', 'Direito Constitucional', '#f2b861');
    setup([calc, law], [{ ...subjectWith('WEIGHTED', [7, 8], 'm1'), name: calc.name }]);

    const grid = await sidebarGrid();
    const tile = await within(grid).findByRole('button', {
      name: 'Cálculo Diferencial e Integral I: 7,50',
    });
    expect(tile).toHaveAttribute('title', 'Cálculo Diferencial e Integral I: 7,50');
    expect(tile).toHaveTextContent('7,50');
    const empty = within(grid).getByRole('button', { name: 'Direito Constitucional: sem nota' });
    expect(empty).toHaveTextContent('—');
    // Sem "ver todas" com poucas matérias.
    expect(within(grid).queryByRole('link', { name: /Ver todas/ })).not.toBeInTheDocument();
  });

  it('na soma, a situação é o total de pontos', async () => {
    const hist = subject('h', 'História');
    setup([hist], [{ ...subjectWith('SUM', [4, undefined], 'h'), name: 'História' }]);
    const grid = await sidebarGrid();
    expect(await within(grid).findByRole('button', { name: 'História: 4,00' })).toBeVisible();
  });

  it('clicar abre a matéria na The Stack (mesma ação de antes)', async () => {
    const { user, router } = setup([subject('m1', 'Anatomia'), subject('m2', 'Contabilidade')]);
    const grid = await sidebarGrid();
    await user.click(await within(grid).findByRole('button', { name: 'Contabilidade: sem nota' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/stack'));
    expect(useStackStore.getState().filters.subject).toBe('m2');
  });

  it('mais de 8: as 8 mais recentes primeiro e o link "ver todas"', async () => {
    const many = Array.from({ length: 11 }, (_, i) => subject(`m${i + 1}`, `Matéria ${i + 1}`));
    setup(many);
    const grid = await sidebarGrid();
    await within(grid).findByRole('button', { name: 'Matéria 11: sem nota' });
    const tiles = within(grid).getAllByRole('button');
    expect(tiles).toHaveLength(8);
    expect(tiles[0]).toHaveAccessibleName('Matéria 11: sem nota');
    expect(tiles[7]).toHaveAccessibleName('Matéria 4: sem nota');
    expect(
      within(grid).queryByRole('button', { name: 'Matéria 1: sem nota' }),
    ).not.toBeInTheDocument();
    expect(within(grid).getByRole('link', { name: 'Ver todas (11)' })).toHaveAttribute(
      'href',
      '/stack',
    );
  });

  it('menu recolhido (tablet): uma coluna de pontos coloridos com tooltip', async () => {
    // Só a faixa do tablet casa.
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: query.includes('768px'),
      media: query,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    }));
    setup([subject('m1', 'Anatomia', '#bd6cf7')]);
    const rail = await screen.findByRole('complementary', { name: 'Menu recolhido' });
    const dot = await within(rail).findByRole('button', { name: 'Anatomia: sem nota' });
    expect(dot).toHaveAttribute('title', 'Anatomia: sem nota');
    // Só o ponto colorido, sem texto visível.
    expect(dot).toHaveTextContent('');
    expect(dot.querySelector('span')).toHaveStyle({ backgroundColor: '#bd6cf7' });
    // Navegação só com ícones, com nome acessível.
    expect(within(rail).getByRole('link', { name: 'The Stack' })).toBeInTheDocument();
  });
});
