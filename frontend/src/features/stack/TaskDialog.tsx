import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Dialog, ErrorState, SelectField, TextAreaField, TextField } from '@/components/ui';
import { errorMessage, fieldErrors } from '@/features/auth/errorMessage';
import {
  TASK_PRIORITIES,
  TASK_STATUSES,
  TASK_TYPES,
  type Subject,
  type Task,
  type TaskPriority,
  type TaskStatus,
  type TaskType,
} from '@/lib/api/stack';
import { fromDue, toDue } from './due';
import { useDeleteTask, useSaveTask } from './queries';

interface TaskDialogProps {
  /** Tarefa a editar; sem ela, cria uma nova. */
  task?: Task;
  /** Matérias disponíveis no seletor. */
  subjects: Subject[];
  /** Matéria pré-selecionada ao criar (ex.: filtro ativo). */
  defaultSubjectId?: string;
  /** Título sugerido ao criar. */
  defaultTitle?: string;
  onClose: () => void;
}

export function TaskDialog({
  task,
  subjects,
  defaultSubjectId,
  defaultTitle,
  onClose,
}: TaskDialogProps) {
  const { t } = useTranslation();
  const initialDue = fromDue(task?.dueAt, task?.dueAllDay ?? false);
  const [title, setTitle] = useState(task?.title ?? defaultTitle ?? '');
  const [description, setDescription] = useState(task?.description ?? '');
  const [subjectId, setSubjectId] = useState(task?.subjectId ?? defaultSubjectId ?? '');
  const [type, setType] = useState<TaskType>(task?.type ?? 'OTHER');
  const [priority, setPriority] = useState<TaskPriority>(task?.priority ?? 'MEDIUM');
  const [status, setStatus] = useState<TaskStatus>(task?.status ?? 'TODO');
  const [dueDate, setDueDate] = useState(initialDue.date);
  const [dueTime, setDueTime] = useState(initialDue.time);
  const [titleError, setTitleError] = useState<string>();
  const [confirmDelete, setConfirmDelete] = useState(false);
  const save = useSaveTask();
  const remove = useDeleteTask();
  const errors = fieldErrors(save.error);

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!title.trim()) {
      setTitleError(t('stack.semester.required'));
      return;
    }
    setTitleError(undefined);
    const due = toDue(dueDate, dueTime);
    save.mutate(
      {
        id: task?.id,
        input: {
          title: title.trim(),
          description: description.trim() || null,
          subjectId: subjectId || null,
          type,
          priority,
          status,
          dueAt: due.dueAt,
          dueAllDay: due.dueAllDay,
        },
      },
      { onSuccess: onClose },
    );
  }

  function onDelete() {
    if (!task) return;
    if (!confirmDelete) {
      setConfirmDelete(true);
      return;
    }
    remove.mutate(task.id, { onSuccess: onClose });
  }

  const formId = 'task-form';
  return (
    <Dialog
      title={task ? t('stack.tasks.edit') : t('stack.tasks.new')}
      onClose={onClose}
      footer={
        <>
          {task && (
            <Button
              variant={confirmDelete ? 'primary' : 'ghost'}
              className="mr-auto"
              onClick={onDelete}
              disabled={remove.isPending}
              aria-describedby={confirmDelete ? 'task-delete-warning' : undefined}
            >
              {confirmDelete ? t('common.confirmDelete') : t('common.delete')}
            </Button>
          )}
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
          label={t('stack.tasks.fields.title')}
          placeholder={t('stack.tasks.fields.titlePlaceholder')}
          value={title}
          maxLength={200}
          required
          data-autofocus
          error={titleError ?? errors.title}
          onChange={(e) => setTitle(e.target.value)}
        />
        <TextAreaField
          label={t('stack.tasks.fields.description')}
          value={description}
          maxLength={5000}
          rows={3}
          error={errors.description}
          onChange={(e) => setDescription(e.target.value)}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <SelectField
            label={t('stack.tasks.fields.subject')}
            value={subjectId}
            onChange={(e) => setSubjectId(e.target.value)}
          >
            <option value="">{t('stack.tasks.noSubject')}</option>
            {subjects.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </SelectField>
          <SelectField
            label={t('stack.tasks.fields.type')}
            value={type}
            onChange={(e) => setType(e.target.value as TaskType)}
          >
            {TASK_TYPES.map((v) => (
              <option key={v} value={v}>
                {t(`stack.tasks.type.${v}`)}
              </option>
            ))}
          </SelectField>
          <SelectField
            label={t('stack.tasks.fields.priority')}
            value={priority}
            onChange={(e) => setPriority(e.target.value as TaskPriority)}
          >
            {TASK_PRIORITIES.map((v) => (
              <option key={v} value={v}>
                {t(`stack.tasks.priority.${v}`)}
              </option>
            ))}
          </SelectField>
          <SelectField
            label={t('stack.tasks.fields.status')}
            value={status}
            onChange={(e) => setStatus(e.target.value as TaskStatus)}
          >
            {TASK_STATUSES.map((v) => (
              <option key={v} value={v}>
                {t(`stack.tasks.status.${v}`)}
              </option>
            ))}
          </SelectField>
          <TextField
            type="date"
            label={t('stack.tasks.fields.dueDate')}
            value={dueDate}
            error={errors.dueAt}
            onChange={(e) => setDueDate(e.target.value)}
          />
          <TextField
            type="time"
            label={t('stack.tasks.fields.dueTime')}
            hint={t('stack.tasks.fields.dueTimeHint')}
            value={dueTime}
            disabled={!dueDate}
            onChange={(e) => setDueTime(e.target.value)}
          />
        </div>
        {confirmDelete && (
          <p id="task-delete-warning" className="text-sm text-warning">
            {t('stack.tasks.deleteConfirm')}
          </p>
        )}
        {(save.error || remove.error) && (
          <ErrorState message={errorMessage(t, save.error ?? remove.error)} />
        )}
      </form>
    </Dialog>
  );
}
