import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  Icon,
  LoadingState,
  SelectField,
} from '@/components/ui';
import { errorMessage } from '@/features/auth/errorMessage';
import { SubjectGradeLine } from '@/features/grades/SubjectGradeLine';
import { useGradesOverview } from '@/features/grades/queries';
import type { Semester, Subject } from '@/lib/api/stack';
import { cn } from '@/lib/cn';
import { useStackStore } from '@/stores/stackStore';
import { useSemesterAction } from './queries';

interface SubjectPanelProps {
  semesters: Semester[];
  current: Semester | undefined;
  subjects: Subject[];
  loading: boolean;
  error: unknown;
  onRetry: () => void;
}

/** Lateral da tela: semestre atual e matérias (cor + pendentes), que também filtram a lista. */
export function SubjectPanel({
  semesters,
  current,
  subjects,
  loading,
  error,
  onRetry,
}: SubjectPanelProps) {
  const { t } = useTranslation();
  const subjectFilter = useStackStore((s) => s.filters.subject);
  const setFilters = useStackStore((s) => s.setFilters);
  const openDialog = useStackStore((s) => s.openDialog);
  const action = useSemesterAction();
  const active = semesters.filter((s) => !s.archived);
  // Notas e faltas do semestre atual (mesma consulta da tela do Grade Predictor, em cache).
  const grades = useGradesOverview();
  const gradesById = new Map((grades.data?.subjects ?? []).map((g) => [g.subjectId, g] as const));

  return (
    <Card as="section" aria-labelledby="stack-subjects-title" className="flex flex-col gap-5 p-5">
      {active.length > 0 && (
        <SelectField
          label={t('stack.semester.label')}
          value={current?.id ?? ''}
          disabled={action.isPending}
          onChange={(e) => action.mutate({ id: e.target.value, action: 'current' })}
        >
          {!current && <option value="">{t('stack.semester.none')}</option>}
          {active.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </SelectField>
      )}

      <div>
        <div className="mb-3 flex items-center justify-between gap-2">
          <h2 id="stack-subjects-title" className="label text-text-muted">
            {t('stack.subjects.title')}
          </h2>
          {current && (
            <Button
              variant="ghost"
              iconOnly
              aria-label={t('stack.subjects.new')}
              title={t('stack.subjects.new')}
              onClick={() => openDialog({ kind: 'subject' })}
            >
              <Icon name="plus" size={18} />
            </Button>
          )}
        </div>

        {loading ? (
          <LoadingState />
        ) : error ? (
          <ErrorState message={t('stack.subjects.loadError')} onRetry={onRetry} />
        ) : (
          <ul className="m-0 flex list-none flex-col gap-1 p-0">
            <FilterItem
              label={t('stack.subjects.all')}
              pressed={subjectFilter === 'all'}
              onClick={() => setFilters({ subject: 'all' })}
            />
            {subjects.map((s) => (
              <FilterItem
                key={s.id}
                label={s.name}
                color={s.color}
                count={s.pendingTasks}
                countLabel={t('stack.subjects.pending', { count: s.pendingTasks })}
                pressed={subjectFilter === s.id}
                onClick={() => setFilters({ subject: s.id })}
                onEdit={() => openDialog({ kind: 'subject', subjectId: s.id })}
                editLabel={t('stack.subjects.editNamed', { name: s.name })}
                detail={<SubjectGradeLine grades={gradesById.get(s.id)} />}
              />
            ))}
            <FilterItem
              label={t('stack.subjects.none')}
              pressed={subjectFilter === 'none'}
              onClick={() => setFilters({ subject: 'none' })}
            />
          </ul>
        )}

        {!loading && !error && subjects.length === 0 && (
          <EmptyState
            className="mt-3"
            title={current ? t('stack.subjects.empty') : t('stack.subjects.needSemester')}
            action={
              current ? (
                <Button variant="secondary" onClick={() => openDialog({ kind: 'subject' })}>
                  <Icon name="plus" size={18} />
                  {t('stack.subjects.new')}
                </Button>
              ) : undefined
            }
          />
        )}
      </div>
      {action.error && <ErrorState message={errorMessage(t, action.error)} />}
    </Card>
  );
}

interface FilterItemProps {
  label: string;
  pressed: boolean;
  onClick: () => void;
  color?: string;
  count?: number;
  countLabel?: string;
  onEdit?: () => void;
  editLabel?: string;
  /** Linha extra abaixo do nome (média e faltas). */
  detail?: ReactNode;
}

function FilterItem({
  label,
  pressed,
  onClick,
  color,
  count,
  countLabel,
  onEdit,
  editLabel,
  detail,
}: FilterItemProps) {
  return (
    <li className="group flex items-center gap-1">
      <button
        type="button"
        aria-pressed={pressed}
        onClick={onClick}
        className={cn(
          'flex min-h-11 min-w-0 flex-1 items-center gap-3 rounded-pill px-3 text-left text-sm transition-colors',
          pressed
            ? 'bg-surface-raised font-semibold text-text'
            : 'text-text-secondary hover:bg-surface-raised hover:text-text',
        )}
      >
        {color && (
          <span
            aria-hidden="true"
            className="h-2.5 w-2.5 shrink-0 rounded-full"
            style={{ backgroundColor: color }}
          />
        )}
        <span className="flex min-w-0 flex-1 flex-col py-1">
          <span className="truncate">{label}</span>
          {detail}
        </span>
        {count !== undefined && (
          <span className="tabular text-text-muted">
            <span aria-hidden="true">{count}</span>
            <span className="sr-only">{countLabel}</span>
          </span>
        )}
      </button>
      {onEdit && (
        <Button
          variant="ghost"
          iconOnly
          aria-label={editLabel}
          className="opacity-60 group-focus-within:opacity-100 group-hover:opacity-100"
          onClick={onEdit}
        >
          <Icon name="edit" size={16} />
        </Button>
      )}
    </li>
  );
}
