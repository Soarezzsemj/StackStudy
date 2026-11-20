import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '@/app/router';
import i18n from '@/i18n';
import type { Semester, Subject, Task } from '@/lib/api/stack';
import { useAuthStore } from '@/stores/authStore';
import { emptyPage, jsonResponse, mockApi, renderWithProviders, testUser } from '@/test/utils';

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
  credits: 6,
  color: '#bd6cf7',
  status: 'IN_PROGRESS',
  pendingTasks: 2,
};

function makeTask(partial: Partial<Task> & { id: string; title: string }): Task {
  return {
    type: 'OTHER',
    priority: 'MEDIUM',
    status: 'TODO',
    dueAllDay: false,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...partial,
  };
}

const yesterday = new Date(Date.now() - 24 * 3600 * 1000).toISOString();
const endOfToday = (() => {
  const d = new Date();
  d.setHours(23, 59, 59, 0);
  return d.toISOString();
})();

const tasks: Task[] = [
  makeTask({ id: 't1', title: 'Prova prática', subjectId: 'sub1', type: 'EXAM', dueAt: yesterday }),
  makeTask({
    id: 't2',
    title: 'Ler capítulo 4',
    subjectId: 'sub1',
    dueAt: endOfToday,
    dueAllDay: true,
  }),
  makeTask({ id: 't3', title: 'Renovar livros' }),
];

function page(items: Task[]) {
  return { ...emptyPage, items, totalElements: items.length, totalPages: 1 };
}

function setup(overrides: Parameters<typeof mockApi>[0] = {}) {
  useAuthStore.getState().setSession('token', testUser);
  const fetchMock = mockApi({
    'GET /api/semesters': () => jsonResponse(200, [semester]),
    'GET /api/subjects': () => jsonResponse(200, [anatomy]),
    'GET /api/tasks': () => jsonResponse(200, page(tasks)),
    ...overrides,
  });
  const utils = renderWithProviders(routes, { initialEntries: ['/stack'] });
  return { ...utils, fetchMock };
}

/** Chamadas feitas para um "MÉTODO /caminho". */
function calls(fetchMock: ReturnType<typeof mockApi>, method: string, path: string) {
  return fetchMock.mock.calls.filter(
    ([url, init]) => (init?.method ?? 'GET') === method && String(url).split('?')[0] === path,
  );
}

afterEach(async () => {
  // Desmonta antes de trocar o idioma, para não re-renderizar fora do act().
  cleanup();
  await i18n.changeLanguage('pt-BR');
});

describe('The Stack page', () => {
  it('groups tasks by due date and shows subjects with pending counts', async () => {
    setup();
    expect(await screen.findByRole('heading', { name: /Atrasadas/ })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Hoje/ })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Sem prazo/ })).toBeInTheDocument();

    const overdue = screen.getByRole('heading', { name: /Atrasadas/ }).closest('section')!;
    expect(within(overdue).getByText('Prova prática')).toBeInTheDocument();

    const panel = screen.getByRole('region', { name: 'Matérias' });
    const subjectButton = await within(panel).findByRole('button', { name: /^Anatomia/ });
    expect(subjectButton).toHaveTextContent('2');
    expect(within(panel).getByRole('combobox', { name: 'Semestre atual' })).toHaveValue('s1');
  });

  it('filters by subject, type and search through the API', async () => {
    const { fetchMock } = setup();
    const panel = await screen.findByRole('region', { name: 'Matérias' });
    await userEvent.click(await within(panel).findByRole('button', { name: /^Anatomia/ }));
    await waitFor(() =>
      expect(
        calls(fetchMock, 'GET', '/api/tasks').some(([u]) => String(u).includes('subjectId=sub1')),
      ).toBe(true),
    );

    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Tipo' }), 'EXAM');
    await userEvent.type(screen.getByRole('searchbox', { name: 'Buscar tarefas' }), 'prova');
    await waitFor(() =>
      expect(
        calls(fetchMock, 'GET', '/api/tasks').some(([u]) => {
          const q = new URLSearchParams(String(u).split('?')[1]);
          return (
            q.get('type') === 'EXAM' && q.get('q') === 'prova' && q.get('subjectId') === 'sub1'
          );
        }),
      ).toBe(true),
    );
  });

  it('quick add creates a task with Enter', async () => {
    const { fetchMock } = setup({
      'POST /api/tasks': (init) =>
        jsonResponse(201, makeTask({ id: 'new', title: JSON.parse(String(init?.body)).title })),
    });
    const input = await screen.findByRole('textbox', { name: 'Adicionar tarefa rápida' });
    await userEvent.type(input, 'Resumo de História{Enter}');

    await waitFor(() => expect(calls(fetchMock, 'POST', '/api/tasks')).toHaveLength(1));
    const [, init] = calls(fetchMock, 'POST', '/api/tasks')[0]!;
    expect(JSON.parse(String(init!.body))).toMatchObject({
      title: 'Resumo de História',
      subjectId: null,
      dueAt: null,
    });
    await waitFor(() => expect(input).toHaveValue(''));
  });

  it('marks as done with one click', async () => {
    const { fetchMock } = setup({
      'PATCH /api/tasks/t3/status': () =>
        jsonResponse(200, makeTask({ id: 't3', title: 'Renovar livros', status: 'DONE' })),
    });
    await userEvent.click(
      await screen.findByRole('button', { name: 'Marcar "Renovar livros" como feita' }),
    );
    await waitFor(() => expect(calls(fetchMock, 'PATCH', '/api/tasks/t3/status')).toHaveLength(1));
    const [, init] = calls(fetchMock, 'PATCH', '/api/tasks/t3/status')[0]!;
    expect(JSON.parse(String(init!.body))).toEqual({ status: 'DONE' });
  });

  it('keyboard: arrows select, Space marks done, E edits, N creates', async () => {
    const { fetchMock } = setup({
      'PATCH /api/tasks/t1/status': () =>
        jsonResponse(200, makeTask({ id: 't1', title: 'Prova prática', status: 'DONE' })),
    });
    await screen.findByText('Prova prática');

    // Primeira seta seleciona a primeira tarefa visível (a atrasada).
    await userEvent.keyboard('{ArrowDown}');
    expect(document.activeElement).toHaveAttribute('data-task-id', 't1');

    await userEvent.keyboard(' ');
    await waitFor(() => expect(calls(fetchMock, 'PATCH', '/api/tasks/t1/status')).toHaveLength(1));

    await userEvent.keyboard('e');
    const edit = await screen.findByRole('dialog', { name: 'Editar tarefa' });
    expect(within(edit).getByRole('textbox', { name: 'Título' })).toHaveValue('Prova prática');
    await userEvent.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    await userEvent.keyboard('n');
    expect(await screen.findByRole('dialog', { name: 'Nova tarefa' })).toBeInTheDocument();
  });

  it('shortcuts do not fire while typing', async () => {
    setup();
    const input = await screen.findByRole('textbox', { name: 'Adicionar tarefa rápida' });
    await userEvent.type(input, 'n');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('creates a task from the dialog with due date and time', async () => {
    const { fetchMock } = setup({
      'POST /api/tasks': () => jsonResponse(201, makeTask({ id: 'n', title: 'P2' })),
    });
    await userEvent.click(await screen.findByRole('button', { name: 'Nova tarefa' }));
    const dialog = await screen.findByRole('dialog', { name: 'Nova tarefa' });

    // Título vazio é barrado no cliente.
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }));
    expect(within(dialog).getByRole('textbox', { name: 'Título' })).toHaveAttribute(
      'aria-invalid',
      'true',
    );

    await userEvent.type(within(dialog).getByRole('textbox', { name: 'Título' }), 'P2');
    await userEvent.selectOptions(
      within(dialog).getByRole('combobox', { name: 'Matéria' }),
      'sub1',
    );
    await userEvent.selectOptions(within(dialog).getByRole('combobox', { name: 'Tipo' }), 'EXAM');
    await userEvent.type(within(dialog).getByLabelText('Data do prazo'), '2026-12-01');
    await userEvent.type(within(dialog).getByLabelText('Hora'), '14:00');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    await waitFor(() => expect(calls(fetchMock, 'POST', '/api/tasks')).toHaveLength(1));
    const body = JSON.parse(String(calls(fetchMock, 'POST', '/api/tasks')[0]![1]!.body));
    expect(body).toMatchObject({ title: 'P2', subjectId: 'sub1', type: 'EXAM', dueAllDay: false });
    expect(new Date(body.dueAt).getHours()).toBe(14);
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('shows backend validation errors on the field', async () => {
    setup({
      'POST /api/subjects': () =>
        jsonResponse(400, {
          code: 'VALIDATION_ERROR',
          message: 'Request validation failed',
          details: [{ field: 'name', message: 'size must be between 0 and 120' }],
        }),
    });
    const panel = await screen.findByRole('region', { name: 'Matérias' });
    await userEvent.click(await within(panel).findByRole('button', { name: 'Nova matéria' }));
    const dialog = await screen.findByRole('dialog', { name: 'Nova matéria' });
    await userEvent.type(within(dialog).getByRole('textbox', { name: 'Nome' }), 'Contabilidade');
    await userEvent.click(within(dialog).getByRole('radio', { name: 'Ciano' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Salvar' }));

    expect(await within(dialog).findByText('size must be between 0 and 120')).toBeInTheDocument();
    expect(within(dialog).getByRole('alert')).toHaveTextContent('Confira os campos destacados.');
  });

  it('empty state offers to clear filters; error state offers retry', async () => {
    setup({ 'GET /api/tasks': () => jsonResponse(200, page([])) });
    expect(await screen.findByText('Nada pendente por aqui')).toBeInTheDocument();

    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Status' }), 'DONE');
    expect(await screen.findByText('Nenhuma tarefa com esses filtros.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Limpar filtros' }));
    expect(await screen.findByText('Nada pendente por aqui')).toBeInTheDocument();
  });

  it('shows an error with retry when tasks fail to load', async () => {
    setup({ 'GET /api/tasks': () => jsonResponse(500, { code: 'INTERNAL_ERROR' }) });
    expect(await screen.findByText('Não foi possível carregar as tarefas.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Tentar novamente' })).toBeInTheDocument();
  });

  it('without semesters, prompts to create one', async () => {
    setup({
      'GET /api/semesters': () => jsonResponse(200, []),
      'GET /api/subjects': () => jsonResponse(200, []),
    });
    expect(await screen.findByText('Comece pelo semestre')).toBeInTheDocument();
    expect(screen.getByText('Crie um semestre para adicionar matérias.')).toBeInTheDocument();
  });

  it('renders in English', async () => {
    await i18n.changeLanguage('en');
    setup();
    expect(await screen.findByRole('heading', { name: /Overdue/ })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /No due date/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'New task' })).toBeInTheDocument();
  });

  it('command palette opens "New subject" from another page', async () => {
    useAuthStore.getState().setSession('token', testUser);
    mockApi({
      'GET /api/semesters': () => jsonResponse(200, [semester]),
      'GET /api/subjects': () => jsonResponse(200, [anatomy]),
    });
    const { router } = renderWithProviders(routes, { initialEntries: ['/'] });
    await screen.findByRole('heading', { level: 1, name: 'Cockpit' });

    await userEvent.keyboard('{Control>}k{/Control}');
    await userEvent.click(await screen.findByRole('option', { name: /Nova matéria/ }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/stack'));
    expect(await screen.findByRole('dialog', { name: 'Nova matéria' })).toBeInTheDocument();
  });
});
