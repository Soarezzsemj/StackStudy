import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Dialog, ErrorState, SelectField, TextField } from '@/components/ui';
import { errorMessage, fieldErrors } from '@/features/auth/errorMessage';
import { GradingRulePicker } from '@/features/grades/GradingRulePicker';
import type { GradeMethod } from '@/lib/api/grades';
import {
  SUBJECT_COLORS,
  SUBJECT_STATUSES,
  type Semester,
  type Subject,
  type SubjectStatus,
} from '@/lib/api/stack';
import { cn } from '@/lib/cn';
import { colorKey } from './labels';
import { useDeleteSubject, useSaveSubject } from './queries';

interface SubjectDialogProps {
  subject?: Subject;
  /** Semestres onde a matéria pode ficar (não arquivados + o da própria matéria). */
  semesters: Semester[];
  defaultSemesterId?: string;
  /** Próxima cor da paleta, para matéria nova. */
  defaultColor: string;
  onClose: () => void;
}

/** Converte o texto do campo numérico (vazio = sem valor). */
function toNumber(value: string): number | undefined {
  if (value.trim() === '') return undefined;
  const n = Number(value);
  return Number.isFinite(n) ? n : undefined;
}

export function SubjectDialog({
  subject,
  semesters,
  defaultSemesterId,
  defaultColor,
  onClose,
}: SubjectDialogProps) {
  const { t } = useTranslation();
  const [name, setName] = useState(subject?.name ?? '');
  const [code, setCode] = useState(subject?.code ?? '');
  const [professor, setProfessor] = useState(subject?.professor ?? '');
  const [credits, setCredits] = useState(subject ? String(subject.credits) : '');
  const [workload, setWorkload] = useState(
    subject?.workloadHours !== undefined ? String(subject.workloadHours) : '',
  );
  const [color, setColor] = useState(subject?.color ?? defaultColor);
  const [status, setStatus] = useState<SubjectStatus>(subject?.status ?? 'IN_PROGRESS');
  // Regra de nota: '' = usa a preferência do usuário (Grade Predictor).
  const [gradingRule, setGradingRule] = useState<GradeMethod | ''>(subject?.gradingRule ?? '');
  const [semesterId, setSemesterId] = useState(
    subject?.semesterId ?? defaultSemesterId ?? semesters[0]?.id ?? '',
  );
  const [nameError, setNameError] = useState<string>();
  const [confirmDelete, setConfirmDelete] = useState(false);
  const save = useSaveSubject();
  const remove = useDeleteSubject();
  const errors = fieldErrors(save.error);

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!name.trim()) {
      setNameError(t('stack.semester.required'));
      return;
    }
    setNameError(undefined);
    save.mutate(
      {
        id: subject?.id,
        input: {
          semesterId,
          name: name.trim(),
          code: code.trim() || undefined,
          professor: professor.trim() || undefined,
          credits: toNumber(credits),
          workloadHours: toNumber(workload) ?? null,
          color,
          status,
          gradingRule: gradingRule || null,
        },
      },
      { onSuccess: onClose },
    );
  }

  function onDelete() {
    if (!subject) return;
    if (!confirmDelete) {
      setConfirmDelete(true);
      return;
    }
    remove.mutate(subject.id, { onSuccess: onClose });
  }

  const formId = 'subject-form';
  return (
    <Dialog
      title={subject ? t('stack.subjects.edit') : t('stack.subjects.new')}
      onClose={onClose}
      footer={
        <>
          {subject && (
            <Button
              variant={confirmDelete ? 'primary' : 'ghost'}
              className="mr-auto"
              onClick={onDelete}
              disabled={remove.isPending}
              aria-describedby={confirmDelete ? 'subject-delete-warning' : undefined}
            >
              {confirmDelete ? t('common.confirmDelete') : t('common.delete')}
            </Button>
          )}
          <Button variant="secondary" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form={formId} disabled={save.isPending || !semesterId}>
            {t('common.save')}
          </Button>
        </>
      }
    >
      <form id={formId} noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
        <TextField
          label={t('stack.subjects.fields.name')}
          placeholder={t('stack.subjects.fields.namePlaceholder')}
          value={name}
          maxLength={120}
          required
          data-autofocus
          error={nameError ?? errors.name}
          onChange={(e) => setName(e.target.value)}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label={t('stack.subjects.fields.code')}
            placeholder={t('stack.subjects.fields.codePlaceholder')}
            value={code}
            maxLength={30}
            error={errors.code}
            onChange={(e) => setCode(e.target.value)}
          />
          <TextField
            label={t('stack.subjects.fields.professor')}
            value={professor}
            maxLength={120}
            error={errors.professor}
            onChange={(e) => setProfessor(e.target.value)}
          />
          <TextField
            type="number"
            inputMode="numeric"
            min={0}
            max={60}
            label={t('stack.subjects.fields.credits')}
            value={credits}
            error={errors.credits}
            onChange={(e) => setCredits(e.target.value)}
          />
          <TextField
            type="number"
            inputMode="numeric"
            min={0}
            max={2000}
            label={t('stack.subjects.fields.workloadHours')}
            value={workload}
            error={errors.workloadHours}
            onChange={(e) => setWorkload(e.target.value)}
          />
          <SelectField
            label={t('stack.subjects.fields.status')}
            value={status}
            onChange={(e) => setStatus(e.target.value as SubjectStatus)}
          >
            {SUBJECT_STATUSES.map((v) => (
              <option key={v} value={v}>
                {t(`stack.subjects.status.${v}`)}
              </option>
            ))}
          </SelectField>
          <SelectField
            label={t('stack.subjects.fields.semester')}
            value={semesterId}
            error={errors.semesterId}
            onChange={(e) => setSemesterId(e.target.value)}
          >
            {semesters.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
              </option>
            ))}
          </SelectField>
        </div>

        <GradingRulePicker
          value={gradingRule}
          inheritLabel={t('stack.subjects.fields.gradingRuleInherit')}
          onChange={setGradingRule}
        />

        <fieldset className="flex flex-col gap-2">
          <legend className="field-label mb-2 block">{t('stack.subjects.fields.color')}</legend>
          <div className="flex flex-wrap gap-2">
            {SUBJECT_COLORS.map((c) => (
              <label
                key={c}
                className={cn(
                  'flex h-11 w-11 cursor-pointer items-center justify-center rounded-full border-2 has-[:focus-visible]:outline has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-action',
                  color === c ? 'border-text' : 'border-transparent',
                )}
              >
                <input
                  type="radio"
                  name="subject-color"
                  value={c}
                  checked={color === c}
                  onChange={() => setColor(c)}
                  className="sr-only"
                  aria-label={t(colorKey(c))}
                />
                <span
                  aria-hidden="true"
                  className="h-7 w-7 rounded-full"
                  style={{ backgroundColor: c }}
                />
              </label>
            ))}
          </div>
          {errors.color && <p className="text-sm text-danger">{errors.color}</p>}
        </fieldset>

        {confirmDelete && (
          <p id="subject-delete-warning" className="text-sm text-warning">
            {t('stack.subjects.deleteWarning')}
          </p>
        )}
        {(save.error || remove.error) && (
          <ErrorState message={errorMessage(t, save.error ?? remove.error)} />
        )}
      </form>
    </Dialog>
  );
}
