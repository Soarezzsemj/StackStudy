import { useId, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, ErrorState, Icon, SelectField } from '@/components/ui';
import { errorMessage } from '@/features/auth/errorMessage';
import { TASK_STATUSES, TASK_TYPES } from '@/lib/api/stack';
import { useStackStore } from '@/stores/stackStore';
import { toDue } from './due';
import type { StatusFilter, TaskFilters } from './filters';
import { useSaveTask } from './queries';

/** Busca e filtros de tipo e status (a matéria filtra pelo painel lateral). */
export function TaskFilterBar() {
  const { t } = useTranslation();
  const filters = useStackStore((s) => s.filters);
  const setFilters = useStackStore((s) => s.setFilters);
  const searchId = useId();

  return (
    <div role="search" aria-label={t('stack.filters.label')} className="flex flex-col gap-3">
      <div className="relative">
        <label htmlFor={searchId} className="sr-only">
          {t('stack.filters.search')}
        </label>
        <Icon
          name="search"
          size={18}
          className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-text-muted"
        />
        <input
          id={searchId}
          type="search"
          className="input pl-11"
          placeholder={t('stack.filters.searchPlaceholder')}
          value={filters.q}
          maxLength={100}
          onChange={(e) => setFilters({ q: e.target.value })}
        />
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <SelectField
          label={t('stack.filters.type')}
          value={filters.type}
          onChange={(e) => setFilters({ type: e.target.value as TaskFilters['type'] })}
        >
          <option value="all">{t('stack.filters.allTypes')}</option>
          {TASK_TYPES.map((v) => (
            <option key={v} value={v}>
              {t(`stack.tasks.type.${v}`)}
            </option>
          ))}
        </SelectField>
        <SelectField
          label={t('stack.filters.status')}
          value={filters.status}
          onChange={(e) => setFilters({ status: e.target.value as StatusFilter })}
        >
          <option value="open">{t('stack.filters.open')}</option>
          <option value="all">{t('stack.filters.all')}</option>
          {TASK_STATUSES.map((v) => (
            <option key={v} value={v}>
              {t(`stack.tasks.status.${v}`)}
            </option>
          ))}
        </SelectField>
      </div>
    </div>
  );
}

/** Criação rápida: título (+ data opcional) e Enter. Usa a matéria do filtro ativo. */
export function QuickAdd() {
  const { t } = useTranslation();
  const subjectFilter = useStackStore((s) => s.filters.subject);
  const [title, setTitle] = useState('');
  const [date, setDate] = useState('');
  const save = useSaveTask();
  const inputId = useId();
  const dateId = useId();

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const value = title.trim();
    if (!value) return;
    const due = toDue(date, '');
    const subjectId = subjectFilter === 'all' || subjectFilter === 'none' ? null : subjectFilter;
    save.mutate(
      { input: { title: value, subjectId, dueAt: due.dueAt, dueAllDay: due.dueAllDay } },
      {
        onSuccess: () => {
          setTitle('');
          setDate('');
        },
      },
    );
  }

  return (
    <div className="flex flex-col gap-2">
      <form onSubmit={onSubmit} className="card-inner flex flex-wrap items-center gap-2 p-2">
        <label htmlFor={inputId} className="sr-only">
          {t('stack.tasks.quickAdd')}
        </label>
        <input
          id={inputId}
          className="input min-w-[12rem] flex-1 border-transparent bg-transparent"
          placeholder={t('stack.tasks.quickAddPlaceholder')}
          value={title}
          maxLength={200}
          onChange={(e) => setTitle(e.target.value)}
        />
        <label htmlFor={dateId} className="sr-only">
          {t('stack.tasks.quickAddDue')}
        </label>
        <input
          id={dateId}
          type="date"
          className="input w-auto"
          value={date}
          onChange={(e) => setDate(e.target.value)}
        />
        <Button type="submit" disabled={!title.trim() || save.isPending}>
          <Icon name="plus" size={18} />
          {t('stack.tasks.add')}
        </Button>
      </form>
      {save.error && <ErrorState message={errorMessage(t, save.error)} />}
    </div>
  );
}
