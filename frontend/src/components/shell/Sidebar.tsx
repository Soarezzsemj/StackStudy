import { NavLink, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { NAV_ITEMS } from '@/app/routes';
import { Button, Icon, Wordmark } from '@/components/ui';
import { useAuthStore } from '@/stores/authStore';
import { authApi } from '@/lib/api/auth';
import { changeLanguage, SUPPORTED_LANGUAGES, type Language } from '@/i18n';
import { cn } from '@/lib/cn';

/** Matérias do semestre atual; vêm da API na Fase 1. */
interface SidebarSubject {
  id: string;
  name: string;
  color: string;
  weekHours: number;
}

const SUBJECTS: SidebarSubject[] = [];

export function Sidebar({ onNavigate }: { onNavigate?: () => void }) {
  const { t, i18n } = useTranslation();
  const user = useAuthStore((s) => s.user);
  const navigate = useNavigate();

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
          {NAV_ITEMS.map((item) => (
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
        {SUBJECTS.length === 0 ? (
          <p className="px-3 text-sm text-text-muted">{t('shell.noSubjects')}</p>
        ) : (
          <ul className="m-0 flex list-none flex-col gap-1 p-0">
            {SUBJECTS.map((s) => (
              <li key={s.id} className="flex min-h-9 items-center gap-3 px-3 text-sm">
                <span
                  aria-hidden="true"
                  className="h-2 w-2 shrink-0 rounded-full"
                  style={{ backgroundColor: s.color }}
                />
                <span className="flex-1 truncate text-text-secondary">{s.name}</span>
                <span className="tabular text-text-muted">
                  {t('shell.hours', { value: s.weekHours })}
                </span>
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
              onClick={() => void changeLanguage(lang)}
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
          <span
            aria-hidden="true"
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-space-deep text-sm font-bold text-text-on-space"
          >
            {user?.name.charAt(0).toUpperCase() ?? '?'}
          </span>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold">{user?.name}</p>
            <p className="truncate text-xs text-text-muted">{user?.email}</p>
          </div>
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
      </div>
    </div>
  );
}
