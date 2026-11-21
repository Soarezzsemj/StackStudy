import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Card, Chip, Icon, SelectField } from '@/components/ui';
import type { SubjectGrades } from '@/lib/api/grades';
import { cn } from '@/lib/cn';
import { formatGrade, formatNumber, standingIcon, standingTone, toneColor } from './format';
import { hasWhatIf, maxScoreOf, parseScore, simulate, type WhatIf } from './simulation';
import { gradeHeadline, headlineSubjectOf } from './summary';

interface SimulatorCardProps {
  subjects: SubjectGrades[];
  selectedId: string;
  onSelect: (id: string) => void;
}

/**
 * "E se": notas hipotéticas nas avaliações sem nota, recalculadas na hora no navegador com a mesma
 * conta do backend. Nada é salvo.
 */
export function SimulatorCard({ subjects, selectedId, onSelect }: SimulatorCardProps) {
  const { t } = useTranslation();
  const subject = subjects.find((s) => s.subjectId === selectedId);

  return (
    <Card
      variant="hero"
      eyebrow={t('grades.simulator.eyebrow')}
      title={t('grades.simulator.title')}
      aria-label={t('grades.simulator.title')}
    >
      <div className="mb-5 max-w-sm">
        <SelectField
          label={t('grades.simulator.subject')}
          value={selectedId}
          onChange={(e) => onSelect(e.target.value)}
        >
          {subjects.map((s) => (
            <option key={s.subjectId} value={s.subjectId}>
              {s.name}
            </option>
          ))}
        </SelectField>
      </div>
      {/* key: trocar de matéria (ou os dados mudarem) recomeça a simulação. */}
      {subject && <SimulatorBody key={bodyKey(subject)} subject={subject} />}
    </Card>
  );
}

function bodyKey(s: SubjectGrades) {
  return `${s.subjectId}:${s.assessments.map((a) => `${a.id}=${a.score ?? ''}`).join(',')}`;
}

function SimulatorBody({ subject }: { subject: SubjectGrades }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const max = subject.rule.scaleMax;
  const isSum = subject.rule.method === 'SUM';
  const [texts, setTexts] = useState<Record<string, string>>({});
  const [examText, setExamText] = useState('');

  const parsed = useMemo(() => {
    const whatIf: WhatIf = {};
    const invalid = new Set<string>();
    for (const [id, text] of Object.entries(texts)) {
      const a = subject.assessments.find((x) => x.id === id);
      const value = parseScore(text, a ? maxScoreOf(subject, a) : max);
      if (value === 'invalid') invalid.add(id);
      else if (value !== null) whatIf[id] = value;
    }
    const examValue = parseScore(examText, max);
    return {
      whatIf,
      invalid,
      exam: examValue === 'invalid' || examValue === null ? undefined : examValue,
      examInvalid: examValue === 'invalid',
    };
  }, [texts, examText, max, subject]);

  const result = useMemo(
    () => simulate(subject, parsed.whatIf, parsed.exam),
    [subject, parsed.whatIf, parsed.exam],
  );
  const simulating = hasWhatIf(parsed.whatIf, parsed.exam);
  // Mesma mensagem do cartão, agora sobre o resultado simulado ("e se" preenchido conta como nota).
  const headline = gradeHeadline(
    {
      ...headlineSubjectOf(subject),
      pendingNames: subject.assessments
        .filter((a) => a.score === undefined && parsed.whatIf[a.id] === undefined)
        .map((a) => a.name),
    },
    result,
    lang,
  );
  // Exame só faz sentido quando a regra tem exame e ainda não há nota de exame gravada.
  const showExam =
    subject.rule.finalExamEnabled &&
    subject.finalExamScore === undefined &&
    result.examRequired !== null;

  function clear() {
    setTexts({});
    setExamText('');
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_minmax(0,280px)]">
      <div>
        <p className="mb-4 text-sm text-text-secondary">{t('grades.simulator.hint')}</p>
        <ul className="m-0 flex list-none flex-col gap-2 p-0">
          {subject.assessments.map((a, i) => {
            const inputId = `whatif-${a.id}`;
            const errorId = `${inputId}-error`;
            const isInvalid = parsed.invalid.has(a.id);
            const itemMax = maxScoreOf(subject, a);
            // Na soma, o necessário é por avaliação (repartido); nas médias, igual em todas.
            const needed = isSum ? (result.sum?.requiredPerItem[i] ?? null) : result.requiredScore;
            return (
              <li
                key={a.id}
                className="card-inner flex flex-wrap items-center justify-between gap-3 px-4 py-3"
              >
                <div className="min-w-0">
                  <p className="truncate font-semibold">{a.name}</p>
                  {subject.rule.method === 'WEIGHTED' && (
                    <p className="text-xs text-text-muted">
                      {t('grades.assessments.weight')} {formatNumber(a.weight, lang)}
                    </p>
                  )}
                  {isSum && (
                    <p className="text-xs text-text-muted">
                      {a.maxPoints !== undefined
                        ? t('grades.sum.worth', { points: formatGrade(a.maxPoints, lang) })
                        : t('grades.sum.worthUnknown')}
                      {a.bonus && ` · ${t('grades.assessments.bonus')}`}
                    </p>
                  )}
                </div>
                {a.score !== undefined ? (
                  <p className="tabular text-lg font-semibold">
                    {formatGrade(a.score, lang)}{' '}
                    <span className="text-xs font-normal text-text-muted">
                      {t('grades.simulator.graded')}
                    </span>
                  </p>
                ) : (
                  <div className="flex flex-col items-end gap-1">
                    <label htmlFor={inputId} className="sr-only">
                      {t('grades.simulator.whatIfNamed', { name: a.name })}
                    </label>
                    <input
                      id={inputId}
                      inputMode="decimal"
                      autoComplete="off"
                      className="input tabular w-32 text-right"
                      placeholder={
                        needed !== null && needed <= itemMax
                          ? t('grades.simulator.needed', { value: formatGrade(needed, lang) })
                          : undefined
                      }
                      value={texts[a.id] ?? ''}
                      aria-invalid={isInvalid || undefined}
                      aria-describedby={isInvalid ? errorId : undefined}
                      onChange={(e) => setTexts((s) => ({ ...s, [a.id]: e.target.value }))}
                    />
                    {isInvalid && (
                      <p id={errorId} className="text-xs text-danger">
                        {t('grades.simulator.invalid', { max: formatNumber(itemMax, lang) })}
                      </p>
                    )}
                  </div>
                )}
              </li>
            );
          })}
          {showExam && (
            <li className="card-inner flex flex-wrap items-center justify-between gap-3 px-4 py-3">
              <label htmlFor="whatif-exam" className="font-semibold">
                {t('grades.simulator.exam')}
              </label>
              <div className="flex flex-col items-end gap-1">
                <input
                  id="whatif-exam"
                  inputMode="decimal"
                  autoComplete="off"
                  className="input tabular w-32 text-right"
                  placeholder={
                    result.examRequired !== null && result.examRequired <= max
                      ? t('grades.simulator.needed', {
                          value: formatGrade(result.examRequired, lang),
                        })
                      : undefined
                  }
                  value={examText}
                  aria-invalid={parsed.examInvalid || undefined}
                  aria-describedby={parsed.examInvalid ? 'whatif-exam-error' : undefined}
                  onChange={(e) => setExamText(e.target.value)}
                />
                {parsed.examInvalid && (
                  <p id="whatif-exam-error" className="text-xs text-danger">
                    {t('grades.simulator.invalid', { max: formatNumber(max, lang) })}
                  </p>
                )}
              </div>
            </li>
          )}
        </ul>
      </div>

      <section
        aria-live="polite"
        aria-label={simulating ? t('grades.simulator.simulated') : t('grades.simulator.real')}
        className="card-inner flex flex-col gap-4 p-5"
      >
        <div className="flex items-center justify-between gap-2">
          <p className="label text-text-muted">
            {simulating ? t('grades.simulator.simulated') : t('grades.simulator.real')}
          </p>
          <Chip tone={standingTone(result.standing)}>
            <Icon name={standingIcon(result.standing)} size={14} />
            {isSum
              ? t(`grades.standingSum.${result.standing}`)
              : t(`grades.standing.${result.standing}`)}
          </Chip>
        </div>
        {isSum && result.sum ? (
          <dl className="m-0 flex flex-col gap-4">
            <div>
              <dt className="label text-text-muted">{t('grades.sum.points')}</dt>
              <dd className="tabular m-0 text-4xl font-[650] leading-none tracking-card">
                {t('grades.sum.ofPassing', {
                  total: formatGrade(result.sum.total, lang),
                  passing: formatGrade(subject.rule.passingGrade, lang),
                })}
              </dd>
            </div>
            {result.sum.pointsToPass > 0 && (
              <div>
                <dt className="label text-text-muted">{t('grades.sum.stillMissing')}</dt>
                <dd
                  className={cn(
                    'tabular m-0 text-xl font-semibold',
                    result.standing === 'IMPOSSIBLE' && 'text-danger',
                  )}
                >
                  {formatGrade(result.sum.pointsToPass, lang)}
                </dd>
              </div>
            )}
            {result.sum.pointsAvailable > 0 && (
              <div>
                <dt className="label text-text-muted">{t('grades.sum.available')}</dt>
                <dd className="tabular m-0 text-xl font-semibold">
                  {formatGrade(result.sum.pointsAvailable, lang)}
                </dd>
              </div>
            )}
            {result.examRequired !== null && result.standing !== 'PASSED' && (
              <div>
                <dt className="label text-text-muted">{t('grades.simulator.examNeeded')}</dt>
                <dd className="tabular m-0 text-xl font-semibold">
                  {formatGrade(result.examRequired, lang)}
                </dd>
              </div>
            )}
          </dl>
        ) : (
          <dl className="m-0 flex flex-col gap-4">
            <div>
              <dt className="label text-text-muted">{t('grades.simulator.average')}</dt>
              <dd className="tabular m-0 text-4xl font-[650] leading-none tracking-card">
                {formatGrade(result.finalGrade, lang)}
              </dd>
            </div>
            {result.requiredScore !== null && (
              <div>
                <dt className="label text-text-muted">{t('grades.simulator.stillNeeds')}</dt>
                <dd
                  className={
                    result.requiredScore > max
                      ? 'tabular m-0 text-xl font-semibold text-danger'
                      : 'tabular m-0 text-xl font-semibold'
                  }
                >
                  {formatGrade(result.requiredScore, lang)}
                  {result.requiredScore > max && (
                    <span className="ml-2 text-sm font-normal">
                      ({t('grades.card.aboveMax', { max: formatNumber(max, lang) })})
                    </span>
                  )}
                </dd>
              </div>
            )}
            {result.examRequired !== null && result.standing !== 'PASSED' && (
              <div>
                <dt className="label text-text-muted">{t('grades.simulator.examNeeded')}</dt>
                <dd
                  className={
                    result.examRequired > max
                      ? 'tabular m-0 text-xl font-semibold text-danger'
                      : 'tabular m-0 text-xl font-semibold'
                  }
                >
                  {formatGrade(result.examRequired, lang)}
                </dd>
              </div>
            )}
          </dl>
        )}
        {headline ? (
          <p
            className="flex items-start gap-2 text-sm"
            style={{ color: toneColor(standingTone(result.standing)) }}
          >
            <Icon name={standingIcon(result.standing)} size={18} className="mt-0.5 shrink-0" />
            <span>{t(`grades.headline.${headline.key}`, headline.values)}</span>
          </p>
        ) : (
          result.standing === 'IMPOSSIBLE' && (
            <p className="text-sm text-danger">{t('grades.card.impossibleBody')}</p>
          )
        )}
        {simulating && (
          <Button variant="secondary" onClick={clear}>
            {t('grades.simulator.clear')}
          </Button>
        )}
      </section>
    </div>
  );
}
