import { useTranslation } from 'react-i18next';
import { Chip } from '@/components/ui';
import { AuthBrand } from '@/features/auth/AuthLayout';
import { initialsOf, type SignupDraft } from './signupLogic';

/** Painel de marca (só em tela larga): slogan, 3 benefícios e a prévia do perfil ao vivo. */
export function SignupBrand({ draft, photoUrl }: { draft: SignupDraft; photoUrl?: string }) {
  const { t } = useTranslation();
  return (
    <AuthBrand label={t('signup.brand.label')}>
      <ProfilePreview draft={draft} photoUrl={photoUrl} />
    </AuthBrand>
  );
}

/** Prévia do perfil: atualiza enquanto a pessoa digita. */
export function ProfilePreview({ draft, photoUrl }: { draft: SignupDraft; photoUrl?: string }) {
  const { t } = useTranslation();
  const course = [draft.course.trim(), draft.period.trim()].filter(Boolean).join(' · ');
  return (
    <section
      aria-label={t('signup.preview.label')}
      aria-live="polite"
      className="card flex max-w-md flex-col gap-4 p-5"
    >
      <p className="label text-text-muted">{t('signup.preview.title')}</p>
      <div className="flex items-center gap-4">
        {photoUrl ? (
          <img src={photoUrl} alt="" className="h-12 w-12 rounded-full object-cover" />
        ) : (
          <span
            aria-hidden="true"
            className="flex h-12 w-12 items-center justify-center rounded-full bg-surface-raised text-base font-bold"
          >
            {initialsOf(draft.name)}
          </span>
        )}
        <div className="min-w-0">
          <p className="truncate text-lg font-semibold" data-testid="preview-name">
            {draft.name.trim() || t('signup.preview.namePlaceholder')}
          </p>
          <p className="truncate text-sm text-text-secondary" data-testid="preview-nickname">
            {draft.nickname.trim()
              ? `@${draft.nickname.trim()}`
              : t('signup.preview.nicknamePlaceholder')}
          </p>
        </div>
      </div>
      <div className="flex flex-wrap gap-2">
        {draft.area && <Chip tone="action">{t(`signup.areas.${draft.area}`)}</Chip>}
        {course && <Chip>{course}</Chip>}
        {draft.institution.trim() && <Chip>{draft.institution.trim()}</Chip>}
      </div>
      <p className="text-sm text-text-secondary">
        {t('signup.preview.routine', {
          hours: draft.weeklyGoalHours,
          rhythm: t(`signup.rhythms.${draft.focusRhythm}.label`),
        })}
      </p>
      <p className="text-xs text-text-muted">{t('signup.preview.visible')}</p>
    </section>
  );
}
