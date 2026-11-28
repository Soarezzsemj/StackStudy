import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { formatGrade } from '@/features/grades/format';
import { sidebarSubjects, sidebarValue } from '@/features/grades/gradeView';
import { useGradesOverview } from '@/features/grades/queries';
import { useCurrentSubjects } from '@/features/stack/queries';
import { cn } from '@/lib/cn';
import { useStackStore } from '@/stores/stackStore';

/**
 * Matérias do semestre atual na barra lateral (Fase 14): grade de quadradinhos (2 colunas) com a
 * cor, o nome em até 2 linhas e a situação (nota/pontos ou "—"). No menu recolhido, uma coluna de
 * pontos coloridos com tooltip. Os dados são os mesmos da The Stack e do Grade Predictor (mesmas
 * consultas em cache do TanStack Query): nada novo é buscado nem atualizado em segundo plano.
 */
export function SidebarSubjects({
  compact = false,
  onNavigate,
}: {
  compact?: boolean;
  onNavigate?: () => void;
}) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { list: subjects, current } = useCurrentSubjects();
  const grades = useGradesOverview(undefined, { background: true });
  const setFilters = useStackStore((st) => st.setFilters);
  const gradeOf = new Map((grades.data?.subjects ?? []).map((g) => [g.subjectId, g] as const));
  const { shown, hidden } = sidebarSubjects(subjects);

  function openSubject(id: string) {
    setFilters({ subject: id });
    navigate('/stack');
    onNavigate?.();
  }

  const heading = current ? t('shell.subjectsOf', { semester: current.name }) : t('shell.subjects');

  return (
    <section
      aria-labelledby={compact ? undefined : 'sidebar-subjects'}
      aria-label={compact ? heading : undefined}
      className="flex min-h-0 flex-1 flex-col"
    >
      {!compact && (
        <h2 id="sidebar-subjects" className="label mb-2 px-3 font-medium text-text-muted">
          {heading}
        </h2>
      )}
      {subjects.length === 0 ? (
        !compact && (
          <p className="px-3 text-sm text-text-muted">
            {current ? t('shell.noSubjects') : t('shell.noSemester')}
          </p>
        )
      ) : (
        <>
          <ul
            className={cn(
              'm-0 list-none overflow-y-auto p-0',
              compact
                ? 'flex max-h-[50vh] flex-col items-center gap-1'
                : 'grid max-h-[min(22rem,42vh)] grid-cols-2 gap-2 px-1 py-1',
            )}
          >
            {shown.map((s) => {
              const grade = gradeOf.get(s.id);
              const value = grade ? sidebarValue(grade) : null;
              const shownValue = value === null ? '—' : formatGrade(value, i18n.language);
              const label = t('shell.subjectTile', {
                name: s.name,
                value: value === null ? t('shell.noGrade') : shownValue,
              });
              return (
                <li key={s.id} className="min-w-0">
                  <button
                    type="button"
                    title={label}
                    aria-label={label}
                    onClick={() => openSubject(s.id)}
                    className={
                      compact
                        ? 'flex h-11 w-11 items-center justify-center rounded-pill hover:bg-surface'
                        : 'flex h-full min-h-[4.5rem] w-full flex-col justify-between gap-1 rounded-small border border-l-4 border-border bg-surface/60 py-2 pl-2 pr-1.5 text-left transition-colors hover:bg-surface'
                    }
                    // A cor da matéria é a faixa da esquerda (sobra mais espaço para o nome).
                    style={compact ? undefined : { borderLeftColor: s.color }}
                  >
                    {compact ? (
                      <span
                        aria-hidden="true"
                        className="h-3 w-3 rounded-full"
                        style={{ backgroundColor: s.color }}
                      />
                    ) : (
                      <>
                        <span
                          aria-hidden="true"
                          className="line-clamp-2 hyphens-auto break-words text-xs font-medium leading-tight text-text"
                        >
                          {s.name}
                        </span>
                        <span aria-hidden="true" className="tabular text-xs text-text-muted">
                          {shownValue}
                        </span>
                      </>
                    )}
                  </button>
                </li>
              );
            })}
          </ul>
          {hidden > 0 && (
            <Link
              to="/stack"
              onClick={onNavigate}
              title={compact ? t('shell.viewAll', { count: subjects.length }) : undefined}
              aria-label={compact ? t('shell.viewAll', { count: subjects.length }) : undefined}
              className={cn(
                'mt-1 flex min-h-11 items-center rounded-pill text-sm font-semibold text-action hover:bg-surface',
                compact ? 'justify-center px-0 text-xs' : 'px-3',
              )}
            >
              {compact ? `+${hidden}` : t('shell.viewAll', { count: subjects.length })}
            </Link>
          )}
        </>
      )}
    </section>
  );
}
