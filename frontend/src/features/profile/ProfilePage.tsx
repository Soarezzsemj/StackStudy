import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation } from 'react-router-dom';
import { LoadingState, PageTitle } from '@/components/ui';
import { useAuthStore } from '@/stores/authStore';
import { AccountSection } from './AccountSection';
import { PreferencesSection } from './PreferencesSection';
import { PrivacySection } from './PrivacySection';
import { ProfileSection } from './ProfileSection';

const SECTIONS = [
  { id: 'perfil', key: 'profile.profile.title' },
  { id: 'preferencias', key: 'profile.preferences.title' },
  { id: 'conta', key: 'profile.account.title' },
  { id: 'privacidade', key: 'profile.privacy.title' },
] as const;

/** Perfil, preferências, conta (e-mail, senha, dispositivos) e privacidade (LGPD). */
export function ProfilePage() {
  const { t } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const { hash } = useLocation();
  // Ctrl K abre direto numa seção (/profile#preferencias): rola até ela.
  useEffect(() => {
    if (!hash || !user) return;
    document.getElementById(hash.slice(1))?.scrollIntoView?.({ block: 'start' });
  }, [hash, user]);

  return (
    <>
      <PageTitle title={t('pages.profile.title')} description={t('pages.profile.description')} />
      {!user ? (
        <LoadingState />
      ) : (
        <div className="grid gap-6 xl:grid-cols-[200px_minmax(0,1fr)]">
          <nav aria-label={t('profile.sectionsNav')} className="hidden xl:block">
            <ul className="sticky top-4 m-0 flex list-none flex-col gap-1 p-0">
              {SECTIONS.map((s) => (
                <li key={s.id}>
                  <a
                    href={`#${s.id}`}
                    className="flex min-h-11 items-center rounded-pill px-3 text-sm font-semibold text-text-secondary hover:bg-surface hover:text-text"
                  >
                    {t(s.key)}
                  </a>
                </li>
              ))}
            </ul>
          </nav>
          <div className="flex min-w-0 flex-col gap-6">
            {/* key: trocar de conta recomeça os formulários. */}
            <ProfileSection key={`profile-${user.id}`} user={user} />
            <PreferencesSection />
            <AccountSection key={`account-${user.id}`} user={user} />
            <PrivacySection user={user} />
          </div>
        </div>
      )}
    </>
  );
}
