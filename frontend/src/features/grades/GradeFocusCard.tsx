import { useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Card, Chip, EmptyState, ErrorState, Icon, ProgressBar } from '@/components/ui';
import { errorMessage } from '@/features/auth/errorMessage';
import { GRADE_METHODS, type GradeMethod, type SubjectGrades } from '@/lib/api/grades';
import { cn } from '@/lib/cn';
import type { CalcResult } from './calculator';
import { formatGrade, formatNumber, standingIcon, standingTone, toneColor } from './format';
import { ScaleMismatchNote } from './GradeDialogs';
import { mainFigure, ruleInputWithMethod, statusBucket } from './gradeView';
import { useSaveRule } from './queries';
import {
  hasWhatIf,
  maxScoreOf,
  parseScore,
  scenariosOf,
  simulate,
  type WhatIf,
} from './simulation';
import { gradeHeadline, headlineSubjectOf } from './summary';

interface GradeFocusCardProps {
  subjects: SubjectGrades[];
  selectedId: string;
  onSelect: (id: string) => void;
  onEditAssessments: (id: string) => void;
  onEditRule: (id: string) => void;
}

/**
 * Cartão principal do Grade Predictor (mockup da Fase 14): abas de matéria, regra, destaque com o
 * número principal, avaliações com "e se" e cenários. A conta é sempre a da calculadora (espelho
 * do backend); nada é salvo, exceto a troca de regra.
 */
export function GradeFocusCard({
  subjects,
  selectedId,
  onSelect,
  onEditAssessments,
  onEditRule,
}: GradeFocusCardProps) {
  const { t } = useTranslation();
  const subject = subjects.find((s) => s.subjectId === selectedId) ?? subjects[0];
  if (!subject) return null;
  const panelId = `grade-panel-${subject.subjectId}`;

  return (
    <Card variant="hero" aria-label={t('grades.simulator.title')} className="p-4 sm:p-6">
      <SubjectTabs subjects={subjects} selectedId={subject.subjectId} onSelect={onSelect} />
      <div
        role="tabpanel"
        id={panelId}
        aria-labelledby={tabId(subject.subjectId)}
        className="mt-5 flex flex-col gap-5"
      >
        <div className="flex flex-wrap items-center justify-between gap-3">
          <RuleSwitch subject={subject} />
          <div className="flex flex-wrap gap-1">
            <Button
              variant="ghost"
              aria-label={t('grades.card.assessmentsNamed', { name: subject.name })}
              onClick={() => onEditAssessments(subject.subjectId)}
            >
              <Icon name="edit" size={16} />
              {t('grades.card.assessments')}
            </Button>
            <Button
              variant="ghost"
              aria-label={t('grades.card.ruleNamed', { name: subject.name })}
              onClick={() => onEditRule(subject.subjectId)}
            >
              {t('grades.card.rule')}
            </Button>
          </div>
        </div>
        {/* key: trocar de matéria (ou os dados mudarem) recomeça a simulação. */}
        <FocusBody
          key={bodyKey(subject)}
          subject={subject}
          onEditAssessments={() => onEditAssessments(subject.subjectId)}
        />
      </div>
    </Card>
  );
}

function tabId(subjectId: string) {
  return `grade-tab-${subjectId}`;
}

function bodyKey(s: SubjectGrades) {
  return `${s.subjectId}:${s.rule.method}:${s.assessments.map((a) => `${a.id}=${a.score ?? ''}`).join(',')}`;
}

/** Abas de matéria: setas, Home e End trocam (e selecionam) a aba, como no padrão do WAI-ARIA. */
function SubjectTabs({
  subjects,
  selectedId,
  onSelect,
}: {
  subjects: SubjectGrades[];
  selectedId: string;
  onSelect: (id: string) => void;
}) {
  const { t } = useTranslation();
  const listRef = useRef<HTMLDivElement>(null);

  function onKeyDown(e: KeyboardEvent<HTMLButtonElement>) {
    const index = subjects.findIndex((s) => s.subjectId === selectedId);
    const last = subjects.length - 1;
    const next =
      e.key === 'ArrowRight' || e.key === 'ArrowDown'
        ? index === last
          ? 0
          : index + 1
        : e.key === 'ArrowLeft' || e.key === 'ArrowUp'
          ? index === 0
            ? last
            : index - 1
          : e.key === 'Home'
            ? 0
            : e.key === 'End'
              ? last
              : null;
    if (next === null) return;
    e.preventDefault();
    const id = subjects[next]!.subjectId;
    onSelect(id);
    listRef.current?.querySelector<HTMLElement>(`#${CSS.escape(tabId(id))}`)?.focus();
  }

  return (
    <div
      ref={listRef}
      role="tablist"
      aria-label={t('grades.focus.tabs')}
      className="flex flex-wrap gap-2"
    >
      {subjects.map((s) => {
        const selected = s.subjectId === selectedId;
        return (
          <button
            key={s.subjectId}
            id={tabId(s.subjectId)}
            type="button"
            role="tab"
            aria-selected={selected}
            aria-controls={selected ? `grade-panel-${s.subjectId}` : undefined}
            tabIndex={selected ? 0 : -1}
            onClick={() => onSelect(s.subjectId)}
            onKeyDown={onKeyDown}
            className={cn(
              'flex min-h-11 max-w-full items-center gap-2 rounded-pill border px-4 text-sm font-medium transition-colors',
              selected
                ? 'border-border-control bg-surface-raised text-text'
                : 'border-border text-text-secondary hover:text-text',
            )}
          >
            <span
              aria-hidden="true"
              className="h-2 w-2 shrink-0 rounded-full"
              style={{ backgroundColor: s.color }}
            />
            <span className="truncate">{s.name}</span>
          </button>
        );
      })}
    </div>
  );
}

/** Regra da matéria em três botões: trocar salva na hora (os pesos e pontos ficam guardados). */
function RuleSwitch({ subject }: { subject: SubjectGrades }) {
  const { t } = useTranslation();
  const save = useSaveRule();
  const current = subject.rule.method;

  function pick(method: GradeMethod) {
    if (method === current || save.isPending) return;
    save.mutate({ subjectId: subject.subjectId, input: ruleInputWithMethod(subject, method) });
  }

  return (
    <div className="flex min-w-0 flex-col gap-1">
      <div
        role="group"
        aria-label={t('grades.focus.ruleLabel', { name: subject.name })}
        aria-describedby="grade-rule-hint"
        className="segmented grid w-full grid-cols-3 rounded-inner sm:inline-flex sm:w-auto sm:rounded-pill"
      >
        {GRADE_METHODS.map((m) => (
          <button
            key={m}
            type="button"
            aria-pressed={m === current}
            disabled={save.isPending}
            onClick={() => pick(m)}
            className="segmented-item px-2 text-xs leading-tight aria-pressed:!bg-action aria-pressed:!text-action-ink sm:px-3 sm:text-sm"
          >
            {t(`grades.method.${m}`)}
          </button>
        ))}
      </div>
      <p id="grade-rule-hint" className="sr-only">
        {t('grades.rule.switchHint')}
      </p>
      {save.isPending && (
        <p role="status" className="text-xs text-text-muted">
          {t('grades.focus.ruleSaving')}
        </p>
      )}
      {save.isError && <ErrorState message={errorMessage(t, save.error)} />}
    </div>
  );
}

function FocusBody({
  subject,
  onEditAssessments,
}: {
  subject: SubjectGrades;
  onEditAssessments: () => void;
}) {
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
  const scenarios = useMemo(() => scenariosOf(subject), [subject]);
  const simulating = hasWhatIf(parsed.whatIf, parsed.exam);

  if (subject.assessments.length === 0) {
    return (
      <EmptyState
        title={t('grades.focus.noAssessmentsTitle')}
        body={t('grades.card.noAssessments')}
        action={
          <Button onClick={onEditAssessments}>
            <Icon name="plus" size={18} />
            {t('grades.focus.addAssessments')}
          </Button>
        }
      />
    );
  }

  // Exame só faz sentido quando a regra tem exame e ainda não há nota de exame gravada.
  const showExam =
    subject.rule.finalExamEnabled &&
    subject.finalExamScore === undefined &&
    result.examRequired !== null;

  return (
    <>
      <Highlight
        subject={subject}
        result={result}
        simulating={simulating}
        pendingNames={subject.assessments
          .filter((a) => a.score === undefined && parsed.whatIf[a.id] === undefined)
          .map((a) => a.name)}
      />

      {isSum && subject.result.sum?.scaleMismatch && (
        <ScaleMismatchNote planned={subject.result.sum.plannedPoints} scaleMax={max} />
      )}
      {isSum && (subject.result.sum?.missingMaxPoints ?? 0) > 0 && (
        <p role="note" className="flex items-start gap-2 text-sm text-warning">
          <Icon name="alert" size={18} className="mt-0.5 shrink-0" />
          <span>
            {t('grades.sum.missingMaxPoints', { count: subject.result.sum!.missingMaxPoints })}
          </span>
        </p>
      )}

      <div>
        <p className="mb-1 text-sm text-text-secondary">{t('grades.simulator.hint')}</p>
        <ul className="m-0 list-none p-0" aria-label={t('grades.card.assessments')}>
          {subject.assessments.map((a, i) => {
            const inputId = `whatif-${a.id}`;
            const errorId = `${inputId}-error`;
            const isInvalid = parsed.invalid.has(a.id);
            const itemMax = maxScoreOf(subject, a);
            // Na soma, o necessário é por avaliação (repartido); nas médias, igual em todas.
            const needed = isSum ? (result.sum?.requiredPerItem[i] ?? null) : result.requiredScore;
            const showNeeded = needed !== null && needed > 0 && needed <= itemMax;
            return (
              <li key={a.id} className="grade-row">
                <p className="m-0 min-w-0 text-sm">
                  <span className="font-semibold">{a.name}</span>{' '}
                  <span className="text-text-muted">
                    {isSum
                      ? a.maxPoints !== undefined
                        ? t('grades.sum.worth', { points: formatGrade(a.maxPoints, lang) })
                        : t('grades.sum.worthUnknown')
                      : subject.rule.method === 'WEIGHTED'
                        ? `${t('grades.assessments.weight')} ${formatNumber(a.weight, lang)}`
                        : null}
                    {a.bonus && ` · ${t('grades.assessments.bonus')}`}
                  </span>
                </p>
                {a.score !== undefined ? (
                  <p className="tabular m-0 inline-flex min-h-11 min-w-24 items-center justify-end gap-2 rounded-pill border border-border px-4 font-semibold">
                    {formatGrade(a.score, lang)}
                    <span className="sr-only">{t('grades.simulator.graded')}</span>
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
                      className={cn(
                        'input tabular w-36 text-right',
                        showNeeded && 'border-action/70 placeholder:text-action',
                      )}
                      placeholder={
                        showNeeded
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
            <li className="grade-row">
              <label htmlFor="whatif-exam" className="text-sm font-semibold">
                {t('grades.simulator.exam')}
              </label>
              <div className="flex flex-col items-end gap-1">
                <input
                  id="whatif-exam"
                  inputMode="decimal"
                  autoComplete="off"
                  className="input tabular w-36 text-right"
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
        {simulating && (
          <Button
            variant="secondary"
            className="mt-3"
            onClick={() => {
              setTexts({});
              setExamText('');
            }}
          >
            {t('grades.simulator.clear')}
          </Button>
        )}
      </div>

      {subject.result.complete && !simulating && (
        <p role="note" className="flex items-start gap-2 text-sm text-text-secondary">
          <Icon name="check" size={18} className="mt-0.5 shrink-0 text-action" />
          {t('grades.focus.allGraded')}
        </p>
      )}

      {scenarios.length > 0 && (
        <section aria-labelledby="grade-scenarios">
          <h3 id="grade-scenarios" className="label mb-3 text-text-muted">
            {t('grades.scenarios.title')}
          </h3>
          <ul className="grid-fit m-0 list-none p-0 [--fit-min:11rem]">
            {scenarios.map((sc) => (
              <li key={sc.key} className="card-inner flex flex-col gap-1 p-4">
                <p className="m-0 text-sm text-text-secondary">
                  {t(`grades.scenarios.${sc.key}`, { pct: sc.pct })}
                </p>
                <p
                  className="figure-sm m-0"
                  style={{ color: toneColor(standingTone(sc.result.standing)) }}
                >
                  {formatGrade(isSum ? (sc.result.sum?.total ?? null) : sc.result.finalGrade, lang)}
                </p>
                <p className="m-0 text-xs text-text-muted">
                  {t(`grades.bucket.${statusBucket(sc.result.standing)}`)}
                </p>
              </li>
            ))}
          </ul>
        </section>
      )}
    </>
  );
}

/** Destaque compacto: número principal (até ~44 px), mensagem, barra e situação. */
function Highlight({
  subject,
  result,
  simulating,
  pendingNames,
}: {
  subject: SubjectGrades;
  result: CalcResult;
  simulating: boolean;
  /** Avaliações ainda sem nota (nem real nem "e se"), para "na P2" quando só falta uma. */
  pendingNames: string[];
}) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const { rule } = subject;
  const isSum = rule.method === 'SUM';
  const figure = mainFigure(rule, result);
  const tone = standingTone(result.standing);
  const color = toneColor(tone);
  // Mesma mensagem da linha da Stack e do Cockpit, sobre o resultado (real ou simulado).
  const headline = gradeHeadline({ ...headlineSubjectOf(subject), pendingNames }, result, lang);
  const label = simulating ? t('grades.simulator.simulated') : t('grades.simulator.real');
  const figureLabel =
    figure.kind === 'sum'
      ? t('grades.focus.pointsSoFar')
      : result.complete
        ? t('grades.card.finalGrade')
        : t('grades.card.currentAverage');

  return (
    <section
      aria-live="polite"
      aria-label={label}
      className="card-inner grid gap-4 p-4 sm:p-5 md:grid-cols-[auto_minmax(0,1fr)] md:items-center"
    >
      <div className="flex min-w-0 flex-col gap-1">
        <p className="label m-0 text-text-muted">
          {figureLabel}
          {simulating && <span className="text-space-glow"> · {label}</span>}
        </p>
        <p className="m-0 flex flex-wrap items-baseline gap-x-2">
          <span className="figure" style={{ color: tone === 'neutral' ? undefined : color }}>
            {figure.kind === 'sum'
              ? formatGrade(figure.total, lang)
              : figure.kind === 'average'
                ? formatGrade(figure.value, lang)
                : '—'}
          </span>
          <span className="text-sm text-text-secondary">
            {t('grades.focus.toPass', { value: formatGrade(rule.passingGrade, lang) })}
          </span>
        </p>
      </div>

      <div className="flex min-w-0 flex-col gap-2">
        <div className="flex flex-wrap items-start justify-between gap-2">
          {headline ? (
            <p
              className="m-0 flex min-w-0 flex-1 basis-56 items-start gap-2 text-sm"
              style={{ color }}
            >
              <Icon name={standingIcon(result.standing)} size={18} className="mt-0.5 shrink-0" />
              <span>{t(`grades.headline.${headline.key}`, headline.values)}</span>
            </p>
          ) : (
            <span />
          )}
          <Chip tone={tone} className="shrink-0">
            <Icon name={standingIcon(result.standing)} size={14} />
            {isSum
              ? t(`grades.standingSum.${result.standing}`)
              : t(`grades.standing.${result.standing}`)}
          </Chip>
        </div>
        {isSum && result.sum ? (
          <ProgressBar
            value={result.sum.total}
            max={rule.passingGrade > 0 ? rule.passingGrade : 1}
            color={color}
            label={t('grades.sum.progressLabel', {
              total: formatGrade(result.sum.total, lang),
              passing: formatGrade(rule.passingGrade, lang),
            })}
          />
        ) : (
          <ProgressBar value={result.gradedWeightPct} label={t('grades.card.progress')} showValue />
        )}
        <dl className="m-0 flex flex-wrap gap-x-4 gap-y-1 text-xs text-text-muted">
          <div className="flex gap-1">
            <dt>{t(`grades.method.${rule.method}`)}</dt>
            <dd className="m-0">
              ·{' '}
              {t('grades.card.passing', {
                value: formatNumber(rule.passingGrade, lang),
                max: formatNumber(rule.scaleMax, lang),
              })}
            </dd>
          </div>
          {isSum && result.sum && result.sum.pointsToPass > 0 && (
            <Extra label={t('grades.sum.stillMissing')} value={result.sum.pointsToPass} />
          )}
          {isSum && result.sum && result.sum.pointsAvailable > 0 && (
            <Extra label={t('grades.sum.available')} value={result.sum.pointsAvailable} />
          )}
          {!isSum && result.requiredScore !== null && result.standing !== 'SECURED' && (
            <Extra
              label={t('grades.simulator.stillNeeds')}
              value={result.requiredScore}
              danger={result.requiredScore > rule.scaleMax}
            />
          )}
          {result.examRequired !== null && result.standing !== 'PASSED' && (
            <Extra
              label={t('grades.simulator.examNeeded')}
              value={result.examRequired}
              danger={result.examRequired > rule.scaleMax}
            />
          )}
        </dl>
        {result.standing === 'IMPOSSIBLE' && (
          <p className="m-0 text-sm text-danger">{t('grades.card.impossibleBody')}</p>
        )}
      </div>
    </section>
  );
}

function Extra({ label, value, danger }: { label: string; value: number; danger?: boolean }) {
  const { i18n } = useTranslation();
  return (
    <div className="flex gap-1">
      <dt>{label}:</dt>
      <dd className={cn('tabular m-0 font-semibold text-text', danger && 'text-danger')}>
        {formatGrade(value, i18n.language)}
      </dd>
    </div>
  );
}
