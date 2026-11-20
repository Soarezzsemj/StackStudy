import { api } from './client';
import type { Page } from './stack';

// Tipos e chamadas dos Flashcards. Campos null são omitidos pelo backend (viram undefined).

export const RATINGS = ['AGAIN', 'HARD', 'GOOD', 'EASY'] as const;
export type Rating = (typeof RATINGS)[number];
export type Stage = 'NEW' | 'LEARNING' | 'MATURE';

export interface Deck {
  id: string;
  name: string;
  subjectId?: string;
  newPerDay?: number;
  reviewsPerDay?: number;
  totalCards: number;
  newCards: number;
  dueCards: number;
  /** Quantas entram hoje (limites diários aplicados). */
  newToday: number;
  dueToday: number;
}

export interface DeckInput {
  name: string;
  subjectId: string | null;
  newPerDay: number | null;
  reviewsPerDay: number | null;
}

export interface Card {
  id: string;
  deckId: string;
  front: string;
  back: string;
  tags: string[];
  stage: Stage;
  dueOn?: string;
  intervalDays: number;
  easeFactor: number;
  repetitions: number;
  lapses: number;
  createdAt: string;
}

export interface CardInput {
  deckId: string;
  front: string;
  back: string;
  tags: string[];
}

export interface ReviewQueue {
  today: string;
  timeZone: string;
  newRemaining: number;
  reviewRemaining: number;
  dueCount: number;
  newCount: number;
  cards: Card[];
}

export interface ReviewResult {
  reviewId: string;
  card: Card;
}

export interface FlashcardSettings {
  newPerDay: number;
  reviewsPerDay: number;
}

export interface DayCount {
  date: string;
  count: number;
}

export interface FlashcardStats {
  today: string;
  timeZone: string;
  newCards: number;
  learningCards: number;
  matureCards: number;
  reviews30d: number;
  correct30d: number;
  accuracyPct?: number;
  reviewsByDay: DayCount[];
  forecast: DayCount[];
}

export interface ImportError {
  line: number;
  code: string;
}

export interface ImportResult {
  imported: number;
  errors: ImportError[];
}

export interface ExportFile {
  fileName: string;
  content: string;
}

/** Limite do texto do CSV (igual ao backend). */
export const CSV_MAX_CHARS = 1_000_000;

const tzParam = (tz: string) => `tz=${encodeURIComponent(tz)}`;

export const flashcardsApi = {
  decks: (tz: string) => api.get<Deck[]>(`/flashcards/decks?${tzParam(tz)}`),
  createDeck: (input: DeckInput) => api.post<Deck>('/flashcards/decks', input),
  updateDeck: (id: string, input: DeckInput) => api.put<Deck>(`/flashcards/decks/${id}`, input),
  deleteDeck: (id: string) => api.delete<void>(`/flashcards/decks/${id}`),

  cards: (params: { deckId?: string; q?: string; page?: number; size?: number }) => {
    const query = new URLSearchParams();
    if (params.deckId) query.set('deckId', params.deckId);
    if (params.q) query.set('q', params.q);
    query.set('page', String(params.page ?? 0));
    query.set('size', String(params.size ?? 50));
    return api.get<Page<Card>>(`/flashcards/cards?${query.toString()}`);
  },
  createCard: (input: CardInput) => api.post<Card>('/flashcards/cards', input),
  updateCard: (id: string, input: CardInput) => api.put<Card>(`/flashcards/cards/${id}`, input),
  deleteCard: (id: string) => api.delete<void>(`/flashcards/cards/${id}`),

  queue: (tz: string, deckId?: string) =>
    api.get<ReviewQueue>(
      `/flashcards/queue?${tzParam(tz)}${deckId ? `&deckId=${encodeURIComponent(deckId)}` : ''}`,
    ),
  review: (cardId: string, rating: Rating, tz: string) =>
    api.post<ReviewResult>(`/flashcards/cards/${cardId}/review`, { rating, tz }),
  undo: (reviewId: string) => api.delete<Card>(`/flashcards/reviews/${reviewId}`),

  stats: (tz: string) => api.get<FlashcardStats>(`/flashcards/stats?${tzParam(tz)}`),
  settings: () => api.get<FlashcardSettings>('/flashcards/settings'),
  updateSettings: (input: FlashcardSettings) =>
    api.put<FlashcardSettings>('/flashcards/settings', input),

  importCsv: (deckId: string, csv: string) =>
    api.post<ImportResult>(`/flashcards/decks/${deckId}/import`, { csv }),
  exportCsv: (deckId: string) => api.get<ExportFile>(`/flashcards/decks/${deckId}/export`),
};
