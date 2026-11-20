import type { Card, Rating } from '@/lib/api/flashcards';

/** Estado da sessão de revisão (só no navegador; cada resposta já foi salva no servidor). */
export interface ReviewSessionState {
  cards: Card[];
  index: number;
  revealed: boolean;
  /** Respostas na ordem, para desfazer a última. */
  history: { cardId: string; rating: Rating; reviewId: string }[];
  startedAt: number;
}

export type ReviewAction =
  | { type: 'start'; cards: Card[]; now: number }
  | { type: 'reveal' }
  | { type: 'answered'; rating: Rating; reviewId: string }
  | { type: 'undone' };

export function initialSession(cards: Card[] = [], now = 0): ReviewSessionState {
  return { cards, index: 0, revealed: false, history: [], startedAt: now };
}

export function reviewReducer(state: ReviewSessionState, action: ReviewAction): ReviewSessionState {
  switch (action.type) {
    case 'start':
      return initialSession(action.cards, action.now);
    case 'reveal':
      return isFinished(state) ? state : { ...state, revealed: true };
    case 'answered': {
      const card = state.cards[state.index];
      if (!card) return state;
      return {
        ...state,
        index: state.index + 1,
        revealed: false,
        history: [
          ...state.history,
          { cardId: card.id, rating: action.rating, reviewId: action.reviewId },
        ],
      };
    }
    case 'undone':
      // Volta para a carta desfeita, já com o verso à mostra (ela acabou de ser vista).
      if (state.history.length === 0) return state;
      return {
        ...state,
        index: state.index - 1,
        revealed: true,
        history: state.history.slice(0, -1),
      };
  }
}

export function isFinished(state: ReviewSessionState): boolean {
  return state.index >= state.cards.length;
}

export function currentCard(state: ReviewSessionState): Card | undefined {
  return state.cards[state.index];
}

export interface SessionSummary {
  total: number;
  byRating: Record<Rating, number>;
  /** Acertos (tudo menos "Errei") em %, sem casas; null sem respostas. */
  accuracyPct: number | null;
  elapsedSeconds: number;
}

export function summarize(state: ReviewSessionState, now: number): SessionSummary {
  const byRating: Record<Rating, number> = { AGAIN: 0, HARD: 0, GOOD: 0, EASY: 0 };
  for (const h of state.history) byRating[h.rating]++;
  const total = state.history.length;
  return {
    total,
    byRating,
    accuracyPct: total === 0 ? null : Math.round(((total - byRating.AGAIN) / total) * 100),
    elapsedSeconds: Math.max(0, Math.round((now - state.startedAt) / 1000)),
  };
}

/** Tecla → ação da tela de revisão (null = ignorar). */
export function reviewKey(
  key: string,
  revealed: boolean,
): { type: 'reveal' } | { type: 'rate'; rating: Rating } | { type: 'undo' } | null {
  if (key === ' ' || key === 'Enter') return revealed ? null : { type: 'reveal' };
  if (key === 'u' || key === 'U' || key === 'z' || key === 'Z') return { type: 'undo' };
  const index = ['1', '2', '3', '4'].indexOf(key);
  if (index >= 0 && revealed) {
    return { type: 'rate', rating: (['AGAIN', 'HARD', 'GOOD', 'EASY'] as const)[index]! };
  }
  return null;
}
