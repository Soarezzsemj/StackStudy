import { useTranslation } from 'react-i18next';
import type { SubjectGrades } from '@/lib/api/grades';
import { attendanceTone, formatGrade, standingTone, toneColor } from './format';
import { gradeHeadline, headlineSubjectOf } from './summary';

/**
 * Linha curta para a lista de matérias da The Stack: média atual (ou pontos, na soma) e faltas,
 * com a cor de risco. Não mostra nada enquanto não houver nota nem falta.
 */
export function SubjectGradeLine({ grades }: { grades: SubjectGrades | undefined }) {
  const { t, i18n } = useTranslation();
  if (!grades) return null;
  const lang = i18n.language;
  const sum = grades.rule.method === 'SUM' ? grades.result.sum : undefined;
  const average = grades.result.complete ? grades.result.finalGrade : grades.result.currentAverage;
  const used = grades.attendance.used;
  if (average === undefined && used === 0) return null;

  const gradeTone = standingTone(grades.result.standing);
  const absenceTone = attendanceTone(grades.attendance.status);
  const headline = gradeHeadline(headlineSubjectOf(grades), grades.result, lang);
  const gradeText = sum
    ? t('grades.stackLine.points', {
        total: formatGrade(sum.total, lang),
        passing: formatGrade(grades.rule.passingGrade, lang),
      })
    : t('grades.stackLine.average', { value: formatGrade(average, lang) });
  return (
    <span className="tabular truncate text-xs font-normal text-text-muted">
      {average !== undefined && (
        <span
          style={gradeTone === 'action' ? undefined : { color: toneColor(gradeTone) }}
          title={headline ? t(`grades.headline.${headline.key}`, headline.values) : undefined}
        >
          {gradeText}
          <span className="sr-only">
            {' '}
            (
            {headline
              ? t(`grades.headline.${headline.key}`, headline.values)
              : t(`grades.standing.${grades.result.standing}`)}
            )
          </span>
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
