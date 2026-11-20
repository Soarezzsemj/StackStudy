import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router-dom';
import {
  Button,
  buttonClass,
  EmptyState,
  ErrorState,
  Icon,
  LoadingState,
  PageTitle,
  TextField,
} from '@/components/ui';
import { errorMessage } from '@/features/auth/errorMessage';
import { flashcardsApi } from '@/lib/api/flashcards';
import { useDebouncedValue } from '@/lib/useDebouncedValue';
import { useFlashcardStore } from '@/stores/flashcardStore';
import { CardList } from './CardList';
import { downloadText } from './csv';
import { FlashcardDialogHost } from './FlashcardDialogs';
import { useDecks } from './queries';

/** Cartas de um deck: busca, criação, edição, importar e exportar CSV. */
export function DeckPage() {
  const { t } = useTranslation();
  const { deckId = '' } = useParams();
  const decks = useDecks();
  const openDialog = useFlashcardStore((s) => s.openDialog);
  const [search, setSearch] = useState('');
  const q = useDebouncedValue(search.trim(), 250);
  const [exportError, setExportError] = useState<unknown>(null);
  const [exporting, setExporting] = useState(false);
  const deck = decks.data?.find((d) => d.id === deckId);

  async function exportCsv() {
    setExportError(null);
    setExporting(true);
    try {
      const file = await flashcardsApi.exportCsv(deckId);
      downloadText(file.fileName, file.content);
    } catch (err) {
      setExportError(err);
    } finally {
      setExporting(false);
    }
  }

  if (decks.isPending) return <LoadingState />;
  if (decks.isError) {
    return (
      <ErrorState message={t('flashcards.decks.loadError')} onRetry={() => void decks.refetch()} />
    );
  }
  if (!deck) {
    return (
      <EmptyState
        title={t('flashcards.decks.notFound')}
        action={
          <Link to="/flashcards" className={buttonClass('secondary')}>
            {t('flashcards.decks.back')}
          </Link>
        }
      />
    );
  }

  const today = deck.newToday + deck.dueToday;
  return (
    <>
      <PageTitle
        eyebrow={
          <Link to="/flashcards" className="hover:text-text">
            ← {t('flashcards.decks.back')}
          </Link>
        }
        title={deck.name}
        description={t('flashcards.decks.summary', {
          total: deck.totalCards,
          newCount: deck.newToday,
          due: deck.dueToday,
        })}
        actions={
          <>
            <Button variant="secondary" onClick={() => openDialog({ kind: 'import', deckId })}>
              {t('flashcards.import.open')}
            </Button>
            <Button variant="secondary" disabled={exporting} onClick={() => void exportCsv()}>
              {t('flashcards.export.open')}
            </Button>
            <Button variant="ghost" onClick={() => openDialog({ kind: 'deck', deckId })}>
              {t('flashcards.decks.edit')}
            </Button>
            <Button
              variant="ghost"
              className="text-danger"
              onClick={() => openDialog({ kind: 'deleteDeck', deckId })}
            >
              {t('common.delete')}
            </Button>
            {today > 0 && (
              <Link to={`/flashcards/review?deck=${deckId}`} className={buttonClass('primary')}>
                {t('flashcards.review.start')}
              </Link>
            )}
          </>
        }
      />
      {exportError !== null && (
        <ErrorState className="mb-4" message={errorMessage(t, exportError)} />
      )}
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div className="min-w-0 flex-1">
          <TextField
            type="search"
            label={t('flashcards.search.inDeck')}
            placeholder={t('flashcards.search.placeholder')}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <Button onClick={() => openDialog({ kind: 'card', deckId })}>
          <Icon name="plus" size={18} />
          {t('flashcards.card.new')}
        </Button>
      </div>
      <CardList
        deckId={deckId}
        q={q}
        decks={decks.data}
        emptyTitle={q ? t('flashcards.search.empty') : t('flashcards.cards.empty')}
      />
      <FlashcardDialogHost />
    </>
  );
}
