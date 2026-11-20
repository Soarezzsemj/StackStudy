import { useTranslation } from 'react-i18next';
import { Button, Card, Chip, ProgressBar } from '@/components/ui';
import type { SubjectGrades } from '@/lib/api/grades';
import { cn } from '@/lib/cn';
import { attendanceTone, formatGrade, formatNumber, standingTone, toneColor } from './format';

interface SubjectGradeCardProps {
  subject: SubjectGrades;
  selected: boolean;
  onSimulate: () => void;
  onEditAssessments: () => void;
  onEditRule: () => void;
}

/** Cartão da matéria: média atual, nota necessária, exame, situação e faltas. */
export function SubjectGradeCard({
  subject,
  selected,
  onSimulate,
  onEditAssessments,
  onEditRule,
}: SubjectGradeCardProps) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const { result, rule, attendance } = subject;
  const tone = standingTone(result.standing);
  const titleId = `grade-card-${subject.subjectId}`;
  const dropped = subject.status === 'DROPPED';

  return (
    <Card
      as="article"
      aria-labelledby={titleId}
      className={cn('flex flex-col gap-5 p-5', selected && 'border-action/60')}
    >
      <header className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 id={titleId} className="card-title flex items-center gap-2">
            <span
              aria-hidden="true"
              className="h-2.5 w-2.5 shrink-0 rounded-full"
              style={{ backgroundColor: subject.color }}
            />
            <span className="truncate">{subject.name}</span>
          </h3>
          <p className="mt-1 text-sm text-text-muted">
            {t(`grades.method.${rule.method}`)} ·{' '}
            {t('grades.card.passing', {
              value: formatNumber(rule.passingGrade, lang),
              max: formatNumber(rule.scaleMax, lang),
            })}
          </p>
        </div>
        <Chip tone={tone} className="shrink-0">
          {t(`grades.standing.${result.standing}`)}
        </Chip>
      </header>

      {result.standing === 'NO_ASSESSMENTS' ? (
        <p className="text-sm text-text-secondary">{t('grades.card.noAssessments')}</p>
      ) : (
        <>
          <dl className="m-0 grid grid-cols-2 gap-4">
            <Stat
              label={
                result.complete ? t('grades.card.finalGrade') : t('grades.card.currentAverage')
              }
              value={formatGrade(result.complete ? result.finalGrade : result.currentAverage, lang)}
            />
            <RequiredStat subject={subject} />
          </dl>
          {result.standing === 'IMPOSSIBLE' && (
            <p className="text-sm text-danger">{t('grades.card.impossibleBody')}</p>
          )}
          <ProgressBar value={result.gradedWeightPct} label={t('grades.card.progress')} showValue />
        </>
      )}

      <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border pt-4">
        <p className="text-sm text-text-secondary">
          <span className="label mr-2 text-text-muted">{t('grades.card.absences')}</span>
          <span className="tabular" style={{ color: toneColor(attendanceTone(attendance.status)) }}>
            {attendance.allowed !== undefined
              ? t('grades.card.absencesValue', {
                  used: attendance.used,
                  allowed: attendance.allowed,
                })
              : t('grades.card.absencesUnknown', { used: attendance.used })}
          </span>
          <span className="sr-only"> · {t(`grades.attendanceStatus.${attendance.status}`)}</span>
        </p>
        {(attendance.status === 'WARNING' || attendance.status === 'OVER') && (
          <Chip tone={attendanceTone(attendance.status)}>
            {t(`grades.attendanceStatus.${attendance.status}`)}
          </Chip>
        )}
      </div>
      {dropped && <p className="text-sm text-text-muted">{t('grades.card.dropped')}</p>}

      <div className="flex flex-wrap gap-2">
        {subject.assessments.length > 0 && (
          <Button
            variant={selected ? 'primary' : 'secondary'}
            aria-pressed={selected}
            aria-label={t('grades.card.simulateNamed', { name: subject.name })}
            onClick={onSimulate}
          >
            {t('grades.card.simulate')}
          </Button>
        )}
        <Button
          variant="ghost"
          aria-label={t('grades.card.assessmentsNamed', { name: subject.name })}
          onClick={onEditAssessments}
        >
          {t('grades.card.assessments')}
        </Button>
        <Button
          variant="ghost"
          aria-label={t('grades.card.ruleNamed', { name: subject.name })}
          onClick={onEditRule}
        >
          {t('grades.card.rule')}
        </Button>
      </div>
    </Card>
  );
}

function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="flex flex-col gap-1">
      <dt className="label text-text-muted">{label}</dt>
      <dd className="tabular m-0 text-3xl font-[650] leading-none tracking-card">{value}</dd>
      {hint && <dd className="m-0 text-xs text-text-secondary">{hint}</dd>}
    </div>
  );
}

/** Nota necessária (ou no exame), com o caso "acima do máximo" explícito. */
function RequiredStat({ subject }: { subject: SubjectGrades }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const { result, rule } = subject;

  if (result.examRequired !== undefined && result.standing !== 'PASSED') {
    return (
      <Stat
        label={t('grades.card.exam')}
        value={formatGrade(result.examRequired, lang)}
        hint={
          result.examRequired > rule.scaleMax
            ? t('grades.card.aboveMax', { max: formatNumber(rule.scaleMax, lang) })
            : result.complete || result.requiredScore === undefined
              ? undefined
              : t('grades.card.examBest')
        }
      />
    );
  }
  if (result.requiredScore === undefined) {
    return (
      <Stat label={t('grades.card.required')} value="—" hint={t('grades.card.requiredDone')} />
    );
  }
  if (result.standing === 'SECURED') {
    return (
      <Stat
        label={t('grades.card.required')}
        value={formatGrade(0, lang)}
        hint={t('grades.card.secured')}
      />
    );
  }
  return (
    <Stat
      label={t('grades.card.required')}
      value={formatGrade(result.requiredScore, lang)}
      hint={
        result.requiredScore > rule.scaleMax
          ? t('grades.card.aboveMax', { max: formatNumber(rule.scaleMax, lang) })
          : t('grades.card.requiredHint')
      }
    />
  );
}
