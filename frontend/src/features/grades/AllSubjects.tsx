import { useTranslation } from 'react-i18next';
import { Icon } from '@/components/ui';
import type { SubjectGrades } from '@/lib/api/grades';
import { cn } from '@/lib/cn';
import { formatGrade, standingIcon, toneColor } from './format';
import { bucketTone, mainFigure, statusBucket } from './gradeView';

/**
 * "Todas as matérias": cartões pequenos (nome, regra, número principal e situação). Clicar abre a
 * matéria no cartão principal. A grade se ajusta sozinha (auto-fill), sem largura fixa.
 */
export function AllSubjects({
  subjects,
  selectedId,
  onSelect,
}: {
  subjects: SubjectGrades[];
  selectedId: string | undefined;
  onSelect: (id: string) => void;
}) {
  const { t } = useTranslation();
  return (
    <section aria-labelledby="grades-all-title">
      <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
        <h2 id="grades-all-title" className="card-title m-0 text-lg">
          {t('grades.all.title')}
        </h2>
        <p className="m-0 text-sm text-text-muted">{t('grades.all.hint')}</p>
      </div>
      <ul className="grid-fit m-0 list-none p-0 [--fit-min:12rem]">
        {subjects.map((s) => (
          <li key={s.subjectId}>
            <MiniCard
              subject={s}
              selected={s.subjectId === selectedId}
              onSelect={() => onSelect(s.subjectId)}
            />
          </li>
        ))}
      </ul>
    </section>
  );
}

function MiniCard({
  subject,
  selected,
  onSelect,
}: {
  subject: SubjectGrades;
  selected: boolean;
  onSelect: () => void;
}) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const { result, rule } = subject;
  const bucket = statusBucket(result.standing);
  const color = toneColor(bucketTone(bucket));
  const figure = mainFigure(rule, result);
  const status = t(`grades.bucket.${bucket}`);
  // Número curto: "5,50 / 7,00" na soma; "precisa 5,10" enquanto falta nota; senão a média.
  const value =
    figure.kind === 'sum'
      ? `${formatGrade(figure.total, lang)} / ${formatGrade(figure.passing, lang)}`
      : figure.kind === 'average'
        ? !result.complete &&
          result.requiredScore !== undefined &&
          result.standing !== 'SECURED' &&
          result.requiredScore <= rule.scaleMax
          ? t('grades.simulator.needed', { value: formatGrade(result.requiredScore, lang) })
          : formatGrade(figure.value, lang)
        : '—';

  return (
    <button
      type="button"
      aria-pressed={selected}
      aria-label={t('grades.all.open', { name: subject.name, status })}
      onClick={onSelect}
      className={cn(
        'card flex h-full min-h-11 w-full flex-col gap-1 p-4 text-left transition-colors hover:border-border-control',
        selected && 'border-action/70',
      )}
    >
      <span className="flex min-w-0 items-center gap-2 font-semibold">
        <span
          aria-hidden="true"
          className="h-2 w-2 shrink-0 rounded-full"
          style={{ backgroundColor: subject.color }}
        />
        <span className="truncate">{subject.name}</span>
      </span>
      <span className="text-xs text-text-muted">{t(`grades.method.${rule.method}`)}</span>
      <span className="mt-1 flex flex-wrap items-end justify-between gap-x-3 gap-y-1">
        <span
          className="figure-sm tabular"
          style={{ color: bucket === 'NONE' ? undefined : color }}
        >
          {value}
        </span>
        <span
          className="label inline-flex items-center gap-1"
          style={{ color: bucket === 'NONE' ? 'var(--text-muted)' : color }}
        >
          <Icon name={standingIcon(result.standing)} size={14} />
          {status}
        </span>
      </span>
    </button>
  );
}
