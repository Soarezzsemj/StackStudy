import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '@/app/router';
import i18n from '@/i18n';
import type { Card, Deck, FlashcardStats, ReviewQueue } from '@/lib/api/flashcards';
import { useAuthStore } from '@/stores/authStore';
import { useFlashcardStore } from '@/stores/flashcardStore';
import { emptyPage, jsonResponse, mockApi, renderWithProviders, testUser } from '@/test/utils';

const anatomy: Deck = {
  id: 'd1',
  name: 'Anatomia',
  totalCards: 30,
  newCards: 25,
  dueCards: 4,
  newToday: 20,
  dueToday: 4,
};
const law: Deck = {
  id: 'd2',
  name: 'Direito',
  totalCards: 3,
  newCards: 0,
  dueCards: 0,
  newToday: 0,
  dueToday: 0,
};

function card(id: string, partial: Partial<Card> = {}): Card {
  return {
    id,
    deckId: 'd1',
    front: `**Pergunta ${id}**`,
    back: `Resposta ${id}`,
    tags: ['ossos'],
    stage: 'NEW',
    intervalDays: 0,
    easeFactor: 2.5,
    repetitions: 0,
    lapses: 0,
    createdAt: '2026-11-20T10:00:00Z',
    ...partial,
  };
}

const stats: FlashcardStats = {
  today: '2026-11-20',
  timeZone: 'UTC',
  newCards: 25,
  learningCards: 6,
  matureCards: 2,
  reviews30d: 40,
  correct30d: 34,
  accuracyPct: 85,
  reviewsByDay: [{ date: '2026-11-19', count: 12 }],
  forecast: [{ date: '2026-11-21', count: 5 }],
};

function setup(overrides: Parameters<typeof mockApi>[0] = {}, path = '/flashcards') {
  useAuthStore.getState().setSession('token', testUser);
  const fetchMock = mockApi({
    'GET /api/flashcards/decks': () => jsonResponse(200, [anatomy, law]),
    'GET /api/flashcards/stats': () => jsonResponse(200, stats),
    'GET /api/flashcards/settings': () => jsonResponse(200, { newPerDay: 20, reviewsPerDay: 100 }),
    'GET /api/flashcards/cards': () => jsonResponse(200, emptyPage),
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

function body(fetchMock: ReturnType<typeof mockApi>, method: string, path: string, i = 0) {
  return JSON.parse(String(calls(fetchMock, method, path)[i]![1]!.body));
}

afterEach(async () => {
  cleanup();
  useAuthStore.getState().clear();
  useFlashcardStore.setState({ dialog: null, lastDeckId: null });
  await i18n.changeLanguage('pt-BR');
});

describe('FlashcardsPage', () => {
  it('lista decks com novas e para revisar, e as estatísticas', async () => {
    setup();
    const deck = await screen.findByRole('article', { name: 'Anatomia' });
    expect(within(deck).getByText('20')).toBeInTheDocument();
    expect(within(deck).getByText('4')).toBeInTheDocument();
    expect(
      within(deck).getByRole('link', { name: /Revisar Anatomia \(24 cartas hoje\)/ }),
    ).toHaveAttribute('href', '/flashcards/review?deck=d1');
    const done = screen.getByRole('article', { name: 'Direito' });
    expect(within(done).getByText('Tudo em dia por hoje.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Revisar tudo (24)' })).toBeInTheDocument();

    const statsCard = screen.getByRole('region', { name: 'Estatísticas' });
    expect(await within(statsCard).findByText('85')).toBeInTheDocument();
    expect(within(statsCard).getByText('40 respostas')).toBeInTheDocument();
    expect(
      within(statsCard).getByRole('img', { name: '12 revisões nos últimos 30 dias' }),
    ).toBeInTheDocument();
  });

  it('estado vazio sem decks', async () => {
    setup({ 'GET /api/flashcards/decks': () => jsonResponse(200, []) });
    expect(await screen.findByText('Nenhum deck ainda')).toBeInTheDocument();
  });

  it('erro ao carregar decks', async () => {
    setup({
      'GET /api/flashcards/decks': () =>
        jsonResponse(500, { code: 'INTERNAL_ERROR', message: 'x' }),
    });
    expect(await screen.findByText('Não foi possível carregar os decks.')).toBeInTheDocument();
  });

  it('busca cartas por texto ou tag', async () => {
    const { user, fetchMock } = setup({
      'GET /api/flashcards/cards': () =>
        jsonResponse(200, { ...emptyPage, items: [card('c1')], totalElements: 1 }),
    });
    await screen.findByRole('article', { name: 'Anatomia' });
    await user.type(screen.getByLabelText('Buscar cartas'), 'ossos');
    const results = await screen.findByRole('region', { name: 'Resultados da busca' });
    expect(await within(results).findByText('Pergunta c1')).toBeInTheDocument();
    expect(within(results).getByText('#ossos')).toBeInTheDocument();
    const url = String(calls(fetchMock, 'GET', '/api/flashcards/cards').at(-1)![0]);
    expect(url).toContain('q=ossos');
  });

  it('cria cartas em sequência pelo diálogo (salvar e criar outra)', async () => {
    const { user, fetchMock } = setup({
      'POST /api/flashcards/cards': () => jsonResponse(201, card('new')),
    });
    await screen.findByRole('article', { name: 'Anatomia' });
    await user.click(screen.getByRole('button', { name: 'Nova carta' }));
    const dialog = screen.getByRole('dialog', { name: 'Nova carta' });
    expect(within(dialog).getByLabelText('Frente')).toHaveFocus();
    await user.type(within(dialog).getByLabelText('Frente'), 'Maior osso?');
    await user.type(within(dialog).getByLabelText('Verso'), 'Fêmur');
    await user.type(within(dialog).getByLabelText('Tags'), 'Ossos, coxa');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar e criar outra' }));

    await waitFor(() => expect(calls(fetchMock, 'POST', '/api/flashcards/cards')).toHaveLength(1));
    expect(body(fetchMock, 'POST', '/api/flashcards/cards')).toEqual({
      deckId: 'd1',
      front: 'Maior osso?',
      back: 'Fêmur',
      tags: ['ossos', 'coxa'],
    });
    expect(await within(dialog).findByText('1 carta salva.')).toBeInTheDocument();
    expect(within(dialog).getByLabelText('Frente')).toHaveValue('');
    expect(within(dialog).getByLabelText('Tags')).toHaveValue('Ossos, coxa');
  });

  it('Ctrl K abre a criação rápida de carta', async () => {
    const { user } = setup();
    await screen.findByRole('article', { name: 'Anatomia' });
    await user.keyboard('{Control>}k{/Control}');
    await user.click(await screen.findByRole('option', { name: 'Nova carta' }));
    expect(await screen.findByRole('dialog', { name: 'Nova carta' })).toBeInTheDocument();
  });

  it('importa CSV e mostra o relatório de linhas inválidas', async () => {
    const { user, fetchMock } = setup(
      {
        'POST /api/flashcards/decks/d1/import': () =>
          jsonResponse(200, {
            imported: 2,
            errors: [
              { line: 3, code: 'FRONT_REQUIRED' },
              { line: 5, code: 'MISSING_COLUMNS' },
            ],
          }),
      },
      '/flashcards/decks/d1',
    );
    await user.click(await screen.findByRole('button', { name: 'Importar CSV' }));
    const dialog = screen.getByRole('dialog', { name: 'Importar cartas em Anatomia' });
    await user.type(within(dialog).getByLabelText('Ou cole o conteúdo'), 'a,b');
    await user.click(within(dialog).getByRole('button', { name: 'Importar' }));
    expect(await within(dialog).findByText('2 cartas importadas.')).toBeInTheDocument();
    expect(within(dialog).getByText('2 linhas não foram importadas:')).toBeInTheDocument();
    expect(within(dialog).getByText('frente vazia')).toBeInTheDocument();
    expect(within(dialog).getByText('Linha 5')).toBeInTheDocument();
    expect(body(fetchMock, 'POST', '/api/flashcards/decks/d1/import')).toEqual({ csv: 'a,b' });
  });

  it('página do deck lista as cartas e edita uma', async () => {
    const { user, fetchMock } = setup(
      {
        'GET /api/flashcards/cards': () =>
          jsonResponse(200, { ...emptyPage, items: [card('c1')], totalElements: 1 }),
        'PUT /api/flashcards/cards/c1': () => jsonResponse(200, card('c1')),
      },
      '/flashcards/decks/d1',
    );
    expect(await screen.findByRole('heading', { name: 'Anatomia' })).toBeInTheDocument();
    await user.click(await screen.findByRole('button', { name: 'Editar carta: Pergunta c1' }));
    const dialog = screen.getByRole('dialog', { name: 'Editar carta' });
    // Mover para outro deck.
    await user.selectOptions(within(dialog).getByLabelText('Deck'), 'd2');
    await user.click(within(dialog).getByRole('button', { name: 'Salvar' }));
    await waitFor(() =>
      expect(calls(fetchMock, 'PUT', '/api/flashcards/cards/c1')).toHaveLength(1),
    );
    expect(body(fetchMock, 'PUT', '/api/flashcards/cards/c1').deckId).toBe('d2');
  });
});

describe('ReviewPage', () => {
  const queue: ReviewQueue = {
    today: '2026-11-20',
    timeZone: 'UTC',
    newRemaining: 20,
    reviewRemaining: 100,
    dueCount: 1,
    newCount: 1,
    cards: [card('c1', { stage: 'LEARNING' }), card('c2')],
  };

  function setupReview(overrides: Parameters<typeof mockApi>[0] = {}) {
    let n = 0;
    return setup(
      {
        'GET /api/flashcards/queue': () => jsonResponse(200, queue),
        'POST /api/flashcards/cards/c1/review': () =>
          jsonResponse(200, { reviewId: `r${++n}`, card: card('c1') }),
        'POST /api/flashcards/cards/c2/review': () =>
          jsonResponse(200, { reviewId: `r${++n}`, card: card('c2') }),
        'DELETE /api/flashcards/reviews/r1': () => jsonResponse(200, card('c1')),
        ...overrides,
      },
      '/flashcards/review?deck=d1',
    );
  }

  it('Espaço revela, 1–4 respondem, U desfaz e o fim mostra o resumo', async () => {
    const { user, fetchMock } = setupReview();
    expect(await screen.findByText('Pergunta c1')).toBeInTheDocument();
    expect(screen.queryByText('Resposta c1')).not.toBeInTheDocument();
    expect(screen.getByText('1 de 2')).toBeInTheDocument();

    // 3 antes de revelar não faz nada.
    await user.keyboard('3');
    expect(calls(fetchMock, 'POST', '/api/flashcards/cards/c1/review')).toHaveLength(0);

    await user.keyboard(' ');
    expect(screen.getByText('Resposta c1')).toBeInTheDocument();
    await user.keyboard('3');
    expect(await screen.findByText('Pergunta c2')).toBeInTheDocument();
    expect(body(fetchMock, 'POST', '/api/flashcards/cards/c1/review')).toMatchObject({
      rating: 'GOOD',
    });

    // Desfaz: volta para c1 com o verso à mostra.
    await user.keyboard('u');
    expect(await screen.findByText('Resposta c1')).toBeInTheDocument();
    expect(calls(fetchMock, 'DELETE', '/api/flashcards/reviews/r1')).toHaveLength(1);

    await user.keyboard('1');
    await screen.findByText('Pergunta c2');
    await user.keyboard(' ');
    await user.click(screen.getByRole('button', { name: /Fácil/ }));

    const summary = await screen.findByRole('region', { name: 'Sessão concluída' });
    expect(within(summary).getByText('2')).toBeInTheDocument();
    expect(within(summary).getByText('50%')).toBeInTheDocument();
    expect(body(fetchMock, 'POST', '/api/flashcards/cards/c1/review', 1)).toMatchObject({
      rating: 'AGAIN',
    });
  });

  it('fila vazia', async () => {
    setupReview({
      'GET /api/flashcards/queue': () => jsonResponse(200, { ...queue, cards: [] }),
    });
    expect(await screen.findByText('Nada para revisar agora')).toBeInTheDocument();
  });

  it('erro ao responder aparece e a carta continua', async () => {
    const { user } = setupReview({
      'POST /api/flashcards/cards/c1/review': () =>
        jsonResponse(500, { code: 'INTERNAL_ERROR', message: 'x' }),
    });
    await screen.findByText('Pergunta c1');
    await user.keyboard(' ');
    await user.keyboard('4');
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('Pergunta c1')).toBeInTheDocument();
  });
});
