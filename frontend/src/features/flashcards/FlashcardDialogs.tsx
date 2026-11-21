import { useState, type ChangeEvent, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Dialog,
  EmptyState,
  ErrorState,
  SelectField,
  TextAreaField,
  TextField,
} from '@/components/ui';
import { errorMessage, fieldErrors } from '@/features/auth/errorMessage';
import { useSubjects } from '@/features/stack/queries';
import type { Card, Deck, ImportResult } from '@/lib/api/flashcards';
import { useFlashcardStore } from '@/stores/flashcardStore';
import { csvTooLarge, parseTags } from './csv';
import { Markdown } from './Markdown';
import {
  useDecks,
  useDeleteCard,
  useDeleteDeck,
  useFlashcardSettings,
  useImportCsv,
  useSaveCard,
  useSaveDeck,
  useSaveFlashcardSettings,
} from './queries';

/** Mostra o diálogo pedido no store (tela de Flashcards, deck ou paleta Ctrl K). */
export function FlashcardDialogHost() {
  const dialog = useFlashcardStore((s) => s.dialog);
  const close = useFlashcardStore((s) => s.closeDialog);
  const decks = useDecks();
  if (!dialog) return null;
  const list = decks.data ?? [];
  switch (dialog.kind) {
    case 'card':
      return (
        <CardDialog
          card={dialog.card}
          deckId={dialog.deckId}
          defaultTags={dialog.tags}
          decks={list}
          onClose={close}
        />
      );
    case 'deck':
      return <DeckDialog deck={list.find((d) => d.id === dialog.deckId)} onClose={close} />;
    case 'settings':
      return <LimitsDialog onClose={close} />;
    case 'import': {
      const deck = list.find((d) => d.id === dialog.deckId);
      return deck ? <ImportDialog deck={deck} onClose={close} /> : null;
    }
    case 'deleteDeck': {
      const deck = list.find((d) => d.id === dialog.deckId);
      return deck ? <DeleteDeckDialog deck={deck} onClose={close} /> : null;
    }
  }
}

// ---------------------------------------------------------------------------
// carta
// ---------------------------------------------------------------------------

function CardDialog({
  card,
  deckId,
  defaultTags,
  decks,
  onClose,
}: {
  card?: Card;
  deckId?: string;
  defaultTags?: string[];
  decks: Deck[];
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const lastDeckId = useFlashcardStore((s) => s.lastDeckId);
  const setLastDeckId = useFlashcardStore((s) => s.setLastDeckId);
  const openDialog = useFlashcardStore((s) => s.openDialog);
  const initialDeck =
    card?.deckId ??
    deckId ??
    (lastDeckId && decks.some((d) => d.id === lastDeckId) ? lastDeckId : undefined) ??
    decks[0]?.id ??
    '';
  const [deck, setDeck] = useState(initialDeck);
  const [front, setFront] = useState(card?.front ?? '');
  const [back, setBack] = useState(card?.back ?? '');
  const [tags, setTags] = useState(card?.tags.join(' ') ?? defaultTags?.join(' ') ?? '');
  const [preview, setPreview] = useState(false);
  const [savedCount, setSavedCount] = useState(0);
  const save = useSaveCard();
  const remove = useDeleteCard();
  const errors = fieldErrors(save.error);

  if (decks.length === 0) {
    return (
      <Dialog title={t('flashcards.card.new')} onClose={onClose}>
        <EmptyState
          title={t('flashcards.decks.emptyTitle')}
          body={t('flashcards.card.needDeck')}
          action={
            <Button onClick={() => openDialog({ kind: 'deck' })}>
              {t('flashcards.decks.new')}
            </Button>
          }
        />
      </Dialog>
    );
  }

  function submit(another: boolean) {
    save.mutate(
      { id: card?.id, input: { deckId: deck, front, back, tags: parseTags(tags) } },
      {
        onSuccess: () => {
          setLastDeckId(deck);
          if (another) {
            // Criação em sequência: limpa os textos e mantém deck e tags.
            setFront('');
            setBack('');
            setSavedCount((n) => n + 1);
            document.getElementById('card-front')?.focus();
          } else {
            onClose();
          }
        },
      },
    );
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    submit(false);
  }

  return (
    <Dialog
      title={card ? t('flashcards.card.edit') : t('flashcards.card.new')}
      onClose={onClose}
      className="max-w-2xl"
      footer={
        <>
          {card && (
            <Button
              variant="ghost"
              className="mr-auto text-danger"
              disabled={remove.isPending}
              onClick={() => remove.mutate(card.id, { onSuccess: onClose })}
            >
              {t('common.delete')}
            </Button>
          )}
          <Button variant="secondary" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          {!card && (
            <Button variant="secondary" disabled={save.isPending} onClick={() => submit(true)}>
              {t('flashcards.card.saveAndNew')}
            </Button>
          )}
          <Button type="submit" form="card-form" disabled={save.isPending}>
            {t('common.save')}
          </Button>
        </>
      }
    >
      <form id="card-form" noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
        <SelectField
          label={t('flashcards.card.deck')}
          value={deck}
          error={errors.deckId}
          onChange={(e) => setDeck(e.target.value)}
        >
          {decks.map((d) => (
            <option key={d.id} value={d.id}>
              {d.name}
            </option>
          ))}
        </SelectField>
        <TextAreaField
          id="card-front"
          label={t('flashcards.card.front')}
          value={front}
          rows={3}
          maxLength={5000}
          data-autofocus
          error={errors.front}
          onChange={(e) => setFront(e.target.value)}
        />
        <TextAreaField
          label={t('flashcards.card.back')}
          value={back}
          rows={4}
          maxLength={5000}
          error={errors.back}
          hint={t('flashcards.card.markdownHint')}
          onChange={(e) => setBack(e.target.value)}
        />
        <TextField
          label={t('flashcards.card.tags')}
          value={tags}
          hint={t('flashcards.card.tagsHint')}
          error={Object.entries(errors).find(([k]) => k.startsWith('tags'))?.[1]}
          onChange={(e) => setTags(e.target.value)}
        />
        <div>
          <Button variant="ghost" aria-expanded={preview} onClick={() => setPreview((p) => !p)}>
            {preview ? t('flashcards.card.hidePreview') : t('flashcards.card.showPreview')}
          </Button>
          {preview && (
            <div className="card-inner mt-2 grid gap-4 p-4 sm:grid-cols-2">
              <div>
                <p className="label mb-2 text-text-muted">{t('flashcards.card.front')}</p>
                <Markdown source={front || '—'} />
              </div>
              <div>
                <p className="label mb-2 text-text-muted">{t('flashcards.card.back')}</p>
                <Markdown source={back || '—'} />
              </div>
            </div>
          )}
        </div>
        {savedCount > 0 && (
          <p role="status" className="text-sm text-action">
            {t('flashcards.card.saved', { count: savedCount })}
          </p>
        )}
        {(save.error || remove.error) && (
          <ErrorState message={errorMessage(t, save.error ?? remove.error)} />
        )}
      </form>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// deck
// ---------------------------------------------------------------------------

function DeckDialog({ deck, onClose }: { deck?: Deck; onClose: () => void }) {
  const { t } = useTranslation();
  const subjects = useSubjects();
  const [name, setName] = useState(deck?.name ?? '');
  const [subjectId, setSubjectId] = useState(deck?.subjectId ?? '');
  const [newPerDay, setNewPerDay] = useState(deck?.newPerDay?.toString() ?? '');
  const [reviewsPerDay, setReviewsPerDay] = useState(deck?.reviewsPerDay?.toString() ?? '');
  const save = useSaveDeck();
  const errors = fieldErrors(save.error);
  const toLimit = (v: string) => (v.trim() === '' ? null : Number(v));

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    save.mutate(
      {
        id: deck?.id,
        input: {
          name,
          subjectId: subjectId || null,
          newPerDay: toLimit(newPerDay),
          reviewsPerDay: toLimit(reviewsPerDay),
        },
      },
      { onSuccess: onClose },
    );
  }

  return (
    <Dialog
      title={deck ? t('flashcards.decks.edit') : t('flashcards.decks.new')}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="deck-form" disabled={save.isPending}>
            {t('common.save')}
          </Button>
        </>
      }
    >
      <form id="deck-form" noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
        <TextField
          label={t('flashcards.decks.name')}
          value={name}
          maxLength={120}
          placeholder={t('flashcards.decks.namePlaceholder')}
          error={errors.name}
          data-autofocus
          onChange={(e) => setName(e.target.value)}
        />
        <SelectField
          label={t('flashcards.decks.subject')}
          value={subjectId}
          error={errors.subjectId}
          onChange={(e) => setSubjectId(e.target.value)}
        >
          <option value="">{t('flashcards.decks.noSubject')}</option>
          {(subjects.data ?? [])
            .filter((s) => s.status !== 'DROPPED' || s.id === subjectId)
            .map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
        </SelectField>
        <fieldset className="grid gap-4 sm:grid-cols-2">
          <legend className="label mb-2 text-text-secondary">{t('flashcards.decks.limits')}</legend>
          <TextField
            type="number"
            inputMode="numeric"
            min={0}
            max={1000}
            label={t('flashcards.settings.newPerDay')}
            placeholder={t('flashcards.decks.limitDefault')}
            value={newPerDay}
            error={errors.newPerDay}
            onChange={(e) => setNewPerDay(e.target.value)}
          />
          <TextField
            type="number"
            inputMode="numeric"
            min={0}
            max={1000}
            label={t('flashcards.settings.reviewsPerDay')}
            placeholder={t('flashcards.decks.limitDefault')}
            value={reviewsPerDay}
            error={errors.reviewsPerDay}
            onChange={(e) => setReviewsPerDay(e.target.value)}
          />
        </fieldset>
        <p className="text-sm text-text-muted">{t('flashcards.decks.limitsHint')}</p>
        {save.error && <ErrorState message={errorMessage(t, save.error)} />}
      </form>
    </Dialog>
  );
}

function DeleteDeckDialog({ deck, onClose }: { deck: Deck; onClose: () => void }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const remove = useDeleteDeck();
  return (
    <Dialog
      title={t('flashcards.decks.deleteTitle', { name: deck.name })}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button
            disabled={remove.isPending}
            className="bg-danger text-action-ink"
            onClick={() =>
              remove.mutate(deck.id, {
                onSuccess: () => {
                  onClose();
                  navigate('/flashcards');
                },
              })
            }
          >
            {t('common.confirmDelete')}
          </Button>
        </>
      }
    >
      <p className="text-text-secondary">
        {t('flashcards.decks.deleteWarning', { count: deck.totalCards })}
      </p>
      {remove.error && <ErrorState className="mt-4" message={errorMessage(t, remove.error)} />}
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// limites diários
// ---------------------------------------------------------------------------

function LimitsDialog({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation();
  const current = useFlashcardSettings();
  const [form, setForm] = useState<{ newPerDay: string; reviewsPerDay: string } | null>(null);
  const save = useSaveFlashcardSettings();
  const errors = fieldErrors(save.error);
  const values = form ?? {
    newPerDay: String(current.data?.newPerDay ?? 20),
    reviewsPerDay: String(current.data?.reviewsPerDay ?? 100),
  };

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    save.mutate(
      { newPerDay: Number(values.newPerDay), reviewsPerDay: Number(values.reviewsPerDay) },
      { onSuccess: onClose },
    );
  }

  return (
    <Dialog
      title={t('flashcards.settings.title')}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="limits-form" disabled={save.isPending}>
            {t('common.save')}
          </Button>
        </>
      }
    >
      <form id="limits-form" noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
        <p className="text-sm text-text-secondary">{t('flashcards.settings.hint')}</p>
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            type="number"
            inputMode="numeric"
            min={0}
            max={1000}
            label={t('flashcards.settings.newPerDay')}
            value={values.newPerDay}
            error={errors.newPerDay}
            data-autofocus
            onChange={(e) => setForm({ ...values, newPerDay: e.target.value })}
          />
          <TextField
            type="number"
            inputMode="numeric"
            min={0}
            max={1000}
            label={t('flashcards.settings.reviewsPerDay')}
            value={values.reviewsPerDay}
            error={errors.reviewsPerDay}
            onChange={(e) => setForm({ ...values, reviewsPerDay: e.target.value })}
          />
        </div>
        {save.error && <ErrorState message={errorMessage(t, save.error)} />}
      </form>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// importar CSV
// ---------------------------------------------------------------------------

function ImportDialog({ deck, onClose }: { deck: Deck; onClose: () => void }) {
  const { t } = useTranslation();
  const [text, setText] = useState('');
  const [fileError, setFileError] = useState<string | null>(null);
  const [report, setReport] = useState<ImportResult | null>(null);
  const importCsv = useImportCsv();
  const tooLarge = csvTooLarge(text);

  function onFile(e: ChangeEvent<HTMLInputElement>) {
    setFileError(null);
    const file = e.target.files?.[0];
    if (!file) return;
    // Bytes ≥ caracteres: se o arquivo já passa do limite em bytes com folga, nem lê.
    if (file.size > 4 * 1_000_000) {
      setFileError(t('flashcards.import.tooLarge'));
      return;
    }
    void file.text().then(setText);
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (tooLarge || !text.trim()) return;
    importCsv.mutate({ deckId: deck.id, csv: text }, { onSuccess: setReport });
  }

  return (
    <Dialog
      title={t('flashcards.import.title', { name: deck.name })}
      onClose={onClose}
      className="max-w-2xl"
      footer={
        report ? (
          <Button onClick={onClose}>{t('common.close')}</Button>
        ) : (
          <>
            <Button variant="secondary" onClick={onClose}>
              {t('common.cancel')}
            </Button>
            <Button
              type="submit"
              form="import-form"
              disabled={importCsv.isPending || tooLarge || !text.trim()}
            >
              {t('flashcards.import.submit')}
            </Button>
          </>
        )
      }
    >
      {report ? (
        <div className="flex flex-col gap-4" role="status">
          <p className="text-lg font-semibold text-action">
            {t('flashcards.import.imported', { count: report.imported })}
          </p>
          {report.errors.length > 0 && (
            <div>
              <p className="mb-2 text-warning">
                {t('flashcards.import.errors', { count: report.errors.length })}
              </p>
              <ul className="m-0 flex max-h-64 list-none flex-col gap-1 overflow-y-auto p-0 text-sm">
                {report.errors.map((err) => (
                  <li key={`${err.line}-${err.code}`} className="text-text-secondary">
                    <span className="tabular text-text">
                      {t('flashcards.import.line', { line: err.line })}
                    </span>
                    {' · '}
                    <span>
                      {t(`flashcards.import.codes.${err.code}`, {
                        defaultValue: t('flashcards.import.codes.UNKNOWN'),
                      })}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      ) : (
        <form id="import-form" noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
          <p className="text-sm text-text-secondary">{t('flashcards.import.hint')}</p>
          <div className="flex flex-col gap-2">
            <label htmlFor="csv-file" className="label text-text-secondary">
              {t('flashcards.import.file')}
            </label>
            <input
              id="csv-file"
              type="file"
              accept=".csv,text/csv,text/plain"
              className="text-sm text-text-secondary file:mr-3 file:min-h-11 file:rounded-pill file:border file:border-border-control file:bg-surface-raised file:px-4 file:text-text"
              onChange={onFile}
            />
          </div>
          <TextAreaField
            label={t('flashcards.import.paste')}
            value={text}
            rows={6}
            placeholder={'front,back,tags\nPergunta,Resposta,tag1 tag2'}
            error={
              tooLarge || fileError ? (fileError ?? t('flashcards.import.tooLarge')) : undefined
            }
            onChange={(e) => setText(e.target.value)}
          />
          {importCsv.error && <ErrorState message={errorMessage(t, importCsv.error)} />}
        </form>
      )}
    </Dialog>
  );
}
