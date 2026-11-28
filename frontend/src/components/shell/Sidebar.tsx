import { NavLink, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ADMIN_ITEM, NAV_ITEMS, PROFILE_ITEM } from '@/app/routes';
import { Avatar, Button, Icon, Wordmark } from '@/components/ui';
import { accountApi } from '@/lib/api/account';
import { useAuthStore } from '@/stores/authStore';
import { authApi } from '@/lib/api/auth';
import { changeLanguage, SUPPORTED_LANGUAGES, type Language } from '@/i18n';
import { cn } from '@/lib/cn';
import { LegalLinks } from './LegalLinks';
import { SidebarSubjects } from './SidebarSubjects';

/**
 * Barra lateral. `compact` = menu recolhido (tablet): só ícones, matérias como pontos coloridos e o
 * avatar; idioma, sair e links legais ficam no menu completo (botão de menu do topo).
 */
export function Sidebar({
  onNavigate,
  compact = false,
}: {
  onNavigate?: () => void;
  compact?: boolean;
}) {
  const { t, i18n } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const navigate = useNavigate();
  const items = [...NAV_ITEMS, ...(user?.role === 'ADMIN' ? [ADMIN_ITEM] : [])];

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

  if (compact) {
    return (
      <div className="flex h-full flex-col items-center gap-6 px-2 py-5">
        <span aria-hidden="true" className="text-xl font-bold">
          s<span className="text-action">.</span>
        </span>
        <nav aria-label={t('nav.label')}>
          <ul className="m-0 flex list-none flex-col items-center gap-1 p-0">
            {items.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  end={item.to === '/'}
                  title={t(item.labelKey)}
                  aria-label={t(item.labelKey)}
                  className={({ isActive }) =>
                    cn(
                      'flex h-11 w-11 items-center justify-center rounded-pill transition-colors',
                      isActive
                        ? 'bg-surface-raised text-text'
                        : 'text-text-secondary hover:bg-surface hover:text-text',
                    )
                  }
                >
                  <Icon name={item.icon} size={18} />
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <SidebarSubjects compact />
        <NavLink
          to={PROFILE_ITEM.to}
          title={user?.name}
          aria-label={t('shell.openProfile', { name: user?.name ?? '' })}
          className="mt-auto flex h-11 w-11 items-center justify-center rounded-pill hover:bg-surface"
        >
          <Avatar name={user?.name} color={user?.avatarColor} />
        </NavLink>
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col gap-8 px-4 py-5">
      <div className="px-2">
        <Wordmark />
      </div>

      <nav aria-label={t('nav.label')}>
        <ul className="m-0 flex list-none flex-col gap-1 p-0">
          {items.map((item) => (
            <li key={item.to}>
              <NavLink
                to={item.to}
                end={item.to === '/'}
                onClick={onNavigate}
                className={({ isActive }) =>
                  cn(
                    'flex min-h-11 items-center gap-3 rounded-pill px-3 text-[15px] font-medium transition-colors',
                    isActive
                      ? 'bg-surface-raised text-text'
                      : 'text-text-secondary hover:bg-surface hover:text-text',
                  )
                }
              >
                <Icon name={item.icon} size={18} />
                {t(item.labelKey)}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>

      <SidebarSubjects onNavigate={onNavigate} />

      <div className="flex flex-col gap-2 border-t border-border pt-4">
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
              <span className="block truncate text-xs text-text-muted">
                {user?.course?.trim() || user?.email}
              </span>
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
