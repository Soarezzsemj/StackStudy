import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import {
  Button,
  buttonClass,
  EmptyState,
  ErrorState,
  Icon,
  LoadingState,
  PageTitle,
  SelectField,
} from '@/components/ui';
import { useSemesters } from '@/features/stack/queries';
import { AllSubjects } from './AllSubjects';
import { AbsenceDialog, AssessmentsDialog, GradeSettingsDialog, RuleDialog } from './GradeDialogs';
import { GradeFocusCard } from './GradeFocusCard';
import { defaultSubjectId } from './gradeView';
import { useGradesOverview } from './queries';
import { AttendanceCard, GpaCard } from './SideCards';

type PageDialog =
  | { kind: 'settings' }
  | { kind: 'rule'; subjectId: string }
  | { kind: 'assessments'; subjectId: string }
  | { kind: 'absence'; subjectId?: string }
  | null;

/**
 * Grade Predictor (layout da Fase 14): abas de matéria com a regra, o destaque, as avaliações com
 * "e se" e os cenários; "Todas as matérias" em cartões pequenos; CR e faltas na lateral. As
 * matérias são as mesmas da The Stack.
 */
export function GradesPage() {
  const { t } = useTranslation();
  const semesters = useSemesters();
  const [semesterId, setSemesterId] = useState<string | undefined>(undefined);
  const overview = useGradesOverview(semesterId);
  const [dialog, setDialog] = useState<PageDialog>(null);
  const [chosenId, setChosenId] = useState<string | undefined>(undefined);
  const focusRef = useRef<HTMLDivElement>(null);

  const data = overview.data;
  const subjects = data?.subjects ?? [];
  const selectedId =
    subjects.find((s) => s.subjectId === chosenId)?.subjectId ?? defaultSubjectId(subjects);
  const dialogSubject =
    dialog && 'subjectId' in dialog
      ? subjects.find((s) => s.subjectId === dialog.subjectId)
      : undefined;
  const semesterOptions = (semesters.data ?? []).filter(
    (s) => !s.archived || s.id === data?.semesterId,
  );
  const semesterName = semesters.data?.find((s) => s.id === data?.semesterId)?.name;

  return (
    <>
      <PageTitle
        title={t('pages.grades.title')}
        eyebrow={
          semesterName
            ? `${t('pages.grades.eyebrow')} · ${semesterName}`
            : t('pages.grades.eyebrow')
        }
        actions={
          <>
            {semesterOptions.length > 1 && (
              <div className="w-48">
                <SelectField
                  label={t('grades.semester')}
                  value={data?.semesterId ?? ''}
                  onChange={(e) => setSemesterId(e.target.value)}
                >
                  {semesterOptions.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name}
                    </option>
                  ))}
                </SelectField>
              </div>
            )}
            <Button variant="secondary" onClick={() => setDialog({ kind: 'settings' })}>
              {t('grades.settings.open')}
            </Button>
            {subjects.length > 0 && (
              <Button onClick={() => setDialog({ kind: 'absence' })}>
                <Icon name="plus" size={18} />
                {t('grades.attendance.register')}
              </Button>
            )}
          </>
        }
      />

      {overview.isPending ? (
        <LoadingState />
      ) : overview.isError ? (
        <ErrorState message={t('grades.loadError')} onRetry={() => void overview.refetch()} />
      ) : !data!.semesterId ? (
        <EmptyState
          title={t('grades.empty.noSemesterTitle')}
          body={t('grades.empty.noSemesterBody')}
          action={
            <Link to="/stack" className={buttonClass('secondary')}>
              {t('grades.empty.goStack')}
            </Link>
          }
        />
      ) : (
        <div className="grid grid-cols-[minmax(0,1fr)] gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(17rem,21rem)]">
          <div className="flex min-w-0 flex-col gap-6">
            {subjects.length === 0 ? (
              <EmptyState
                title={t('grades.empty.noSubjectsTitle')}
                body={t('grades.empty.noSubjectsBody')}
                action={
                  <Link to="/stack" className={buttonClass('secondary')}>
                    {t('grades.empty.goStack')}
                  </Link>
                }
              />
            ) : (
              <>
                <div ref={focusRef} className="scroll-mt-16">
                  <GradeFocusCard
                    subjects={subjects}
                    selectedId={selectedId!}
                    onSelect={setChosenId}
                    onEditAssessments={(subjectId) => setDialog({ kind: 'assessments', subjectId })}
                    onEditRule={(subjectId) => setDialog({ kind: 'rule', subjectId })}
                  />
                </div>
                <AllSubjects
                  subjects={subjects}
                  selectedId={selectedId}
                  onSelect={(id) => {
                    setChosenId(id);
                    // Leva até o cartão principal e põe o foco na aba (teclado e leitor de tela).
                    focusRef.current?.scrollIntoView?.({ block: 'start' });
                    requestAnimationFrame(() =>
                      document.getElementById(`grade-tab-${id}`)?.focus({ preventScroll: true }),
                    );
                  }}
                />
                <p className="text-center text-xs text-text-muted">{t('grades.simulationHint')}</p>
              </>
            )}
          </div>
          <aside className="flex min-w-0 flex-col gap-6">
            <GpaCard gpa={data!.gpa} semesterId={data!.semesterId} />
            {subjects.length > 0 && (
              <AttendanceCard
                subjects={subjects}
                onRegister={(subjectId) => setDialog({ kind: 'absence', subjectId })}
              />
            )}
          </aside>
        </div>
      )}

      {dialog?.kind === 'settings' && data && (
        <GradeSettingsDialog settings={data.settings} onClose={() => setDialog(null)} />
      )}
      {dialog?.kind === 'rule' && dialogSubject && data && (
        <RuleDialog
          subject={dialogSubject}
          settings={data.settings}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.kind === 'assessments' && dialogSubject && (
        <AssessmentsDialog subject={dialogSubject} onClose={() => setDialog(null)} />
      )}
      {dialog?.kind === 'absence' && (
        <AbsenceDialog
          subjects={subjects}
          defaultSubjectId={dialog.subjectId}
          onClose={() => setDialog(null)}
        />
      )}
    </>
  );
}
