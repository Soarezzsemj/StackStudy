import { useTranslation } from 'react-i18next';
import { Button, Chip, Icon } from '@/components/ui';
import type { Subject, Task } from '@/lib/api/stack';
import { cn } from '@/lib/cn';
import { useStackStore } from '@/stores/stackStore';
import { formatDue } from './due';
import type { TaskGroup } from './grouping';
import { useSetTaskStatus } from './queries';

interface TaskListProps {
  groups: TaskGroup[];
  subjectsById: Map<string, Subject>;
}

/** Tarefas agrupadas por prazo (Atrasadas, Hoje, Esta semana, Depois, Sem prazo). */
export function TaskList({ groups, subjectsById }: TaskListProps) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col gap-6">
      {groups.map((group) => {
        const headingId = `task-group-${group.bucket}`;
        return (
          <section key={group.bucket} aria-labelledby={headingId}>
            <h3
              id={headingId}
              className={cn(
                'label mb-2 flex items-center gap-2 px-1',
                group.bucket === 'overdue' ? 'text-danger' : 'text-text-muted',
              )}
            >
              {t(`stack.tasks.groups.${group.bucket}`)}
              <span className="tabular text-text-muted">{group.tasks.length}</span>
            </h3>
            <ul className="m-0 flex list-none flex-col gap-2 p-0">
              {group.tasks.map((task) => (
                <TaskRow
                  key={task.id}
                  task={task}
                  subject={task.subjectId ? subjectsById.get(task.subjectId) : undefined}
                  overdue={group.bucket === 'overdue'}
                />
              ))}
            </ul>
          </section>
        );
      })}
    </div>
  );
}

function TaskRow({
  task,
  subject,
  overdue,
}: {
  task: Task;
  subject: Subject | undefined;
  overdue: boolean;
}) {
  const { t, i18n } = useTranslation();
  const selected = useStackStore((s) => s.selectedTaskId === task.id);
  const select = useStackStore((s) => s.select);
  const openDialog = useStackStore((s) => s.openDialog);
  const { mutate: setStatus } = useSetTaskStatus();
  const done = task.status === 'DONE';
  const edit = () => openDialog({ kind: 'task', taskId: task.id });

  return (
    <li
      className={cn(
        'card-inner flex items-center gap-2 py-1 pl-1 pr-2 transition-colors',
        selected && 'border-action/60',
      )}
    >
      <button
        type="button"
        aria-pressed={done}
        aria-label={t(done ? 'stack.tasks.markUndone' : 'stack.tasks.markDone', {
          title: task.title,
        })}
        onClick={() => setStatus({ id: task.id, status: done ? 'TODO' : 'DONE' })}
        className="group flex h-11 w-11 shrink-0 items-center justify-center rounded-full"
      >
        <span
          aria-hidden="true"
          className={cn(
            'flex h-6 w-6 items-center justify-center rounded-full border transition-colors',
            done
              ? 'border-action bg-action text-action-ink'
              : 'border-border-control text-transparent group-hover:border-action group-hover:text-action',
          )}
        >
          <Icon name="check" size={14} />
        </span>
      </button>

      <button
        type="button"
        // Marca para os atalhos (ver TASK_ROW_ATTR em shortcuts.ts).
        data-task-row=""
        data-task-id={task.id}
        aria-current={selected ? 'true' : undefined}
        onFocus={() => select(task.id)}
        onClick={() => select(task.id)}
        onDoubleClick={edit}
        className="min-h-11 min-w-0 flex-1 rounded-inner py-1.5 text-left"
      >
        <span
          className={cn(
            'block truncate font-semibold',
            done ? 'text-text-muted line-through' : 'text-text',
          )}
        >
          {task.title}
        </span>
        <span className="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-text-secondary">
          {subject && (
            <span className="inline-flex min-w-0 items-center gap-1.5">
              <span
                aria-hidden="true"
                className="h-2 w-2 shrink-0 rounded-full"
                style={{ backgroundColor: subject.color }}
              />
              <span className="truncate">{subject.name}</span>
            </span>
          )}
          <span>{t(`stack.tasks.type.${task.type}`)}</span>
          {task.dueAt && (
            <span className={cn('tabular', overdue && 'text-danger')}>
              {formatDue(task.dueAt, task.dueAllDay, i18n.language)}
            </span>
          )}
          {task.status === 'DOING' && (
            <span className="text-warning">{t('stack.tasks.status.DOING')}</span>
          )}
        </span>
      </button>

      {task.priority !== 'MEDIUM' && (
        <Chip
          tone={task.priority === 'HIGH' ? 'danger' : 'neutral'}
          className="hidden sm:inline-flex"
          title={t('stack.tasks.priorityLabel', {
            value: t(`stack.tasks.priority.${task.priority}`),
          })}
        >
          {t(`stack.tasks.priority.${task.priority}`)}
        </Chip>
      )}
      <Button
        variant="ghost"
        iconOnly
        aria-label={t('stack.tasks.editNamed', { title: task.title })}
        onClick={edit}
      >
        <Icon name="edit" size={18} />
      </Button>
    </li>
  );
}
