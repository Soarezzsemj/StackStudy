import { useTranslation } from 'react-i18next';
import { Button, Card, Chip, Icon, ProgressBar } from '@/components/ui';
import type { Gpa, SubjectGrades } from '@/lib/api/grades';
import { cn } from '@/lib/cn';
import { attendanceTone, formatGrade, formatNumber, toneColor } from './format';

/** CR do semestre mostrado e acumulado, com a lista por semestre. */
export function GpaCard({ gpa, semesterId }: { gpa: Gpa; semesterId?: string }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const shown = gpa.semesters.find((s) => s.semesterId === semesterId);
  const withValue = gpa.semesters.filter((s) => s.value !== undefined);

  return (
    <Card title={t('grades.gpa.title')} aria-label={t('grades.gpa.title')}>
      <dl className="m-0 grid grid-cols-2 gap-4">
        <div className="flex flex-col gap-1">
          <dt className="label text-text-muted">{t('grades.gpa.semester')}</dt>
          <dd className="tabular m-0 text-4xl font-[650] leading-none tracking-card">
            {formatGrade(shown?.value, lang)}
          </dd>
        </div>
        <div className="flex flex-col gap-1">
          <dt className="label text-text-muted">{t('grades.gpa.cumulative')}</dt>
          <dd className="tabular m-0 text-4xl font-[650] leading-none tracking-card">
            {formatGrade(gpa.cumulative, lang)}
          </dd>
        </div>
      </dl>
      <p className="mt-4 text-sm text-text-secondary">
        {t('grades.gpa.basis', {
          scale: t(`grades.gpaScale.${gpa.scale}`),
          basis: t(`grades.gpaWeight.${gpa.weightBasis}`),
        })}
      </p>
      <ul className="m-0 mt-2 flex list-none flex-col gap-1 p-0 text-sm text-text-muted">
        {shown && shown.provisional > 0 && (
          <li>{t('grades.gpa.provisional', { count: shown.provisional })}</li>
        )}
        {shown && shown.excludedNoWeight > 0 && (
          <li className="text-warning">
            {t('grades.gpa.excluded', { count: shown.excludedNoWeight })}
          </li>
        )}
        <li>{t('grades.gpa.droppedNote')}</li>
      </ul>

      {withValue.length > 1 && (
        <div className="mt-5 border-t border-border pt-4">
          <h3 className="label mb-2 text-text-muted">{t('grades.gpa.bySemester')}</h3>
          <ul className="m-0 flex list-none flex-col gap-1 p-0">
            {withValue.map((s) => (
              <li
                key={s.semesterId}
                className={cn(
                  'flex items-center justify-between gap-3 text-sm',
                  s.semesterId === semesterId ? 'text-text' : 'text-text-secondary',
                )}
              >
                <span className="truncate">{s.name}</span>
                <span className="tabular">{formatGrade(s.value, lang)}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </Card>
  );
}

/** Painel de faltas: usado / limite por matéria, com aviso perto do limite e erro acima. */
export function AttendanceCard({
  subjects,
  onRegister,
}: {
  subjects: SubjectGrades[];
  onRegister: (subjectId?: string) => void;
}) {
  const { t } = useTranslation();
  return (
    <Card
      title={t('grades.attendance.title')}
      aria-label={t('grades.attendance.title')}
      actions={
        subjects.length > 0 ? (
          <Button
            variant="ghost"
            iconOnly
            aria-label={t('grades.attendance.register')}
            title={t('grades.attendance.register')}
            onClick={() => onRegister()}
          >
            <Icon name="plus" size={18} />
          </Button>
        ) : undefined
      }
    >
      <ul className="m-0 flex list-none flex-col gap-4 p-0">
        {subjects.map((s) => (
          <AttendanceRow key={s.subjectId} subject={s} onOpen={() => onRegister(s.subjectId)} />
        ))}
      </ul>
    </Card>
  );
}

function AttendanceRow({ subject, onOpen }: { subject: SubjectGrades; onOpen: () => void }) {
  const { t, i18n } = useTranslation();
  const a = subject.attendance;
  const tone = attendanceTone(a.status);
  const known = a.allowed !== undefined && a.remaining !== undefined;

  return (
    <li>
      <button
        type="button"
        onClick={onOpen}
        aria-label={t('grades.attendance.manage', { name: subject.name })}
        className="flex min-h-11 w-full flex-col gap-2 rounded-inner px-2 py-2 text-left transition-colors hover:bg-surface-raised"
      >
        <span className="flex w-full items-center justify-between gap-2 text-sm">
          <span className="flex min-w-0 items-center gap-2">
            <span
              aria-hidden="true"
              className="h-2 w-2 shrink-0 rounded-full"
              style={{ backgroundColor: subject.color }}
            />
            <span className="truncate">{subject.name}</span>
          </span>
          <span className="tabular shrink-0" style={{ color: toneColor(tone) }}>
            {known
              ? t('grades.card.absencesValue', { used: a.used, allowed: a.allowed })
              : t('grades.card.absencesUnknown', { used: a.used })}
          </span>
        </span>
        {known ? (
          <>
            <ProgressBar
              value={a.used}
              max={Math.max(a.allowed!, 1)}
              color={toneColor(tone)}
              label={t('grades.attendance.manage', { name: subject.name })}
            />
            <span className="flex w-full items-center justify-between gap-2 text-xs text-text-muted">
              <span>
                {t('grades.attendance.limit', {
                  pct: formatNumber(subject.rule.maxAbsencePct, i18n.language),
                  base: a.base,
                })}
              </span>
              {a.status === 'OVER' ? (
                <Chip tone="danger">{t('grades.attendance.over', { count: -a.remaining! })}</Chip>
              ) : (
                <span style={{ color: toneColor(tone) }}>
                  {t('grades.attendance.remaining', { count: a.remaining! })}
                </span>
              )}
            </span>
          </>
        ) : (
          <span className="text-xs text-text-muted">{t('grades.attendance.unknown')}</span>
        )}
      </button>
    </li>
  );
}
