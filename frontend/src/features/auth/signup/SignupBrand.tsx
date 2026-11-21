import { useTranslation } from 'react-i18next';
import { Chip, Icon, Wordmark } from '@/components/ui';
import { initialsOf, type SignupDraft } from './signupLogic';

/** Painel de marca (só em tela larga): slogan, 3 benefícios e a prévia do perfil ao vivo. */
export function SignupBrand({ draft, photoUrl }: { draft: SignupDraft; photoUrl?: string }) {
  const { t } = useTranslation();
  return (
    <aside
      aria-label={t('signup.brand.label')}
      className="relative hidden flex-col justify-between gap-10 overflow-hidden border-r border-border bg-space-deep/20 p-10 lg:flex"
    >
      <div className="flex flex-col gap-6">
        <Wordmark className="text-2xl" />
        <p className="page-title text-[44px]">{t('signup.brand.slogan')}</p>
        <ul className="m-0 flex list-none flex-col gap-3 p-0 text-text-on-space">
          {(['one', 'two', 'three'] as const).map((k) => (
            <li key={k} className="flex items-start gap-3">
              <Icon name="check" size={18} className="mt-0.5 shrink-0 text-action" />
              {t(`signup.brand.benefits.${k}`)}
            </li>
          ))}
        </ul>
      </div>
      <ProfilePreview draft={draft} photoUrl={photoUrl} />
    </aside>
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
      className="card card-hero flex flex-col gap-4 p-6"
    >
      <p className="label text-text-on-space">{t('signup.preview.title')}</p>
      <div className="flex items-center gap-4">
        {photoUrl ? (
          <img src={photoUrl} alt="" className="h-14 w-14 rounded-full object-cover" />
        ) : (
          <span
            aria-hidden="true"
            className="flex h-14 w-14 items-center justify-center rounded-full bg-space-glow/30 text-lg font-bold"
          >
            {initialsOf(draft.name)}
          </span>
        )}
        <div className="min-w-0">
          <p className="truncate text-lg font-semibold" data-testid="preview-name">
            {draft.name.trim() || t('signup.preview.namePlaceholder')}
          </p>
          <p className="truncate text-sm text-text-on-space" data-testid="preview-nickname">
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
      <p className="text-sm text-text-on-space">
        {t('signup.preview.routine', {
          hours: draft.weeklyGoalHours,
          rhythm: t(`signup.rhythms.${draft.focusRhythm}.label`),
        })}
      </p>
      <p className="text-xs text-text-on-space">{t('signup.preview.visible')}</p>
    </section>
  );
}
