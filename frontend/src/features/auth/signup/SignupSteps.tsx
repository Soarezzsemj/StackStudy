import { useState } from 'react';
import { Trans, useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Icon, TextField } from '@/components/ui';
import type { RoadmapSuggestion, SignupConfig } from '@/lib/api/auth';
import { API_URL } from '@/lib/api/config';
import { cn } from '@/lib/cn';
import { ChoiceGroup, Consent, Switch } from './SignupFields';
import {
  passwordStrength,
  RHYTHMS,
  STUDY_AREAS,
  suggestedGoal,
  type Field,
  type SignupDraft,
} from './signupLogic';

export interface StepProps {
  draft: SignupDraft;
  set: <K extends Field>(field: K, value: SignupDraft[K]) => void;
  /** Erro já traduzido, só para campos tocados (ou erro do servidor). */
  error: (field: Field) => string | undefined;
  touch: (field: Field) => void;
}

// ---------------------------------------------------------------------------
// 1. Conta
// ---------------------------------------------------------------------------

export function AccountStep({
  draft,
  set,
  error,
  touch,
  config,
}: StepProps & { config?: SignupConfig }) {
  const { t } = useTranslation();
  const [show, setShow] = useState(false);
  const strength = passwordStrength(draft.password);
  return (
    <div className="flex flex-col gap-5">
      {config?.googleEnabled && (
        <>
          {/* O fluxo do Google é todo no servidor (redireciona e volta). */}
          <a
            href={API_URL + '/auth/google/start'}
            className="flex min-h-11 items-center justify-center gap-2 rounded-pill border border-border-control px-5 font-semibold hover:border-text-secondary"
          >
            <Icon name="globe" size={18} />
            {t('signup.account.google')}
          </a>
          <p className="text-center text-xs text-text-muted">{t('signup.account.or')}</p>
        </>
      )}
      <TextField
        id="signup-email"
        label={t('signup.account.email')}
        type="email"
        autoComplete="email"
        value={draft.email}
        onChange={(e) => set('email', e.target.value)}
        onBlur={() => touch('email')}
        error={error('email')}
      />
      <div className="flex flex-col gap-2">
        <div className="relative">
          <TextField
            id="signup-password"
            label={t('signup.account.password')}
            type={show ? 'text' : 'password'}
            autoComplete="new-password"
            value={draft.password}
            onChange={(e) => set('password', e.target.value)}
            onBlur={() => touch('password')}
            error={error('password')}
            className="input pr-28"
          />
          <button
            type="button"
            aria-pressed={show}
            aria-controls="signup-password"
            onClick={() => setShow((v) => !v)}
            className="absolute right-1 top-[1.85rem] min-h-11 rounded-pill px-4 text-sm font-semibold text-text-secondary hover:text-text"
          >
            {show ? t('signup.account.hide') : t('signup.account.show')}
          </button>
        </div>
        <div aria-live="polite">
          <div className="flex gap-1" aria-hidden="true">
            {[1, 2, 3, 4].map((n) => (
              <span
                key={n}
                className={cn(
                  'h-1.5 flex-1 rounded-pill',
                  strength.level >= n
                    ? strength.level <= 1
                      ? 'bg-danger'
                      : strength.level === 2
                        ? 'bg-warning'
                        : 'bg-action'
                    : 'bg-border',
                )}
              />
            ))}
          </div>
          <p className="mt-2 text-xs text-text-secondary" data-testid="password-strength">
            {strength.level > 0 && (
              <span className="font-semibold">
                {t('signup.password.level', {
                  level: t(`signup.password.levels.${strength.level}`),
                })}{' '}
              </span>
            )}
            {t(strength.hint)}
          </p>
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// 2. Perfil
// ---------------------------------------------------------------------------

export function ProfileStep({
  draft,
  set,
  error,
  touch,
  suggestion,
  config,
  photoUrl,
  onPhoto,
}: StepProps & {
  suggestion?: RoadmapSuggestion | null;
  config?: SignupConfig;
  photoUrl?: string;
  onPhoto: (file: File | null) => void;
}) {
  const { t } = useTranslation();
  const [photoError, setPhotoError] = useState<string>();
  return (
    <div className="flex flex-col gap-5">
      {config?.profilePhotoEnabled && (
        <div className="flex items-center gap-4">
          {photoUrl ? (
            <img
              src={photoUrl}
              alt={t('signup.profile.photoPreview')}
              className="h-16 w-16 rounded-full object-cover"
            />
          ) : (
            <span
              aria-hidden="true"
              className="flex h-16 w-16 items-center justify-center rounded-full bg-space-deep text-xl font-bold"
            >
              ?
            </span>
          )}
          <label className="flex min-h-11 cursor-pointer items-center rounded-pill border border-border-control px-4 text-sm font-semibold">
            {t('signup.profile.photo')}
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp"
              className="sr-only"
              onChange={(e) => {
                const file = e.target.files?.[0] ?? null;
                if (file && file.size > 2 * 1024 * 1024) {
                  setPhotoError(t('signup.profile.photoTooBig'));
                  onPhoto(null);
                  return;
                }
                setPhotoError(undefined);
                onPhoto(file);
              }}
            />
          </label>
          {photoError && <p className="text-sm text-danger">{photoError}</p>}
        </div>
      )}
      <TextField
        id="signup-name"
        label={t('signup.profile.name')}
        autoComplete="name"
        maxLength={120}
        value={draft.name}
        onChange={(e) => set('name', e.target.value)}
        onBlur={() => touch('name')}
        error={error('name')}
      />
      <TextField
        id="signup-nickname"
        label={t('signup.profile.nickname')}
        hint={t('signup.profile.nicknameHint')}
        maxLength={24}
        autoComplete="nickname"
        value={draft.nickname}
        onChange={(e) => set('nickname', e.target.value)}
        onBlur={() => touch('nickname')}
        error={error('nickname')}
      />
      <ChoiceGroup
        id="signup-area"
        label={t('signup.profile.area')}
        options={STUDY_AREAS.map((a) => ({ value: a, label: t(`signup.areas.${a}`) }))}
        value={draft.area}
        onChange={(v) => {
          set('area', v);
          touch('area');
        }}
        error={error('area')}
      />
      <TextField
        id="signup-course"
        label={t('signup.profile.course')}
        hint={t('signup.profile.courseHint')}
        maxLength={120}
        value={draft.course}
        onChange={(e) => set('course', e.target.value)}
        onBlur={() => touch('course')}
        error={error('course')}
      />
      {suggestion && (
        <p
          role="status"
          className="flex items-center gap-2 rounded-inner border border-action/40 bg-action/5 px-4 py-3 text-sm"
        >
          <Icon name="roadmap" size={18} className="shrink-0 text-action" />
          {t('signup.profile.suggestion', { title: suggestion.title })}
        </p>
      )}
      <div className="grid gap-5 sm:grid-cols-2">
        <TextField
          id="signup-period"
          label={t('signup.profile.period')}
          hint={t('signup.profile.periodHint')}
          maxLength={40}
          value={draft.period}
          onChange={(e) => set('period', e.target.value)}
          onBlur={() => touch('period')}
          error={error('period')}
        />
        <TextField
          id="signup-institution"
          label={t('signup.profile.institution')}
          maxLength={120}
          autoComplete="organization"
          value={draft.institution}
          onChange={(e) => set('institution', e.target.value)}
          onBlur={() => touch('institution')}
          error={error('institution')}
        />
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// 3. Rotina
// ---------------------------------------------------------------------------

export function RoutineStep({
  draft,
  set,
  error,
  suggestion,
  config,
}: StepProps & { suggestion?: RoadmapSuggestion | null; config?: SignupConfig }) {
  const { t } = useTranslation();
  const range = suggestedGoal(draft.area, draft.period);
  const minAge = config?.minAge;
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <label htmlFor="signup-goal" className="label flex justify-between text-text-secondary">
          <span>{t('signup.routine.goal')}</span>
          <span className="tabular text-text">
            {t('signup.routine.goalValue', { count: draft.weeklyGoalHours })}
          </span>
        </label>
        <input
          id="signup-goal"
          type="range"
          min={1}
          max={60}
          step={1}
          value={draft.weeklyGoalHours}
          onChange={(e) => set('weeklyGoalHours', Number(e.target.value))}
          aria-valuetext={t('signup.routine.goalValue', { count: draft.weeklyGoalHours })}
          aria-describedby="signup-goal-hint"
          className="h-11 w-full accent-[var(--action)]"
        />
        <p id="signup-goal-hint" className="text-sm text-text-muted">
          {error('weeklyGoalHours') ?? t('signup.routine.goalHint', range)}
        </p>
      </div>

      <ChoiceGroup
        label={t('signup.routine.rhythm')}
        options={RHYTHMS.map((r) => ({
          value: r,
          label: t(`signup.rhythms.${r}.label`),
          description: t(`signup.rhythms.${r}.description`),
        }))}
        value={draft.focusRhythm}
        onChange={(v) => set('focusRhythm', v)}
      />

      <div className="flex flex-col gap-3">
        <Switch
          label={t('signup.routine.reminders')}
          description={t('signup.routine.remindersHint')}
          checked={draft.deadlineReminders}
          onChange={(v) => set('deadlineReminders', v)}
        />
        {suggestion && (
          <Switch
            label={t('signup.routine.followRoadmap', { title: suggestion.title })}
            description={t('signup.routine.followRoadmapHint')}
            checked={draft.followRoadmap}
            onChange={(v) => set('followRoadmap', v)}
          />
        )}
        <Switch
          label={t('signup.routine.rooms')}
          description={t('signup.routine.roomsHint')}
          checked={draft.roomsVisible}
          onChange={(v) => set('roomsVisible', v)}
        />
      </div>

      <div className="flex flex-col gap-2">
        <Consent
          id="signup-acceptTerms"
          checked={draft.acceptTerms}
          onChange={(v) => set('acceptTerms', v)}
          error={error('acceptTerms')}
        >
          <Trans
            i18nKey="signup.routine.terms"
            components={{
              // Nova aba: sair da página apagaria o rascunho (que só existe na memória).
              terms: (
                <Link
                  to="/terms"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="font-semibold text-action underline underline-offset-4"
                />
              ),
              privacy: (
                <Link
                  to="/privacy"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="font-semibold text-action underline underline-offset-4"
                />
              ),
            }}
          />
        </Consent>
        <Consent
          id="signup-confirmAge"
          checked={draft.confirmAge}
          onChange={(v) => set('confirmAge', v)}
          error={error('confirmAge')}
        >
          {minAge ? t('signup.routine.ageWithNumber', { age: minAge }) : t('signup.routine.age')}
        </Consent>
      </div>
    </div>
  );
}
