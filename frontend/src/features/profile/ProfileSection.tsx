import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Avatar, Button, Card, ErrorState, TextAreaField, TextField } from '@/components/ui';
import { errorMessage, fieldErrors } from '@/features/auth/errorMessage';
import { colorKey } from '@/features/stack/labels';
import { SUBJECT_COLORS } from '@/lib/api/stack';
import type { User } from '@/lib/api/types';
import { cn } from '@/lib/cn';
import { useSaveProfile } from './queries';
import { SavedNote } from './SavedNote';

/** Nome de exibição, curso, instituição, período, bio e cor do avatar de iniciais. */
export function ProfileSection({ user }: { user: User }) {
  const { t } = useTranslation();
  const [form, setForm] = useState({
    name: user.name,
    course: user.course ?? '',
    institution: user.institution ?? '',
    currentPeriod: user.currentPeriod ?? '',
    bio: user.bio ?? '',
  });
  const [avatarColor, setAvatarColor] = useState<string | null>(user.avatarColor ?? null);
  const [nameError, setNameError] = useState<string>();
  const save = useSaveProfile();
  const errors = fieldErrors(save.error);
  const field = (key: keyof typeof form) => ({
    value: form[key],
    error: errors[key],
    onChange: (e: { target: { value: string } }) => {
      save.reset();
      setForm((f) => ({ ...f, [key]: e.target.value }));
    },
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!form.name.trim()) {
      setNameError(t('profile.profile.nameRequired'));
      return;
    }
    setNameError(undefined);
    save.mutate({ ...form, avatarColor });
  }

  return (
    <Card title={t('profile.profile.title')} aria-label={t('profile.profile.title')} id="perfil">
      <form noValidate onSubmit={onSubmit} className="flex flex-col gap-5">
        <div className="flex items-center gap-4">
          <Avatar name={form.name || user.name} color={avatarColor} size="lg" />
          <div className="min-w-0">
            <p className="card-title truncate">{form.name || user.name}</p>
            <p className="truncate text-sm text-text-muted">
              {[form.course, form.institution].filter(Boolean).join(' · ') ||
                t('profile.profile.emptyHint')}
            </p>
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            label={t('profile.profile.name')}
            maxLength={120}
            required
            autoComplete="name"
            {...field('name')}
            error={nameError ?? errors.name}
          />
          <TextField
            label={t('profile.profile.course')}
            placeholder={t('profile.profile.coursePlaceholder')}
            maxLength={120}
            {...field('course')}
          />
          <TextField
            label={t('profile.profile.institution')}
            maxLength={120}
            autoComplete="organization"
            {...field('institution')}
          />
          <TextField
            label={t('profile.profile.currentPeriod')}
            placeholder={t('profile.profile.currentPeriodPlaceholder')}
            maxLength={40}
            {...field('currentPeriod')}
          />
        </div>
        <TextAreaField
          label={t('profile.profile.bio')}
          hint={t('profile.profile.bioHint', { count: 280 - form.bio.length })}
          maxLength={280}
          rows={3}
          {...field('bio')}
        />

        <fieldset className="flex flex-col gap-2">
          <legend className="field-label mb-2 block">{t('profile.profile.avatarColor')}</legend>
          <div className="flex flex-wrap gap-2">
            <ColorOption
              label={t('profile.profile.avatarDefault')}
              checked={avatarColor === null}
              onChange={() => setAvatarColor(null)}
              swatch="var(--space-deep)"
            />
            {SUBJECT_COLORS.map((c) => (
              <ColorOption
                key={c}
                label={t(colorKey(c))}
                checked={avatarColor === c}
                onChange={() => setAvatarColor(c)}
                swatch={c}
              />
            ))}
          </div>
          {errors.avatarColor && <p className="text-sm text-danger">{errors.avatarColor}</p>}
        </fieldset>

        <div className="flex flex-wrap items-center gap-3">
          <Button type="submit" disabled={save.isPending}>
            {save.isPending ? t('profile.saving') : t('profile.profile.save')}
          </Button>
          <SavedNote show={save.isSuccess}>{t('profile.profile.saved')}</SavedNote>
        </div>
        {save.error && <ErrorState message={errorMessage(t, save.error)} />}
      </form>
    </Card>
  );
}

function ColorOption({
  label,
  checked,
  onChange,
  swatch,
}: {
  label: string;
  checked: boolean;
  onChange: () => void;
  swatch: string;
}) {
  return (
    <label
      className={cn(
        'flex h-11 w-11 cursor-pointer items-center justify-center rounded-full border-2 has-[:focus-visible]:outline has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-action',
        checked ? 'border-text' : 'border-transparent',
      )}
    >
      <input
        type="radio"
        name="avatar-color"
        checked={checked}
        onChange={onChange}
        className="sr-only"
        aria-label={label}
      />
      <span
        aria-hidden="true"
        className="h-7 w-7 rounded-full"
        style={{ backgroundColor: swatch }}
      />
    </label>
  );
}
