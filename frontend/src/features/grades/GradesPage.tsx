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
import { AbsenceDialog, AssessmentsDialog, GradeSettingsDialog, RuleDialog } from './GradeDialogs';
import { useGradesOverview } from './queries';
import { AttendanceCard, GpaCard } from './SideCards';
import { SimulatorCard } from './SimulatorCard';
import { SubjectGradeCard } from './SubjectGradeCard';

type PageDialog =
  | { kind: 'settings' }
  | { kind: 'rule'; subjectId: string }
  | { kind: 'assessments'; subjectId: string }
  | { kind: 'absence'; subjectId?: string }
  | null;

/**
 * Grade Predictor: cartões por matéria (média, nota necessária, situação), simulador "e se",
 * faltas e CR. As matérias são as mesmas da The Stack.
 */
export function GradesPage() {
  const { t } = useTranslation();
  const semesters = useSemesters();
  const [semesterId, setSemesterId] = useState<string | undefined>(undefined);
  const overview = useGradesOverview(semesterId);
  const [dialog, setDialog] = useState<PageDialog>(null);
  const [simulatedId, setSimulatedId] = useState<string | undefined>(undefined);
  const simulatorRef = useRef<HTMLDivElement>(null);

  const data = overview.data;
  const subjects = data?.subjects ?? [];
  const simulatable = subjects.filter((s) => s.assessments.length > 0);
  const simulated =
    simulatable.find((s) => s.subjectId === simulatedId)?.subjectId ?? simulatable[0]?.subjectId;
  const dialogSubject =
    dialog && 'subjectId' in dialog
      ? subjects.find((s) => s.subjectId === dialog.subjectId)
      : undefined;
  const semesterOptions = (semesters.data ?? []).filter(
    (s) => !s.archived || s.id === data?.semesterId,
  );

  return (
    <>
      <PageTitle
        title={t('pages.grades.title')}
        description={t('pages.grades.description')}
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
        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_340px]">
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
                {simulated ? (
                  <div ref={simulatorRef} className="scroll-mt-16">
                    <SimulatorCard
                      subjects={simulatable}
                      selectedId={simulated}
                      onSelect={setSimulatedId}
                    />
                  </div>
                ) : (
                  <EmptyState title={t('grades.simulator.empty')} />
                )}
                <section aria-label={t('pages.grades.title')} className="grid gap-4 md:grid-cols-2">
                  {subjects.map((s) => (
                    <SubjectGradeCard
                      key={s.subjectId}
                      subject={s}
                      selected={s.subjectId === simulated}
                      onSimulate={() => {
                        setSimulatedId(s.subjectId);
                        // Leva até o simulador (o cartão pode estar bem abaixo).
                        simulatorRef.current?.scrollIntoView?.({ block: 'start' });
                      }}
                      onEditAssessments={() =>
                        setDialog({ kind: 'assessments', subjectId: s.subjectId })
                      }
                      onEditRule={() => setDialog({ kind: 'rule', subjectId: s.subjectId })}
                    />
                  ))}
                </section>
                <p className="text-center text-xs text-text-muted">{t('grades.simulationHint')}</p>
              </>
            )}
          </div>
          <aside className="flex flex-col gap-6">
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
