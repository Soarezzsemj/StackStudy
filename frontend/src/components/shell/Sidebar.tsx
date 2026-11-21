import { NavLink, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ADMIN_ITEM, NAV_ITEMS, PROFILE_ITEM } from '@/app/routes';
import { Avatar, Button, Icon, Wordmark } from '@/components/ui';
import { accountApi } from '@/lib/api/account';
import { useAuthStore } from '@/stores/authStore';
import { authApi } from '@/lib/api/auth';
import { changeLanguage, SUPPORTED_LANGUAGES, type Language } from '@/i18n';
import { cn } from '@/lib/cn';
import { useCurrentSubjects } from '@/features/stack/queries';
import { useStackStore } from '@/stores/stackStore';
import { LegalLinks } from './LegalLinks';

export function Sidebar({ onNavigate }: { onNavigate?: () => void }) {
  const { t, i18n } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const navigate = useNavigate();
  // Matérias do semestre atual, com pendentes (mesma consulta da tela The Stack).
  const { list: subjects, current } = useCurrentSubjects();
  const setFilters = useStackStore((st) => st.setFilters);

  function openSubject(id: string) {
    setFilters({ subject: id });
    navigate('/stack');
    onNavigate?.();
  }

  /** Troca o idioma na hora e grava na conta (vale no próximo login, em qualquer aparelho). */
  function pickLanguage(lang: Language) {
    void changeLanguage(lang);
    const current = useAuthStore.getState().user;
    if (current && current.locale !== lang) {
      useAuthStore.getState().setUser({ ...current, locale: lang });
      accountApi.updatePreferences({ locale: lang }).catch(() => undefined);
    }
  }

  async function handleLogout() {
    await authApi.logout().catch(() => undefined);
    navigate('/login', { replace: true });
  }

  return (
    <div className="flex h-full flex-col gap-8 px-4 py-5">
      <div className="px-2">
        <Wordmark />
      </div>

      <nav aria-label={t('nav.label')}>
        <ul className="m-0 flex list-none flex-col gap-1 p-0">
          {[...NAV_ITEMS, ...(user?.role === 'ADMIN' ? [ADMIN_ITEM] : [])].map((item) => (
            <li key={item.to}>
              <NavLink
                to={item.to}
                end={item.to === '/'}
                onClick={onNavigate}
                className={({ isActive }) =>
                  cn(
                    'flex min-h-11 items-center gap-3 rounded-pill px-3 text-[15px] font-semibold transition-colors',
                    isActive
                      ? 'bg-surface-raised text-text'
                      : 'text-text-secondary hover:bg-surface hover:text-text',
                  )
                }
              >
                {({ isActive }) => (
                  <>
                    <Icon name={item.icon} className={isActive ? 'text-action' : undefined} />
                    {t(item.labelKey)}
                  </>
                )}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>

      <section aria-labelledby="sidebar-subjects" className="min-h-0 flex-1 overflow-y-auto">
        <h2 id="sidebar-subjects" className="label mb-3 px-3 text-text-muted">
          {t('shell.subjects')}
        </h2>
        {subjects.length === 0 ? (
          <p className="px-3 text-sm text-text-muted">
            {current ? t('shell.noSubjects') : t('shell.noSemester')}
          </p>
        ) : (
          <ul className="m-0 flex list-none flex-col gap-1 p-0">
            {subjects.map((s) => (
              <li key={s.id}>
                <button
                  type="button"
                  onClick={() => openSubject(s.id)}
                  className="flex min-h-11 w-full items-center gap-3 rounded-pill px-3 text-left text-sm text-text-secondary transition-colors hover:bg-surface hover:text-text"
                >
                  <span
                    aria-hidden="true"
                    className="h-2 w-2 shrink-0 rounded-full"
                    style={{ backgroundColor: s.color }}
                  />
                  <span className="flex-1 truncate">{s.name}</span>
                  <span className="tabular text-text-muted">
                    <span aria-hidden="true">{s.pendingTasks}</span>
                    <span className="sr-only">{t('shell.pending', { count: s.pendingTasks })}</span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <div className="flex flex-col gap-3 border-t border-border pt-4">
        <div
          className="flex items-center gap-1 px-1"
          role="group"
          aria-label={t('common.language')}
        >
          <Icon name="globe" size={16} className="mx-2 text-text-muted" />
          {SUPPORTED_LANGUAGES.map((lang: Language) => (
            <button
              key={lang}
              type="button"
              onClick={() => pickLanguage(lang)}
              aria-pressed={i18n.language === lang}
              className={cn(
                'label min-h-11 rounded-pill px-3',
                i18n.language === lang ? 'text-action' : 'text-text-muted hover:text-text',
              )}
            >
              {lang === 'pt-BR' ? 'PT' : 'EN'}
              <span className="sr-only"> — {t(`common.languages.${lang}`)}</span>
            </button>
          ))}
        </div>
        <div className="flex items-center gap-3 px-2">
          <NavLink
            to={PROFILE_ITEM.to}
            onClick={onNavigate}
            aria-label={t('shell.openProfile', { name: user?.name ?? '' })}
            className="flex min-h-11 min-w-0 flex-1 items-center gap-3 rounded-pill pr-2 hover:bg-surface"
          >
            <Avatar name={user?.name} color={user?.avatarColor} />
            <span className="min-w-0 flex-1">
              <span className="block truncate text-sm font-semibold">{user?.name}</span>
              <span className="block truncate text-xs text-text-muted">{user?.email}</span>
            </span>
          </NavLink>
          <Button
            variant="ghost"
            iconOnly
            aria-label={t('shell.logout')}
            title={t('shell.logout')}
            onClick={() => void handleLogout()}
          >
            <Icon name="logout" size={18} />
          </Button>
        </div>
        <LegalLinks className="px-3 text-xs" />
      </div>
    </div>
  );
}
