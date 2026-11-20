import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import {
  Button,
  buttonClass,
  Card,
  Chip,
  EmptyState,
  ErrorState,
  Icon,
  LoadingState,
  PageTitle,
  StatBlock,
  TextField,
} from '@/components/ui';
import { useSubjects } from '@/features/stack/queries';
import type { Deck, FlashcardStats } from '@/lib/api/flashcards';
import { useDebouncedValue } from '@/lib/useDebouncedValue';
import { useFlashcardStore } from '@/stores/flashcardStore';
import { CardList } from './CardList';
import { addDays, lastDays } from './days';
import { FlashcardDialogHost } from './FlashcardDialogs';
import { useDecks, useFlashcardStats } from './queries';

/** Flashcards: decks com novas/para revisar, busca de cartas, estatísticas e criação rápida. */
export function FlashcardsPage() {
  const { t } = useTranslation();
  const decks = useDecks();
  const openDialog = useFlashcardStore((s) => s.openDialog);
  const [search, setSearch] = useState('');
  const q = useDebouncedValue(search.trim(), 250);
  const list = decks.data ?? [];
  const totalToday = list.reduce((sum, d) => sum + d.newToday + d.dueToday, 0);

  return (
    <>
      <PageTitle
        title={t('pages.flashcards.title')}
        description={t('pages.flashcards.description')}
        actions={
          <>
            <Button variant="secondary" onClick={() => openDialog({ kind: 'settings' })}>
              {t('flashcards.settings.open')}
            </Button>
            <Button variant="secondary" onClick={() => openDialog({ kind: 'deck' })}>
              <Icon name="plus" size={18} />
              {t('flashcards.decks.new')}
            </Button>
            <Button onClick={() => openDialog({ kind: 'card' })}>
              <Icon name="plus" size={18} />
              {t('flashcards.card.new')}
            </Button>
          </>
        }
      />

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_340px]">
        <div className="flex min-w-0 flex-col gap-6">
          <div className="flex flex-wrap items-end gap-3">
            <div className="min-w-0 flex-1">
              <TextField
                type="search"
                label={t('flashcards.search.label')}
                placeholder={t('flashcards.search.placeholder')}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
            {totalToday > 0 && (
              <Link to="/flashcards/review" className={buttonClass('primary')}>
                {t('flashcards.review.all', { count: totalToday })}
              </Link>
            )}
          </div>

          {q ? (
            <section aria-label={t('flashcards.search.results')}>
              <CardList q={q} decks={list} emptyTitle={t('flashcards.search.empty')} />
            </section>
          ) : decks.isPending ? (
            <LoadingState />
          ) : decks.isError ? (
            <ErrorState
              message={t('flashcards.decks.loadError')}
              onRetry={() => void decks.refetch()}
            />
          ) : list.length === 0 ? (
            <EmptyState
              title={t('flashcards.decks.emptyTitle')}
              body={t('flashcards.decks.emptyBody')}
              action={
                <Button onClick={() => openDialog({ kind: 'deck' })}>
                  <Icon name="plus" size={18} />
                  {t('flashcards.decks.new')}
                </Button>
              }
            />
          ) : (
            <section aria-label={t('flashcards.decks.title')} className="grid gap-4 md:grid-cols-2">
              {list.map((deck) => (
                <DeckCard key={deck.id} deck={deck} />
              ))}
            </section>
          )}
        </div>
        <aside>
          <StatsCard />
        </aside>
      </div>
      <FlashcardDialogHost />
    </>
  );
}

function DeckCard({ deck }: { deck: Deck }) {
  const { t } = useTranslation();
  const subjects = useSubjects();
  const openDialog = useFlashcardStore((s) => s.openDialog);
  const subject = subjects.data?.find((s) => s.id === deck.subjectId);
  const titleId = `deck-${deck.id}`;
  const today = deck.newToday + deck.dueToday;

  return (
    <Card as="article" aria-labelledby={titleId} className="flex flex-col gap-4 p-5">
      <header className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h2 id={titleId} className="card-title truncate">
            {deck.name}
          </h2>
          {subject ? (
            <Chip dotColor={subject.color} className="mt-2">
              {subject.name}
            </Chip>
          ) : (
            <p className="mt-1 text-sm text-text-muted">{t('flashcards.decks.loose')}</p>
          )}
        </div>
        <Button
          variant="ghost"
          iconOnly
          aria-label={t('flashcards.decks.editNamed', { name: deck.name })}
          onClick={() => openDialog({ kind: 'deck', deckId: deck.id })}
        >
          <Icon name="edit" size={18} />
        </Button>
      </header>
      <dl className="m-0 grid grid-cols-3 gap-3">
        <Count label={t('flashcards.decks.new_')} value={deck.newToday} tone="text-text-on-space" />
        <Count label={t('flashcards.decks.due')} value={deck.dueToday} tone="text-warning" />
        <Count label={t('flashcards.decks.total')} value={deck.totalCards} />
      </dl>
      <div className="flex flex-wrap gap-2">
        {today > 0 ? (
          <Link
            to={`/flashcards/review?deck=${deck.id}`}
            className={buttonClass('primary')}
            aria-label={t('flashcards.review.deckNamed', { name: deck.name, count: today })}
          >
            {t('flashcards.review.start')}
          </Link>
        ) : (
          <span className="flex min-h-11 items-center text-sm text-text-muted">
            {deck.totalCards === 0 ? t('flashcards.decks.noCards') : t('flashcards.decks.done')}
          </span>
        )}
        <Link
          to={`/flashcards/decks/${deck.id}`}
          className={buttonClass('ghost')}
          aria-label={t('flashcards.decks.openNamed', { name: deck.name })}
        >
          {t('flashcards.decks.cards')}
        </Link>
        <Button
          variant="ghost"
          aria-label={t('flashcards.card.newIn', { name: deck.name })}
          onClick={() => openDialog({ kind: 'card', deckId: deck.id })}
        >
          <Icon name="plus" size={18} />
          {t('flashcards.card.short')}
        </Button>
      </div>
    </Card>
  );
}

function Count({ label, value, tone }: { label: string; value: number; tone?: string }) {
  return (
    <div className="flex flex-col gap-1">
      <dt className="label text-text-muted">{label}</dt>
      <dd className={`tabular m-0 text-2xl font-[650] leading-none ${tone ?? ''}`}>{value}</dd>
    </div>
  );
}

function StatsCard() {
  const { t } = useTranslation();
  const stats = useFlashcardStats();
  return (
    <Card title={t('flashcards.stats.title')} aria-label={t('flashcards.stats.title')}>
      {stats.isPending ? (
        <LoadingState />
      ) : stats.isError ? (
        <ErrorState
          message={t('flashcards.stats.loadError')}
          onRetry={() => void stats.refetch()}
        />
      ) : (
        <StatsBody stats={stats.data} />
      )}
    </Card>
  );
}

function StatsBody({ stats }: { stats: FlashcardStats }) {
  const { t, i18n } = useTranslation();
  const days = lastDays(stats.today, 30, stats.reviewsByDay);
  const max = Math.max(1, ...days.map((d) => d.count));
  const weekday = new Intl.DateTimeFormat(i18n.language, { weekday: 'short', timeZone: 'UTC' });
  const forecast = lastDays(addDays(stats.today, 6), 7, stats.forecast);

  return (
    <div className="flex flex-col gap-5">
      <dl className="m-0 grid grid-cols-3 gap-3">
        <StatBlock label={t('flashcards.stage.NEW')} value={stats.newCards} />
        <StatBlock label={t('flashcards.stage.LEARNING')} value={stats.learningCards} />
        <StatBlock label={t('flashcards.stage.MATURE')} value={stats.matureCards} />
      </dl>
      <dl className="m-0">
        <StatBlock
          label={t('flashcards.stats.accuracy')}
          value={stats.accuracyPct === undefined ? '—' : Math.round(stats.accuracyPct)}
          unit={stats.accuracyPct === undefined ? undefined : '%'}
          hint={t('flashcards.stats.accuracyHint', { count: stats.reviews30d })}
        />
      </dl>
      <div>
        <h3 className="label mb-2 text-text-muted">{t('flashcards.stats.perDay')}</h3>
        <div
          role="img"
          aria-label={t('flashcards.stats.perDayLabel', {
            total: stats.reviewsByDay.reduce((s, d) => s + d.count, 0),
          })}
          className="flex h-16 items-end gap-[2px]"
        >
          {days.map((d) => (
            <span
              key={d.date}
              title={`${d.date}: ${d.count}`}
              className="flex-1 rounded-sm bg-action/80"
              style={{
                height: `${d.count === 0 ? 4 : Math.max(8, (d.count / max) * 100)}%`,
                opacity: d.count === 0 ? 0.15 : 1,
              }}
            />
          ))}
        </div>
      </div>
      <div>
        <h3 className="label mb-2 text-text-muted">{t('flashcards.stats.forecast')}</h3>
        <ul className="m-0 grid list-none grid-cols-7 gap-1 p-0 text-center text-xs">
          {forecast.map((d) => (
            <li key={d.date} className="flex flex-col gap-1">
              <span className="text-text-muted">
                {weekday.format(new Date(`${d.date}T00:00:00Z`))}
              </span>
              <span className="tabular text-sm text-text">{d.count}</span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
