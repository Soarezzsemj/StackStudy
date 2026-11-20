import { useTranslation } from 'react-i18next';
import type { SubjectGrades } from '@/lib/api/grades';
import { attendanceTone, formatGrade, standingTone, toneColor } from './format';

/**
 * Linha curta para a lista de matérias da The Stack: média atual e faltas, com a cor de risco.
 * Não mostra nada enquanto não houver nota nem falta.
 */
export function SubjectGradeLine({ grades }: { grades: SubjectGrades | undefined }) {
  const { t, i18n } = useTranslation();
  if (!grades) return null;
  const average = grades.result.complete ? grades.result.finalGrade : grades.result.currentAverage;
  const used = grades.attendance.used;
  if (average === undefined && used === 0) return null;

  const gradeTone = standingTone(grades.result.standing);
  const absenceTone = attendanceTone(grades.attendance.status);
  return (
    <span className="tabular truncate text-xs font-normal text-text-muted">
      {average !== undefined && (
        <span style={gradeTone === 'action' ? undefined : { color: toneColor(gradeTone) }}>
          {t('grades.stackLine.average', { value: formatGrade(average, i18n.language) })}
          <span className="sr-only"> ({t(`grades.standing.${grades.result.standing}`)})</span>
        </span>
      )}
      {average !== undefined && used > 0 && <span aria-hidden="true"> · </span>}
      {used > 0 && (
        <span
          style={
            absenceTone === 'warning' || absenceTone === 'danger'
              ? { color: toneColor(absenceTone) }
              : undefined
          }
        >
          {t('grades.stackLine.absences', { count: used })}
          {(absenceTone === 'warning' || absenceTone === 'danger') && (
            <span className="sr-only">
              {' '}
              ({t(`grades.attendanceStatus.${grades.attendance.status}`)})
            </span>
          )}
        </span>
      )}
    </span>
  );
}
