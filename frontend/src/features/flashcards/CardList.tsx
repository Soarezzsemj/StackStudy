import { useTranslation } from 'react-i18next';
import { Button, Chip, EmptyState, ErrorState, Icon, LoadingState } from '@/components/ui';
import type { Card, Deck } from '@/lib/api/flashcards';
import { useFlashcardStore } from '@/stores/flashcardStore';
import { plainText } from './markdownParse';
import { useCards } from './queries';

const STAGE_TONE = { NEW: 'space', LEARNING: 'warning', MATURE: 'action' } as const;

/** Lista paginada de cartas (busca global ou de um deck), com edição pelo diálogo. */
export function CardList({
  deckId,
  q,
  decks,
  emptyTitle,
}: {
  deckId?: string;
  q: string;
  decks: Deck[];
  emptyTitle: string;
}) {
  const { t, i18n } = useTranslation();
  const openDialog = useFlashcardStore((s) => s.openDialog);
  const cards = useCards(deckId, q);
  const items = cards.data?.pages.flatMap((p) => p.items) ?? [];
  const total = cards.data?.pages[0]?.totalElements ?? 0;
  const deckName = new Map(decks.map((d) => [d.id, d.name] as const));
  const dateFormat = new Intl.DateTimeFormat(i18n.language, {
    day: '2-digit',
    month: 'short',
    timeZone: 'UTC',
  });

  if (cards.isPending) return <LoadingState />;
  if (cards.isError) {
    return (
      <ErrorState message={t('flashcards.cards.loadError')} onRetry={() => void cards.refetch()} />
    );
  }
  if (items.length === 0) return <EmptyState title={emptyTitle} />;

  return (
    <div className="flex flex-col gap-3">
      <p className="text-sm text-text-muted" aria-live="polite">
        {t('flashcards.cards.count', { count: total })}
      </p>
      <ul className="m-0 flex list-none flex-col gap-2 p-0">
        {items.map((card: Card) => (
          <li key={card.id}>
            <div className="card-inner flex items-start gap-3 px-4 py-3">
              <div className="min-w-0 flex-1">
                <p className="truncate font-semibold">{plainText(card.front)}</p>
                <p className="truncate text-sm text-text-secondary">{plainText(card.back)}</p>
                <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-text-muted">
                  <Chip tone={STAGE_TONE[card.stage]}>{t(`flashcards.stage.${card.stage}`)}</Chip>
                  {!deckId && <span>{deckName.get(card.deckId)}</span>}
                  {card.dueOn && (
                    <span className="tabular">
                      {t('flashcards.cards.due', {
                        date: dateFormat.format(new Date(`${card.dueOn}T00:00:00Z`)),
                      })}
                    </span>
                  )}
                  {card.tags.map((tag) => (
                    <span key={tag} className="rounded-pill border border-border px-2 py-0.5">
                      #{tag}
                    </span>
                  ))}
                </div>
              </div>
              <Button
                variant="ghost"
                iconOnly
                aria-label={t('flashcards.cards.editNamed', { front: plainText(card.front) })}
                onClick={() => openDialog({ kind: 'card', card })}
              >
                <Icon name="edit" size={18} />
              </Button>
            </div>
          </li>
        ))}
      </ul>
      {cards.hasNextPage && (
        <Button
          variant="secondary"
          className="self-center"
          disabled={cards.isFetchingNextPage}
          onClick={() => void cards.fetchNextPage()}
        >
          {t('flashcards.cards.loadMore')}
        </Button>
      )}
    </div>
  );
}
