import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, EmptyState, ErrorState, Icon, LoadingState, PageTitle } from '@/components/ui';
import { SUBJECT_COLORS, type Semester, type Subject, type Task } from '@/lib/api/stack';
import { useDebouncedValue } from '@/lib/useDebouncedValue';
import { useStackStore } from '@/stores/stackStore';
import { hasActiveFilters } from './filters';
import { groupTasksByDue } from './grouping';
import { useCurrentSubjects, useTasks } from './queries';
import { SemesterFormDialog, SemestersDialog } from './SemesterDialogs';
import { SubjectDialog } from './SubjectDialog';
import { SubjectPanel } from './SubjectPanel';
import { TaskDialog } from './TaskDialog';
import { TaskList } from './TaskList';
import { QuickAdd, TaskFilterBar } from './TaskToolbar';
import { useStackShortcuts } from './useStackShortcuts';

/** The Stack: semestre atual, matérias e tarefas agrupadas por prazo. */
export function StackPage() {
  const { t } = useTranslation();
  const filters = useStackStore((s) => s.filters);
  const resetFilters = useStackStore((s) => s.resetFilters);
  const openDialog = useStackStore((s) => s.openDialog);
  const { semesters, subjects, current, list: currentSubjects } = useCurrentSubjects();

  // A busca só vai para a API depois de uma pausa na digitação.
  const q = useDebouncedValue(filters.q, 250);
  const queryFilters = useMemo(() => ({ ...filters, q }), [filters, q]);
  const tasksQuery = useTasks(queryFilters);

  const tasks = useMemo(
    () => tasksQuery.data?.pages.flatMap((p) => p.items) ?? [],
    [tasksQuery.data],
  );
  const groups = useMemo(() => groupTasksByDue(tasks, new Date()), [tasks]);
  const visible = useMemo(() => groups.flatMap((g) => g.tasks), [groups]);
  const subjectsById = useMemo(
    () => new Map((subjects.data ?? []).map((s) => [s.id, s] as const)),
    [subjects.data],
  );
  useStackShortcuts(visible);

  const semesterList = semesters.data ?? [];
  const noSemester = semesters.isSuccess && semesterList.length === 0;

  return (
    <>
      <PageTitle
        title={t('pages.stack.title')}
        description={t('pages.stack.description')}
        actions={
          <>
            <Button variant="secondary" onClick={() => openDialog({ kind: 'semesters' })}>
              {t('stack.semester.manage')}
            </Button>
            <Button onClick={() => openDialog({ kind: 'task' })} aria-keyshortcuts="N">
              <Icon name="plus" size={18} />
              {t('stack.tasks.new')}
            </Button>
          </>
        }
      />

      {semesters.isError && (
        <ErrorState
          className="mb-6"
          message={t('errors.generic')}
          onRetry={() => void semesters.refetch()}
        />
      )}
      {noSemester && (
        <EmptyState
          className="mb-6"
          title={t('stack.semester.emptyTitle')}
          body={t('stack.semester.emptyBody')}
          action={
            <Button onClick={() => openDialog({ kind: 'semesterForm' })}>
              <Icon name="plus" size={18} />
              {t('stack.semester.new')}
            </Button>
          }
        />
      )}

      <div className="grid items-start gap-6 lg:grid-cols-[272px_minmax(0,1fr)]">
        <SubjectPanel
          semesters={semesterList}
          current={current}
          subjects={currentSubjects}
          loading={semesters.isPending || subjects.isPending}
          error={subjects.error}
          onRetry={() => void subjects.refetch()}
        />

        <section aria-labelledby="stack-tasks-title" className="flex min-w-0 flex-col gap-5">
          <h2 id="stack-tasks-title" className="sr-only">
            {t('stack.tasks.list')}
          </h2>
          <TaskFilterBar />
          <QuickAdd />

          {tasksQuery.isPending ? (
            <LoadingState />
          ) : tasksQuery.isError ? (
            <ErrorState
              message={t('stack.tasks.loadError')}
              onRetry={() => void tasksQuery.refetch()}
            />
          ) : tasks.length === 0 ? (
            hasActiveFilters(filters) ? (
              <EmptyState
                title={t('stack.tasks.emptyFiltered')}
                action={
                  <Button variant="secondary" onClick={resetFilters}>
                    {t('stack.tasks.clearFilters')}
                  </Button>
                }
              />
            ) : (
              <EmptyState title={t('stack.tasks.emptyTitle')} body={t('stack.tasks.emptyBody')} />
            )
          ) : (
            <>
              <TaskList groups={groups} subjectsById={subjectsById} />
              {tasksQuery.hasNextPage && (
                <Button
                  variant="secondary"
                  className="self-center"
                  disabled={tasksQuery.isFetchingNextPage}
                  onClick={() => void tasksQuery.fetchNextPage()}
                >
                  {t('stack.tasks.loadMore')}
                </Button>
              )}
            </>
          )}

          <p className="text-xs text-text-muted">
            <span className="sr-only">{t('stack.shortcuts.label')}: </span>
            {t('stack.shortcuts.hint')}
          </p>
        </section>
      </div>

      <StackDialogs
        tasks={tasks}
        semesters={semesterList}
        current={current}
        allSubjects={subjects.data ?? []}
        currentSubjects={currentSubjects}
      />
    </>
  );
}

interface StackDialogsProps {
  tasks: Task[];
  semesters: Semester[];
  current: Semester | undefined;
  allSubjects: Subject[];
  currentSubjects: Subject[];
}

/** Abre o diálogo pedido no store (pela tela, pelos atalhos ou pela paleta Ctrl K). */
function StackDialogs({
  tasks,
  semesters,
  current,
  allSubjects,
  currentSubjects,
}: StackDialogsProps) {
  const dialog = useStackStore((s) => s.dialog);
  const close = useStackStore((s) => s.closeDialog);
  const subjectFilter = useStackStore((s) => s.filters.subject);
  if (!dialog) return null;

  switch (dialog.kind) {
    case 'task': {
      const task = dialog.taskId ? tasks.find((x) => x.id === dialog.taskId) : undefined;
      if (dialog.taskId && !task) return null;
      // Matérias do semestre atual + a da tarefa, se for de outro semestre.
      const own = task?.subjectId ? allSubjects.find((s) => s.id === task.subjectId) : undefined;
      const options =
        own && !currentSubjects.some((s) => s.id === own.id)
          ? [...currentSubjects, own]
          : currentSubjects;
      const defaultSubjectId =
        subjectFilter !== 'all' && subjectFilter !== 'none' ? subjectFilter : undefined;
      return (
        <TaskDialog
          key={dialog.taskId ?? 'new'}
          task={task}
          subjects={options}
          defaultSubjectId={defaultSubjectId}
          onClose={close}
        />
      );
    }
    case 'subject': {
      const subject = dialog.subjectId
        ? allSubjects.find((s) => s.id === dialog.subjectId)
        : undefined;
      if (dialog.subjectId && !subject) return null;
      const options = semesters.filter((s) => !s.archived || s.id === subject?.semesterId);
      return (
        <SubjectDialog
          key={dialog.subjectId ?? 'new'}
          subject={subject}
          semesters={options}
          defaultSemesterId={current?.id}
          defaultColor={SUBJECT_COLORS[currentSubjects.length % SUBJECT_COLORS.length]!}
          onClose={close}
        />
      );
    }
    case 'semesters':
      return <SemestersDialog semesters={semesters} onClose={close} />;
    case 'semesterForm': {
      const semester = dialog.semesterId
        ? semesters.find((s) => s.id === dialog.semesterId)
        : undefined;
      return (
        <SemesterFormDialog key={dialog.semesterId ?? 'new'} semester={semester} onClose={close} />
      );
    }
  }
}
