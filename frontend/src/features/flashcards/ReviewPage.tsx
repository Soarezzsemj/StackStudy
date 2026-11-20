import { useEffect, useReducer, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router-dom';
import {
  Button,
  buttonClass,
  Card,
  Chip,
  EmptyState,
  ErrorState,
  LoadingState,
  PageTitle,
  ProgressBar,
} from '@/components/ui';
import { errorMessage } from '@/features/auth/errorMessage';
import { RATINGS, type Rating } from '@/lib/api/flashcards';
import { hasModifier, isTypingTarget } from '@/lib/keyboard';
import { useUiStore } from '@/stores/uiStore';
import { Markdown } from './Markdown';
import { useDecks, useQueue, useRefreshAfterReview, useReviewActions } from './queries';
import {
  currentCard,
  initialSession,
  isFinished,
  reviewKey,
  reviewReducer,
  summarize,
} from './reviewSession';

const RATING_KEY: Record<Rating, string> = { AGAIN: '1', HARD: '2', GOOD: '3', EASY: '4' };

/**
 * Revisão em foco: frente → Espaço revela o verso → 1–4 responde. Cada resposta é salva na hora;
 * U (ou Z) desfaz a última. No fim, resumo da sessão.
 */
export function ReviewPage() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const deckId = params.get('deck') ?? undefined;
  const queue = useQueue(deckId);
  const decks = useDecks();
  const refresh = useRefreshAfterReview();
  const { review, undo } = useReviewActions();
  const [state, dispatch] = useReducer(reviewReducer, undefined, () => initialSession());
  const [started, setStarted] = useState(false);
  const paletteOpen = useUiStore((s) => s.paletteOpen);
  const revealRef = useRef<HTMLButtonElement>(null);
  const deckName = deckId ? decks.data?.find((d) => d.id === deckId)?.name : undefined;

  // A sessão começa com a fila lida do servidor (uma vez).
  useEffect(() => {
    if (queue.data && !started) {
      dispatch({ type: 'start', cards: queue.data.cards, now: Date.now() });
      setStarted(true);
    }
  }, [queue.data, started]);

  // Ao sair da tela, contagens e estatísticas são relidas.
  useEffect(() => () => void refresh(), []); // eslint-disable-line react-hooks/exhaustive-deps

  const card = currentCard(state);
  const finished = started && isFinished(state);
  const busy = review.isPending || undo.isPending;

  function answer(rating: Rating) {
    if (!card || !state.revealed || busy) return;
    review.mutate(
      { cardId: card.id, rating },
      { onSuccess: (r) => dispatch({ type: 'answered', rating, reviewId: r.reviewId }) },
    );
  }

  function undoLast() {
    const last = state.history[state.history.length - 1];
    if (!last || busy) return;
    undo.mutate(last.reviewId, { onSuccess: () => dispatch({ type: 'undone' }) });
  }

  useEffect(() => {
    if (paletteOpen) return;
    function onKeyDown(e: KeyboardEvent) {
      if (e.repeat || isTypingTarget(e.target)) return;
      const isUndo = (e.key === 'z' || e.key === 'Z') && (e.ctrlKey || e.metaKey);
      if (!isUndo && hasModifier(e)) return;
      // Enter/Espaço num botão focado já fazem o clique: não duplicar.
      if ((e.key === ' ' || e.key === 'Enter') && e.target instanceof HTMLButtonElement) return;
      const action = reviewKey(e.key, state.revealed);
      if (!action) return;
      e.preventDefault();
      if (action.type === 'reveal') dispatch({ type: 'reveal' });
      else if (action.type === 'rate') answer(action.rating);
      else undoLast();
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  });

  const title = deckName ?? t('flashcards.review.title');
  const progress = state.cards.length === 0 ? 0 : (state.index / state.cards.length) * 100;

  return (
    <>
      <PageTitle
        eyebrow={
          <Link to="/flashcards" className="hover:text-text">
            ← {t('flashcards.decks.back')}
          </Link>
        }
        title={title}
      />

      {queue.isPending ? (
        <LoadingState />
      ) : queue.isError ? (
        <ErrorState
          message={t('flashcards.review.loadError')}
          onRetry={() => void queue.refetch()}
        />
      ) : !started ? (
        <LoadingState />
      ) : state.cards.length === 0 ? (
        <EmptyState
          title={t('flashcards.review.emptyTitle')}
          body={t('flashcards.review.emptyBody')}
          action={
            <Link to="/flashcards" className={buttonClass('secondary')}>
              {t('flashcards.decks.back')}
            </Link>
          }
        />
      ) : finished ? (
        <Summary state={state} onUndo={undoLast} undoing={undo.isPending} />
      ) : (
        card && (
          <div className="mx-auto flex max-w-3xl flex-col gap-5">
            <div className="flex items-center gap-4">
              <ProgressBar
                className="flex-1"
                value={progress}
                label={t('flashcards.review.progress')}
              />
              <span className="tabular text-sm text-text-secondary" aria-live="polite">
                {t('flashcards.review.position', {
                  current: state.index + 1,
                  total: state.cards.length,
                })}
              </span>
            </div>

            <Card
              variant="hero"
              as="article"
              aria-label={t('flashcards.review.cardLabel')}
              className="flex min-h-[320px] flex-col gap-6 p-8 text-center text-xl"
            >
              <div className="flex justify-center">
                <Chip tone={card.stage === 'NEW' ? 'space' : 'neutral'}>
                  {t(`flashcards.stage.${card.stage}`)}
                </Chip>
              </div>
              <section aria-label={t('flashcards.card.front')}>
                <Markdown source={card.front} className="items-center" />
              </section>
              {state.revealed && (
                <section
                  aria-label={t('flashcards.card.back')}
                  aria-live="polite"
                  className="border-t border-border pt-6 text-lg text-text-secondary"
                >
                  <Markdown source={card.back} className="items-center" />
                </section>
              )}
            </Card>

            {state.revealed ? (
              <div
                role="group"
                aria-label={t('flashcards.review.answerGroup')}
                className="grid grid-cols-2 gap-3 sm:grid-cols-4"
              >
                {RATINGS.map((rating) => (
                  <Button
                    key={rating}
                    variant={rating === 'GOOD' ? 'primary' : 'secondary'}
                    className={rating === 'AGAIN' ? 'text-danger' : undefined}
                    disabled={busy}
                    aria-keyshortcuts={RATING_KEY[rating]}
                    onClick={() => answer(rating)}
                  >
                    <span className="tabular text-text-muted">{RATING_KEY[rating]}</span>
                    {t(`flashcards.rating.${rating}`)}
                  </Button>
                ))}
              </div>
            ) : (
              <Button
                ref={revealRef}
                className="self-center"
                aria-keyshortcuts="Space"
                onClick={() => dispatch({ type: 'reveal' })}
              >
                {t('flashcards.review.reveal')}
              </Button>
            )}

            <div className="flex flex-wrap items-center justify-between gap-3 text-xs text-text-muted">
              <span>{t('flashcards.review.shortcuts')}</span>
              <Button
                variant="ghost"
                disabled={state.history.length === 0 || busy}
                aria-keyshortcuts="U"
                onClick={undoLast}
              >
                {t('flashcards.review.undo')}
              </Button>
            </div>
            {(review.error || undo.error) && (
              <ErrorState message={errorMessage(t, review.error ?? undo.error)} />
            )}
          </div>
        )
      )}
    </>
  );
}

function Summary({
  state,
  onUndo,
  undoing,
}: {
  state: Parameters<typeof summarize>[0];
  onUndo: () => void;
  undoing: boolean;
}) {
  const { t } = useTranslation();
  const [now] = useState(() => Date.now());
  const s = summarize(state, now);
  const minutes = Math.floor(s.elapsedSeconds / 60);
  const seconds = s.elapsedSeconds % 60;

  return (
    <Card
      variant="hero"
      title={t('flashcards.summary.title')}
      aria-label={t('flashcards.summary.title')}
      className="mx-auto max-w-2xl"
    >
      <dl className="m-0 grid grid-cols-2 gap-4 sm:grid-cols-3">
        <Stat label={t('flashcards.summary.answered')} value={String(s.total)} />
        <Stat
          label={t('flashcards.summary.accuracy')}
          value={s.accuracyPct === null ? '—' : `${s.accuracyPct}%`}
        />
        <Stat
          label={t('flashcards.summary.time')}
          value={`${minutes}:${String(seconds).padStart(2, '0')}`}
        />
      </dl>
      <ul className="m-0 mt-6 grid list-none grid-cols-2 gap-2 p-0 text-sm sm:grid-cols-4">
        {RATINGS.map((r) => (
          <li key={r} className="card-inner px-3 py-2">
            <span className="text-text-muted">{t(`flashcards.rating.${r}`)}</span>{' '}
            <span className="tabular font-semibold">{s.byRating[r]}</span>
          </li>
        ))}
      </ul>
      <div className="mt-6 flex flex-wrap gap-3">
        <Link to="/flashcards" className={buttonClass('primary')}>
          {t('flashcards.summary.done')}
        </Link>
        <Button variant="ghost" disabled={undoing || state.history.length === 0} onClick={onUndo}>
          {t('flashcards.review.undo')}
        </Button>
      </div>
    </Card>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col gap-1">
      <dt className="label text-text-muted">{label}</dt>
      <dd className="tabular m-0 text-4xl font-[650] leading-none tracking-card">{value}</dd>
    </div>
  );
}
