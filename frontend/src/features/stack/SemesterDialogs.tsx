import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Chip, Dialog, EmptyState, ErrorState, Icon, TextField } from '@/components/ui';
import { errorMessage, fieldErrors } from '@/features/auth/errorMessage';
import type { Semester } from '@/lib/api/stack';
import { useStackStore } from '@/stores/stackStore';
import { formatLocalDate } from './labels';
import { useSaveSemester, useSemesterAction } from './queries';

/** Lista de semestres: tornar atual, editar, arquivar e desarquivar. */
export function SemestersDialog({
  semesters,
  onClose,
}: {
  semesters: Semester[];
  onClose: () => void;
}) {
  const { t, i18n } = useTranslation();
  const openDialog = useStackStore((s) => s.openDialog);
  const action = useSemesterAction();

  return (
    <Dialog
      title={t('stack.semester.manage')}
      onClose={onClose}
      footer={
        <Button onClick={() => openDialog({ kind: 'semesterForm' })}>
          <Icon name="plus" size={18} />
          {t('stack.semester.new')}
        </Button>
      }
    >
      {semesters.length === 0 ? (
        <EmptyState title={t('stack.semester.listEmpty')} />
      ) : (
        <ul className="m-0 flex list-none flex-col gap-2 p-0">
          {semesters.map((s) => (
            <li key={s.id} className="card-inner flex flex-wrap items-center gap-3 px-4 py-3">
              <div className="min-w-0 flex-1">
                <p className="flex flex-wrap items-center gap-2 font-semibold">
                  <span className="truncate">{s.name}</span>
                  {s.current && <Chip tone="action">{t('stack.semester.currentBadge')}</Chip>}
                  {s.archived && <Chip>{t('stack.semester.archived')}</Chip>}
                </p>
                <p className="tabular text-sm text-text-muted">
                  {t('stack.semester.period', {
                    start: formatLocalDate(s.startDate, i18n.language),
                    end: formatLocalDate(s.endDate, i18n.language),
                  })}
                </p>
              </div>
              <div className="flex flex-wrap items-center gap-1">
                {!s.current && !s.archived && (
                  <Button
                    variant="ghost"
                    disabled={action.isPending}
                    onClick={() => action.mutate({ id: s.id, action: 'current' })}
                  >
                    <Icon name="star" size={16} />
                    {t('stack.semester.makeCurrent')}
                  </Button>
                )}
                <Button
                  variant="ghost"
                  iconOnly
                  aria-label={t('stack.semester.editNamed', { name: s.name })}
                  onClick={() => openDialog({ kind: 'semesterForm', semesterId: s.id })}
                >
                  <Icon name="edit" size={18} />
                </Button>
                <Button
                  variant="ghost"
                  disabled={action.isPending}
                  onClick={() =>
                    action.mutate({ id: s.id, action: s.archived ? 'unarchive' : 'archive' })
                  }
                >
                  <Icon name="archive" size={16} />
                  {s.archived ? t('stack.semester.unarchive') : t('stack.semester.archive')}
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
      {action.error && <ErrorState className="mt-4" message={errorMessage(t, action.error)} />}
    </Dialog>
  );
}

/** Criar ou editar semestre (nome e período). */
export function SemesterFormDialog({
  semester,
  onClose,
}: {
  semester?: Semester;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const [name, setName] = useState(semester?.name ?? '');
  const [startDate, setStartDate] = useState(semester?.startDate ?? '');
  const [endDate, setEndDate] = useState(semester?.endDate ?? '');
  const [local, setLocal] = useState<Record<string, string>>({});
  const save = useSaveSemester();
  const errors = { ...fieldErrors(save.error), ...local };

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const problems: Record<string, string> = {};
    const required = t('stack.semester.required');
    if (!name.trim()) problems.name = required;
    if (!startDate) problems.startDate = required;
    if (!endDate) problems.endDate = required;
    // Datas ISO (YYYY-MM-DD) comparam certo como texto.
    if (startDate && endDate && endDate < startDate) {
      problems.endDate = t('stack.semester.endBeforeStart');
    }
    setLocal(problems);
    if (Object.keys(problems).length > 0) return;
    save.mutate(
      { id: semester?.id, input: { name: name.trim(), startDate, endDate } },
      { onSuccess: onClose },
    );
  }

  const formId = 'semester-form';
  return (
    <Dialog
      title={semester ? t('stack.semester.edit') : t('stack.semester.new')}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form={formId} disabled={save.isPending}>
            {t('common.save')}
          </Button>
        </>
      }
    >
      <form id={formId} noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
        <TextField
          label={t('stack.semester.name')}
          placeholder={t('stack.semester.namePlaceholder')}
          value={name}
          maxLength={80}
          required
          data-autofocus
          error={errors.name}
          onChange={(e) => setName(e.target.value)}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            type="date"
            label={t('stack.semester.startDate')}
            value={startDate}
            required
            error={errors.startDate}
            onChange={(e) => setStartDate(e.target.value)}
          />
          <TextField
            type="date"
            label={t('stack.semester.endDate')}
            value={endDate}
            required
            error={errors.endDate ?? errors.periodValid}
            onChange={(e) => setEndDate(e.target.value)}
          />
        </div>
        {save.error && <ErrorState message={errorMessage(t, save.error)} />}
      </form>
    </Dialog>
  );
}
