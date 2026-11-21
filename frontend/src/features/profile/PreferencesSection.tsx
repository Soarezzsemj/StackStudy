import { useMemo, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Card, ErrorState, LoadingState, SelectField, TextField } from '@/components/ui';
import { errorMessage, fieldErrors } from '@/features/auth/errorMessage';
import { SUPPORTED_LANGUAGES } from '@/i18n';
import type { Preferences } from '@/lib/api/account';
import { browserTimeZone } from '@/lib/api/deepwork';
import { GRADE_METHODS, type GradeMethod } from '@/lib/api/grades';
import {
  invalidNumberFields,
  timeZoneOptions,
  toPreferencesForm,
  toPreferencesPatch,
  type PreferencesForm,
} from './helpers';
import { usePreferences, useSavePreferences } from './queries';
import { SavedNote } from './SavedNote';

const numberProps = { type: 'number', inputMode: 'decimal', step: 'any' } as const;

/** Padrões que as outras telas usam: idioma, fuso, notas, pomodoro e flashcards. */
export function PreferencesSection() {
  const { t } = useTranslation();
  const prefs = usePreferences();
  // Fica aqui (e não no formulário) para o "Salvo" sobreviver ao formulário recarregar.
  const save = useSavePreferences();
  return (
    <Card
      title={t('profile.preferences.title')}
      aria-label={t('profile.preferences.title')}
      id="preferencias"
    >
      <p className="mb-4 text-sm text-text-secondary">{t('profile.preferences.hint')}</p>
      {prefs.isPending ? (
        <LoadingState />
      ) : prefs.isError ? (
        <ErrorState message={t('profile.loadError')} onRetry={() => void prefs.refetch()} />
      ) : (
        <PreferencesForm key={JSON.stringify(prefs.data)} prefs={prefs.data} save={save} />
      )}
    </Card>
  );
}

/** Nome do campo da API → campo do formulário (a meta semanal é em horas na tela). */
const API_FIELD: Partial<Record<string, keyof PreferencesForm>> = {
  weeklyGoalMinutes: 'weeklyGoalHours',
};

function PreferencesForm({
  prefs,
  save,
}: {
  prefs: Preferences;
  save: ReturnType<typeof useSavePreferences>;
}) {
  const { t } = useTranslation();
  const [form, setForm] = useState<PreferencesForm>(() => toPreferencesForm(prefs));
  const [localErrors, setLocalErrors] = useState<Partial<Record<keyof PreferencesForm, string>>>(
    {},
  );
  const zones = useMemo(timeZoneOptions, []);
  const serverErrors = useMemo(() => {
    const out: Partial<Record<keyof PreferencesForm, string>> = {};
    for (const [field, message] of Object.entries(fieldErrors(save.error))) {
      out[API_FIELD[field] ?? (field as keyof PreferencesForm)] = message;
    }
    return out;
  }, [save.error]);
  const errors = { ...serverErrors, ...localErrors };

  const set = (key: keyof PreferencesForm) => (e: { target: { value: string } }) => {
    save.reset();
    setLocalErrors((x) => ({ ...x, [key]: undefined }));
    setForm((f) => ({ ...f, [key]: e.target.value }));
  };
  const num = (key: keyof PreferencesForm, label: string, extra: Record<string, unknown> = {}) => (
    <TextField
      {...numberProps}
      {...extra}
      label={label}
      value={form[key]}
      error={errors[key]}
      onChange={set(key)}
    />
  );

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const invalid = invalidNumberFields(form);
    if (invalid.length > 0) {
      setLocalErrors(Object.fromEntries(invalid.map((k) => [k, t('profile.preferences.number')])));
      return;
    }
    setLocalErrors({});
    save.mutate(toPreferencesPatch(form));
  }

  return (
    <form noValidate onSubmit={onSubmit} className="flex flex-col gap-6">
      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="label mb-2 text-text-secondary">
          {t('profile.preferences.general')}
        </legend>
        <SelectField
          label={t('common.language')}
          value={form.locale}
          error={errors.locale}
          onChange={set('locale')}
        >
          {SUPPORTED_LANGUAGES.map((l) => (
            <option key={l} value={l}>
              {t(`common.languages.${l}`)}
            </option>
          ))}
        </SelectField>
        <SelectField
          label={t('profile.preferences.timeZone')}
          hint={t('profile.preferences.timeZoneHint')}
          value={form.timeZone}
          error={errors.timeZone}
          onChange={set('timeZone')}
        >
          <option value="">
            {t('profile.preferences.timeZoneAuto', { zone: browserTimeZone() })}
          </option>
          {zones.map((z) => (
            <option key={z} value={z}>
              {z.replace(/_/g, ' ')}
            </option>
          ))}
        </SelectField>
      </fieldset>

      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="label mb-2 text-text-secondary">
          {t('profile.preferences.grades')}
        </legend>
        <SelectField
          label={t('profile.preferences.gradeMethod')}
          value={form.gradeMethod}
          error={errors.gradeMethod}
          onChange={(e) => {
            save.reset();
            setForm((f) => ({ ...f, gradeMethod: e.target.value as GradeMethod }));
          }}
        >
          {GRADE_METHODS.map((m) => (
            <option key={m} value={m}>
              {t(`grades.method.${m}`)}
            </option>
          ))}
        </SelectField>
        {num('gradeScaleMax', t('profile.preferences.gradeScaleMax'), { min: 1, max: 100 })}
        {num('passingGrade', t('profile.preferences.passingGrade'), { min: 0, max: 100 })}
        {num('maxAbsencePct', t('profile.preferences.maxAbsencePct'), { min: 0, max: 100 })}
      </fieldset>

      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="label mb-2 text-text-secondary">
          {t('profile.preferences.deepWork')}
        </legend>
        {num('focusMinutes', t('profile.preferences.focusMinutes'), { min: 1, max: 180 })}
        {num('shortBreakMinutes', t('profile.preferences.shortBreakMinutes'), { min: 1, max: 60 })}
        {num('longBreakMinutes', t('profile.preferences.longBreakMinutes'), { min: 1, max: 120 })}
        {num('longBreakEvery', t('profile.preferences.longBreakEvery'), { min: 1, max: 12 })}
        {num('weeklyGoalHours', t('profile.preferences.weeklyGoalHours'), { min: 0, max: 168 })}
      </fieldset>

      <fieldset className="grid gap-4 sm:grid-cols-2">
        <legend className="label mb-2 text-text-secondary">
          {t('profile.preferences.flashcards')}
        </legend>
        {num('newCardsPerDay', t('profile.preferences.newCardsPerDay'), { min: 0, max: 1000 })}
        {num('reviewsPerDay', t('profile.preferences.reviewsPerDay'), { min: 0, max: 1000 })}
      </fieldset>

      <div className="flex flex-wrap items-center gap-3">
        <Button type="submit" disabled={save.isPending}>
          {save.isPending ? t('profile.saving') : t('profile.preferences.save')}
        </Button>
        <SavedNote show={save.isSuccess}>{t('profile.preferences.saved')}</SavedNote>
      </div>
      {(save.error || Object.values(localErrors).some(Boolean)) && (
        <ErrorState
          message={save.error ? errorMessage(t, save.error) : t('errors.VALIDATION_ERROR')}
        />
      )}
    </form>
  );
}
