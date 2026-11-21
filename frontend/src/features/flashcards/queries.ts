import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTimeZone } from '@/lib/useTimeZone';
import {
  flashcardsApi,
  type CardInput,
  type DeckInput,
  type FlashcardSettings,
  type Rating,
} from '@/lib/api/flashcards';
import type { Page } from '@/lib/api/stack';
import type { Card } from '@/lib/api/flashcards';
import { useAuthStore } from '@/stores/authStore';

export const flashcardKeys = {
  all: ['flashcards'] as const,
  decks: (tz: string) => ['flashcards', 'decks', tz] as const,
  cards: (deckId: string | undefined, q: string) =>
    ['flashcards', 'cards', deckId ?? 'all', q] as const,
  queue: (tz: string, deckId: string | undefined) =>
    ['flashcards', 'queue', tz, deckId ?? 'all'] as const,
  stats: (tz: string) => ['flashcards', 'stats', tz] as const,
  settings: ['flashcards', 'settings'] as const,
};

function useIsAuthenticated() {
  return useAuthStore((s) => s.status === 'authenticated');
}

export function useDecks() {
  const tz = useTimeZone();
  return useQuery({
    queryKey: flashcardKeys.decks(tz),
    queryFn: () => flashcardsApi.decks(tz),
    enabled: useIsAuthenticated(),
  });
}

/** Cartas paginadas (busca por texto/tag, opcionalmente num deck). */
export function useCards(deckId: string | undefined, q: string, enabled = true) {
  return useInfiniteQuery({
    queryKey: flashcardKeys.cards(deckId, q),
    queryFn: ({ pageParam }) => flashcardsApi.cards({ deckId, q, page: pageParam, size: 50 }),
    initialPageParam: 0,
    getNextPageParam: (last: Page<Card>) => (last.hasNext ? last.page + 1 : undefined),
    enabled: useIsAuthenticated() && enabled,
  });
}

/**
 * Fila do dia. Lida uma vez por sessão: sem refetch automático, para a ordem não mudar no meio
 * da revisão.
 */
export function useQueue(deckId: string | undefined) {
  const tz = useTimeZone();
  return useQuery({
    queryKey: flashcardKeys.queue(tz, deckId),
    queryFn: () => flashcardsApi.queue(tz, deckId),
    enabled: useIsAuthenticated(),
    staleTime: Infinity,
    gcTime: 0,
    refetchOnWindowFocus: false,
  });
}

export function useFlashcardStats() {
  const tz = useTimeZone();
  return useQuery({
    queryKey: flashcardKeys.stats(tz),
    queryFn: () => flashcardsApi.stats(tz),
    enabled: useIsAuthenticated(),
  });
}

export function useFlashcardSettings() {
  return useQuery({
    queryKey: flashcardKeys.settings,
    queryFn: () => flashcardsApi.settings(),
    enabled: useIsAuthenticated(),
    staleTime: 5 * 60_000,
  });
}

/** Qualquer mudança mexe em contagens, buscas e estatísticas: invalida tudo dos flashcards. */
function useInvalidate() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: flashcardKeys.all });
}

export function useSaveDeck() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: DeckInput }) =>
      id ? flashcardsApi.updateDeck(id, input) : flashcardsApi.createDeck(input),
    onSuccess: invalidate,
  });
}

export function useDeleteDeck() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (id: string) => flashcardsApi.deleteDeck(id),
    onSuccess: invalidate,
  });
}

export function useSaveCard() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, input }: { id?: string; input: CardInput }) =>
      id ? flashcardsApi.updateCard(id, input) : flashcardsApi.createCard(input),
    onSuccess: invalidate,
  });
}

export function useDeleteCard() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (id: string) => flashcardsApi.deleteCard(id),
    onSuccess: invalidate,
  });
}

export function useSaveFlashcardSettings() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (input: FlashcardSettings) => flashcardsApi.updateSettings(input),
    onSuccess: invalidate,
  });
}

export function useImportCsv() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ deckId, csv }: { deckId: string; csv: string }) =>
      flashcardsApi.importCsv(deckId, csv),
    onSuccess: invalidate,
  });
}

/**
 * Responder e desfazer na revisão. Não invalidam a fila (ela é a da sessão); as contagens são
 * atualizadas ao sair da tela.
 */
export function useReviewActions() {
  const tz = useTimeZone();
  return {
    review: useMutation({
      mutationFn: ({ cardId, rating }: { cardId: string; rating: Rating }) =>
        flashcardsApi.review(cardId, rating, tz),
    }),
    undo: useMutation({ mutationFn: (reviewId: string) => flashcardsApi.undo(reviewId) }),
  };
}

/** Ao terminar ou sair da revisão: decks, estatísticas e a próxima fila precisam ser relidos. */
export function useRefreshAfterReview() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: flashcardKeys.all });
}
