import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Button,
  Dialog,
  EmptyState,
  ErrorState,
  Icon,
  LoadingState,
  SelectField,
  TextField,
} from '@/components/ui';
import { errorMessage, fieldErrors } from '@/features/auth/errorMessage';
import { todayInput } from '@/features/stack/due';
import {
  GPA_SCALES,
  GPA_WEIGHTS,
  type GpaScale,
  type GpaWeight,
  type GradeMethod,
  type GradeSettings,
  type SubjectGrades,
} from '@/lib/api/grades';
import { cn } from '@/lib/cn';
import { formatGrade, formatNumber } from './format';
import { numberOrNull, toInput } from './formValues';
import { GradingRulePicker } from './GradingRulePicker';
import { plannedPointsOf } from './simulation';
import {
  useAbsences,
  useCreateAbsence,
  useDeleteAbsence,
  useSaveAssessments,
  useSaveGradeSettings,
  useSaveRule,
} from './queries';

/** Campo numérico com 2 casas (o navegador manda sempre com ponto). */
const decimalProps = { type: 'number', inputMode: 'decimal', step: 0.01, min: 0 } as const;

// ---------------------------------------------------------------------------
// preferências
// ---------------------------------------------------------------------------

export function GradeSettingsDialog({
  settings,
  onClose,
}: {
  settings: GradeSettings;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const [method, setMethod] = useState<GradeMethod>(settings.method);
  const [gpaScale, setGpaScale] = useState<GpaScale>(settings.gpaScale);
  const [gpaWeight, setGpaWeight] = useState<GpaWeight>(settings.gpaWeight);
  const [examEnabled, setExamEnabled] = useState(settings.finalExamEnabled);
  const [roundBefore, setRoundBefore] = useState(settings.roundBeforeCompare);
  const [roundingScale, setRoundingScale] = useState(settings.roundingScale);
  const [form, setForm] = useState({
    scaleMax: toInput(settings.scaleMax),
    passingGrade: toInput(settings.passingGrade),
    maxAbsencePct: toInput(settings.maxAbsencePct),
    finalExamMinAverage: toInput(settings.finalExamMinAverage),
    finalExamAverageWeight: toInput(settings.finalExamAverageWeight),
    finalExamPassingGrade: toInput(settings.finalExamPassingGrade),
  });
  const save = useSaveGradeSettings();
  const errors = fieldErrors(save.error);
  const field = (key: keyof typeof form) => ({
    value: form[key],
    error: errors[key],
    onChange: (e: { target: { value: string } }) =>
      setForm((f) => ({ ...f, [key]: e.target.value })),
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    save.mutate(
      {
        method,
        scaleMax: numberOrNull(form.scaleMax) as number,
        passingGrade: numberOrNull(form.passingGrade) as number,
        maxAbsencePct: numberOrNull(form.maxAbsencePct) as number,
        finalExamEnabled: examEnabled,
        finalExamMinAverage: numberOrNull(form.finalExamMinAverage) as number,
        finalExamAverageWeight: numberOrNull(form.finalExamAverageWeight) as number,
        finalExamPassingGrade: numberOrNull(form.finalExamPassingGrade) as number,
        gpaScale,
        gpaWeight,
        roundBeforeCompare: roundBefore,
        roundingScale,
      },
      { onSuccess: onClose },
    );
  }

  function pickMethod(next: GradeMethod) {
    setMethod(next);
    // Na soma o comum é aprovar com 7: sugere se a mínima ainda é o padrão das médias (6).
    if (next === 'SUM' && numberOrNull(form.passingGrade) === 6) {
      setForm((f) => ({ ...f, passingGrade: '7' }));
    }
  }

  return (
    <Dialog
      title={t('grades.settings.title')}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="grade-settings-form" disabled={save.isPending}>
            {t('common.save')}
          </Button>
        </>
      }
    >
      <form id="grade-settings-form" noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
        <p className="text-sm text-text-secondary">{t('grades.settings.hint')}</p>
        <GradingRulePicker
          value={method}
          focusFirst
          onChange={(m) => {
            if (m) pickMethod(m);
          }}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            {...decimalProps}
            min={1}
            max={100}
            label={t('grades.settings.scaleMax')}
            {...field('scaleMax')}
          />
          <TextField
            {...decimalProps}
            max={100}
            label={t('grades.settings.passingGrade')}
            {...field('passingGrade')}
          />
          <TextField
            {...decimalProps}
            max={100}
            label={t('grades.settings.maxAbsencePct')}
            {...field('maxAbsencePct')}
          />
        </div>

        {method === 'SUM' && (
          <fieldset className="flex flex-col gap-4">
            <legend className="label mb-2 text-text-secondary">{t('grades.sum.rounding')}</legend>
            <label className="flex min-h-11 cursor-pointer items-center gap-3 text-sm">
              <input
                type="checkbox"
                className="h-5 w-5 accent-[var(--action)]"
                checked={roundBefore}
                onChange={(e) => setRoundBefore(e.target.checked)}
              />
              {t('grades.sum.roundBeforeCompare')}
            </label>
            {roundBefore && (
              <SelectField
                label={t('grades.sum.roundingScale')}
                value={String(roundingScale)}
                onChange={(e) => setRoundingScale(Number(e.target.value))}
              >
                {[0, 1, 2].map((d) => (
                  <option key={d} value={d}>
                    {t(`grades.sum.digits.${d}`)}
                  </option>
                ))}
              </SelectField>
            )}
            <p className="text-xs text-text-muted">{t('grades.sum.roundingHint')}</p>
          </fieldset>
        )}

        <fieldset className="flex flex-col gap-4">
          <legend className="label mb-2 text-text-secondary">
            {t('grades.settings.finalExam')}
          </legend>
          <label className="flex min-h-11 cursor-pointer items-center gap-3 text-sm">
            <input
              type="checkbox"
              className="h-5 w-5 accent-[var(--action)]"
              checked={examEnabled}
              onChange={(e) => setExamEnabled(e.target.checked)}
            />
            {t('grades.settings.finalExamEnabled')}
          </label>
          {examEnabled && (
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField
                {...decimalProps}
                label={t('grades.settings.finalExamMinAverage')}
                {...field('finalExamMinAverage')}
              />
              <TextField
                {...decimalProps}
                label={t('grades.settings.finalExamPassingGrade')}
                {...field('finalExamPassingGrade')}
              />
              <TextField
                {...decimalProps}
                max={99.99}
                label={t('grades.settings.finalExamAverageWeight')}
                hint={t('grades.settings.finalExamAverageWeightHint')}
                {...field('finalExamAverageWeight')}
              />
            </div>
          )}
        </fieldset>

        <fieldset className="grid gap-4 sm:grid-cols-2">
          <legend className="label mb-2 text-text-secondary">{t('grades.settings.gpa')}</legend>
          <SelectField
            label={t('grades.settings.gpaScale')}
            value={gpaScale}
            onChange={(e) => setGpaScale(e.target.value as GpaScale)}
          >
            {GPA_SCALES.map((s) => (
              <option key={s} value={s}>
                {t(`grades.gpaScale.${s}`)}
              </option>
            ))}
          </SelectField>
          <SelectField
            label={t('grades.settings.gpaWeight')}
            value={gpaWeight}
            onChange={(e) => setGpaWeight(e.target.value as GpaWeight)}
          >
            {GPA_WEIGHTS.map((w) => (
              <option key={w} value={w}>
                {t(`grades.gpaWeight.${w}`)}
              </option>
            ))}
          </SelectField>
        </fieldset>
        {save.error && <ErrorState message={errorMessage(t, save.error)} />}
      </form>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// regra da matéria
// ---------------------------------------------------------------------------

export function RuleDialog({
  subject,
  settings,
  onClose,
}: {
  subject: SubjectGrades;
  settings: GradeSettings;
  onClose: () => void;
}) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const o = subject.overrides;
  const [method, setMethod] = useState<GradeMethod | ''>(o.method ?? '');
  const [examEnabled, setExamEnabled] = useState<'' | 'yes' | 'no'>(
    o.finalExamEnabled === undefined ? '' : o.finalExamEnabled ? 'yes' : 'no',
  );
  const [roundBefore, setRoundBefore] = useState<'' | 'yes' | 'no'>(
    o.roundBeforeCompare === undefined ? '' : o.roundBeforeCompare ? 'yes' : 'no',
  );
  const [roundingScale, setRoundingScale] = useState<string>(toInput(o.roundingScale));
  const [form, setForm] = useState({
    pointsCap: toInput(o.pointsCap),
    scaleMax: toInput(o.scaleMax),
    passingGrade: toInput(o.passingGrade),
    maxAbsencePct: toInput(o.maxAbsencePct),
    totalClasses: toInput(o.totalClasses),
    finalExamMinAverage: toInput(o.finalExamMinAverage),
    finalExamAverageWeight: toInput(o.finalExamAverageWeight),
    finalExamPassingGrade: toInput(o.finalExamPassingGrade),
    finalExamScore: toInput(subject.finalExamScore),
  });
  const save = useSaveRule();
  const errors = fieldErrors(save.error);
  const field = (key: keyof typeof form, fallback?: number) => ({
    value: form[key],
    error: errors[key],
    placeholder:
      fallback === undefined
        ? undefined
        : t('grades.rule.placeholder', { value: formatNumber(fallback, lang) }),
    onChange: (e: { target: { value: string } }) =>
      setForm((f) => ({ ...f, [key]: e.target.value })),
  });
  const examOn = examEnabled === '' ? settings.finalExamEnabled : examEnabled === 'yes';
  const effectiveMethod = method || settings.method;
  const roundOn = roundBefore === '' ? settings.roundBeforeCompare : roundBefore === 'yes';
  const inheritYesNo = (on: boolean) =>
    t('grades.rule.inherit', { value: on ? t('grades.rule.yes') : t('grades.rule.no') });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    save.mutate(
      {
        subjectId: subject.subjectId,
        input: {
          method: method || null,
          scaleMax: numberOrNull(form.scaleMax),
          passingGrade: numberOrNull(form.passingGrade),
          maxAbsencePct: numberOrNull(form.maxAbsencePct),
          totalClasses: numberOrNull(form.totalClasses),
          finalExamEnabled: examEnabled === '' ? null : examEnabled === 'yes',
          finalExamMinAverage: numberOrNull(form.finalExamMinAverage),
          finalExamAverageWeight: numberOrNull(form.finalExamAverageWeight),
          finalExamPassingGrade: numberOrNull(form.finalExamPassingGrade),
          finalExamScore: examOn ? numberOrNull(form.finalExamScore) : null,
          roundBeforeCompare: roundBefore === '' ? null : roundBefore === 'yes',
          roundingScale: numberOrNull(roundingScale),
          pointsCap: numberOrNull(form.pointsCap),
        },
      },
      { onSuccess: onClose },
    );
  }

  return (
    <Dialog
      title={t('grades.rule.title', { name: subject.name })}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="grade-rule-form" disabled={save.isPending}>
            {t('common.save')}
          </Button>
        </>
      }
    >
      <form id="grade-rule-form" noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
        <p className="text-sm text-text-secondary">{t('grades.rule.hint')}</p>
        <GradingRulePicker
          value={method}
          focusFirst
          inheritLabel={t('grades.rule.inherit', {
            value: t(`grades.method.${settings.method}`),
          })}
          onChange={setMethod}
        />
        {effectiveMethod !== subject.rule.method && subject.assessments.length > 0 && (
          <p className="text-sm text-warning" role="note">
            {t('grades.rule.switchHint')}
          </p>
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            {...decimalProps}
            min={1}
            max={100}
            label={t('grades.settings.scaleMax')}
            {...field('scaleMax', settings.scaleMax)}
          />
          <TextField
            {...decimalProps}
            max={100}
            label={t('grades.settings.passingGrade')}
            {...field('passingGrade', settings.passingGrade)}
          />
          <TextField
            {...decimalProps}
            max={100}
            label={t('grades.settings.maxAbsencePct')}
            {...field('maxAbsencePct', settings.maxAbsencePct)}
          />
          <TextField
            type="number"
            inputMode="numeric"
            min={1}
            step={1}
            label={t('grades.rule.totalClasses')}
            hint={t('grades.rule.totalClassesHint')}
            {...field('totalClasses', subject.workloadHours)}
          />
        </div>

        {effectiveMethod === 'SUM' && (
          <fieldset className="flex flex-col gap-4">
            <legend className="label mb-2 text-text-secondary">{t('grades.sum.rounding')}</legend>
            <div className="grid gap-4 sm:grid-cols-2">
              <SelectField
                label={t('grades.sum.roundBeforeCompare')}
                value={roundBefore}
                error={errors.roundBeforeCompare}
                onChange={(e) => setRoundBefore(e.target.value as '' | 'yes' | 'no')}
              >
                <option value="">{inheritYesNo(settings.roundBeforeCompare)}</option>
                <option value="yes">{t('grades.rule.yes')}</option>
                <option value="no">{t('grades.rule.no')}</option>
              </SelectField>
              {roundOn && (
                <SelectField
                  label={t('grades.sum.roundingScale')}
                  value={roundingScale}
                  error={errors.roundingScale}
                  onChange={(e) => setRoundingScale(e.target.value)}
                >
                  <option value="">
                    {t('grades.rule.inherit', {
                      value: t(`grades.sum.digits.${settings.roundingScale}`),
                    })}
                  </option>
                  {[0, 1, 2].map((d) => (
                    <option key={d} value={d}>
                      {t(`grades.sum.digits.${d}`)}
                    </option>
                  ))}
                </SelectField>
              )}
              <TextField
                {...decimalProps}
                min={0.01}
                max={100}
                label={t('grades.sum.pointsCap')}
                hint={t('grades.sum.pointsCapHint')}
                {...field('pointsCap')}
              />
            </div>
            <p className="text-xs text-text-muted">{t('grades.sum.roundingHint')}</p>
          </fieldset>
        )}

        <fieldset className="flex flex-col gap-4">
          <legend className="label mb-2 text-text-secondary">
            {t('grades.settings.finalExam')}
          </legend>
          <SelectField
            label={t('grades.settings.finalExamEnabled')}
            value={examEnabled}
            onChange={(e) => setExamEnabled(e.target.value as '' | 'yes' | 'no')}
          >
            <option value="">
              {t('grades.rule.inherit', {
                value: settings.finalExamEnabled ? t('grades.rule.yes') : t('grades.rule.no'),
              })}
            </option>
            <option value="yes">{t('grades.rule.yes')}</option>
            <option value="no">{t('grades.rule.no')}</option>
          </SelectField>
          {examOn && (
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField
                {...decimalProps}
                label={t('grades.settings.finalExamMinAverage')}
                {...field('finalExamMinAverage', settings.finalExamMinAverage)}
              />
              <TextField
                {...decimalProps}
                label={t('grades.settings.finalExamPassingGrade')}
                {...field('finalExamPassingGrade', settings.finalExamPassingGrade)}
              />
              <TextField
                {...decimalProps}
                max={99.99}
                label={t('grades.settings.finalExamAverageWeight')}
                hint={t('grades.settings.finalExamAverageWeightHint')}
                {...field('finalExamAverageWeight', settings.finalExamAverageWeight)}
              />
              <TextField
                {...decimalProps}
                label={t('grades.rule.finalExamScore')}
                {...field('finalExamScore')}
              />
            </div>
          )}
        </fieldset>
        {save.error && <ErrorState message={errorMessage(t, save.error)} />}
      </form>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// avaliações
// ---------------------------------------------------------------------------

interface Row {
  key: string;
  id?: string;
  name: string;
  weight: string;
  score: string;
  /** Quanto vale (soma de pontos). */
  maxPoints: string;
  bonus: boolean;
}

let rowSeq = 0;
const newKey = () => `new-${++rowSeq}`;

export function AssessmentsDialog({
  subject,
  onClose,
}: {
  subject: SubjectGrades;
  onClose: () => void;
}) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const [rows, setRows] = useState<Row[]>(() =>
    subject.assessments.map((a) => ({
      key: a.id,
      id: a.id,
      name: a.name,
      weight: toInput(a.weight),
      score: toInput(a.score),
      maxPoints: toInput(a.maxPoints),
      bonus: a.bonus ?? false,
    })),
  );
  const save = useSaveAssessments();
  const errors = fieldErrors(save.error);
  const method = subject.rule.method;
  const arithmetic = method === 'ARITHMETIC';
  const sum = method === 'SUM';
  const totalWeight = rows.reduce((total, r) => total + (Number(r.weight) || 0), 0);
  // Aviso "não fecha a escala": vem da própria calculadora, sobre o que está no formulário.
  const planned = sum
    ? plannedPointsOf(
        subject.rule,
        rows.map((r) => ({ maxPoints: numberOrNull(r.maxPoints), bonus: r.bonus })),
      )
    : null;

  function update(key: string, patch: Partial<Row>) {
    setRows((list) => list.map((r) => (r.key === key ? { ...r, ...patch } : r)));
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    save.mutate(
      {
        subjectId: subject.subjectId,
        items: rows.map((r) => {
          if (sum) {
            // Sem peso na soma: o backend mantém o que já estava.
            return {
              id: r.id,
              name: r.name,
              weight: null,
              score: numberOrNull(r.score),
              maxPoints: numberOrNull(r.maxPoints),
              bonus: r.bonus,
            };
          }
          return {
            id: r.id,
            name: r.name,
            weight: arithmetic ? (numberOrNull(r.weight) ?? 1) : numberOrNull(r.weight),
            score: numberOrNull(r.score),
          };
        }),
      },
      { onSuccess: onClose },
    );
  }

  const hint = sum
    ? t('grades.assessments.sumHint')
    : arithmetic
      ? t('grades.assessments.arithmeticHint')
      : t('grades.assessments.weightHint');

  return (
    <Dialog
      title={t('grades.assessments.title', { name: subject.name })}
      onClose={onClose}
      className="max-w-2xl"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="assessments-form" disabled={save.isPending}>
            {t('common.save')}
          </Button>
        </>
      }
    >
      <form id="assessments-form" noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
        <p className="text-sm text-text-secondary">{hint}</p>
        {rows.length === 0 && <EmptyState title={t('grades.assessments.empty')} />}
        <ol className="m-0 flex list-none flex-col gap-3 p-0">
          {rows.map((r, i) => {
            const maxPoints = numberOrNull(r.maxPoints);
            return (
              <li key={r.key}>
                <fieldset
                  className={cn(
                    'card-inner grid items-start gap-3 p-3',
                    sum
                      ? 'sm:grid-cols-[1fr_112px_112px_auto_auto]'
                      : 'sm:grid-cols-[1fr_96px_112px_auto]',
                  )}
                >
                  <legend className="sr-only">{t('grades.assessments.row', { n: i + 1 })}</legend>
                  <TextField
                    label={t('grades.assessments.name')}
                    value={r.name}
                    maxLength={80}
                    placeholder={t('grades.assessments.namePlaceholder')}
                    error={errors[`items[${i}].name`]}
                    data-autofocus={i === 0 ? true : undefined}
                    onChange={(e) => update(r.key, { name: e.target.value })}
                  />
                  {sum ? (
                    <TextField
                      {...decimalProps}
                      min={0.01}
                      max={subject.rule.scaleMax}
                      label={t('grades.assessments.maxPoints')}
                      value={r.maxPoints}
                      error={errors[`items[${i}].maxPoints`]}
                      onChange={(e) => update(r.key, { maxPoints: e.target.value })}
                    />
                  ) : (
                    <TextField
                      {...decimalProps}
                      min={0.01}
                      label={t('grades.assessments.weight')}
                      value={r.weight}
                      disabled={arithmetic}
                      error={errors[`items[${i}].weight`]}
                      onChange={(e) => update(r.key, { weight: e.target.value })}
                    />
                  )}
                  <TextField
                    {...decimalProps}
                    max={sum ? (maxPoints ?? subject.rule.scaleMax) : subject.rule.scaleMax}
                    label={
                      sum ? t('grades.assessments.scoreObtained') : t('grades.assessments.score')
                    }
                    value={r.score}
                    placeholder={t('grades.assessments.scorePlaceholder')}
                    error={errors[`items[${i}].score`]}
                    onChange={(e) => update(r.key, { score: e.target.value })}
                  />
                  {sum && (
                    <label className="flex min-h-11 cursor-pointer items-center gap-2 text-sm sm:mt-7">
                      <input
                        type="checkbox"
                        className="h-5 w-5 accent-[var(--action)]"
                        checked={r.bonus}
                        onChange={(e) => update(r.key, { bonus: e.target.checked })}
                      />
                      {t('grades.assessments.bonus')}
                    </label>
                  )}
                  <Button
                    variant="ghost"
                    iconOnly
                    className="sm:mt-7"
                    aria-label={t('grades.assessments.remove', {
                      name: r.name || t('grades.assessments.untitled', { n: i + 1 }),
                    })}
                    onClick={() => setRows((list) => list.filter((x) => x.key !== r.key))}
                  >
                    <Icon name="trash" size={18} />
                  </Button>
                </fieldset>
              </li>
            );
          })}
        </ol>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <Button
            variant="secondary"
            disabled={rows.length >= 30}
            onClick={() =>
              setRows((list) => [
                ...list,
                { key: newKey(), name: '', weight: '1', score: '', maxPoints: '', bonus: false },
              ])
            }
          >
            <Icon name="plus" size={18} />
            {t('grades.assessments.add')}
          </Button>
          {!arithmetic && !sum && rows.length > 0 && (
            <p className="tabular text-sm text-text-muted">
              {t('grades.assessments.totalWeight', { total: formatNumber(totalWeight, lang) })}
            </p>
          )}
          {sum && planned !== null && rows.length > 0 && (
            <p className="tabular text-sm text-text-muted">
              {t('grades.sum.plannedTotal', {
                total: formatGrade(planned, lang),
                max: formatGrade(subject.rule.scaleMax, lang),
              })}
            </p>
          )}
        </div>
        {sum && planned !== null && rows.length > 0 && planned !== subject.rule.scaleMax && (
          <ScaleMismatchNote planned={planned} scaleMax={subject.rule.scaleMax} />
        )}
        {save.error && <ErrorState message={errorMessage(t, save.error)} />}
      </form>
    </Dialog>
  );
}

/** Aviso (não bloqueia) de que as avaliações não fecham o máximo da escala. */
export function ScaleMismatchNote({ planned, scaleMax }: { planned: number; scaleMax: number }) {
  const { t, i18n } = useTranslation();
  return (
    <p role="note" className="flex items-start gap-2 text-sm text-warning">
      <Icon name="alert" size={18} className="mt-0.5 shrink-0" />
      <span>
        {t('grades.sum.mismatch', {
          total: formatGrade(planned, i18n.language),
          max: formatGrade(scaleMax, i18n.language),
        })}
      </span>
    </p>
  );
}

// ---------------------------------------------------------------------------
// faltas
// ---------------------------------------------------------------------------

export function AbsenceDialog({
  subjects,
  defaultSubjectId,
  onClose,
}: {
  subjects: SubjectGrades[];
  defaultSubjectId?: string;
  onClose: () => void;
}) {
  const { t, i18n } = useTranslation();
  const [subjectId, setSubjectId] = useState(defaultSubjectId ?? subjects[0]?.subjectId ?? '');
  const [date, setDate] = useState(todayInput());
  const [count, setCount] = useState('2');
  const create = useCreateAbsence();
  const remove = useDeleteAbsence();
  const list = useAbsences(subjectId || undefined);
  const errors = fieldErrors(create.error);
  const dateFormat = new Intl.DateTimeFormat(i18n.language, {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC',
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    create.mutate({ subjectId, input: { date, count: Number(count) } });
  }

  return (
    <Dialog
      title={t('grades.absence.title')}
      onClose={onClose}
      footer={
        <Button variant="secondary" onClick={onClose}>
          {t('common.close')}
        </Button>
      }
    >
      <form noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
        <SelectField
          label={t('grades.absence.subject')}
          value={subjectId}
          data-autofocus
          onChange={(e) => {
            setSubjectId(e.target.value);
            create.reset();
          }}
        >
          {subjects.map((s) => (
            <option key={s.subjectId} value={s.subjectId}>
              {s.name}
            </option>
          ))}
        </SelectField>
        <div className="grid gap-4 sm:grid-cols-[1fr_140px_auto] sm:items-end">
          <TextField
            type="date"
            label={t('grades.absence.date')}
            value={date}
            error={errors.date}
            onChange={(e) => setDate(e.target.value)}
          />
          <TextField
            type="number"
            inputMode="numeric"
            min={1}
            max={24}
            step={1}
            label={t('grades.absence.count')}
            value={count}
            error={errors.count}
            onChange={(e) => setCount(e.target.value)}
          />
          <Button type="submit" disabled={create.isPending || !subjectId}>
            {t('grades.absence.add')}
          </Button>
        </div>
        {create.error && <ErrorState message={errorMessage(t, create.error)} />}
      </form>

      <section className="mt-6" aria-labelledby="absence-list-title">
        <h3 id="absence-list-title" className="label mb-3 text-text-muted">
          {t('grades.absence.list')}
        </h3>
        {list.isPending ? (
          <LoadingState />
        ) : list.isError ? (
          <ErrorState message={t('grades.loadError')} onRetry={() => void list.refetch()} />
        ) : list.data.length === 0 ? (
          <p className="text-sm text-text-muted">{t('grades.absence.empty')}</p>
        ) : (
          <ul className="m-0 flex max-h-64 list-none flex-col gap-1 overflow-y-auto p-0">
            {list.data.map((a) => {
              const label = dateFormat.format(new Date(`${a.date}T00:00:00Z`));
              return (
                <li
                  key={a.id}
                  className="flex items-center justify-between gap-3 rounded-inner px-3 py-1 hover:bg-surface-raised"
                >
                  <span className="text-sm">
                    <span className="tabular">{label}</span>
                    <span className="ml-2 text-text-muted">
                      {t('grades.absence.countValue', { count: a.count })}
                    </span>
                  </span>
                  <Button
                    variant="ghost"
                    iconOnly
                    disabled={remove.isPending}
                    aria-label={t('grades.absence.delete', { date: label })}
                    onClick={() => remove.mutate(a.id)}
                  >
                    <Icon name="trash" size={18} />
                  </Button>
                </li>
              );
            })}
          </ul>
        )}
        {remove.error && <ErrorState className="mt-3" message={errorMessage(t, remove.error)} />}
      </section>
    </Dialog>
  );
}
