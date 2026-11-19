import { useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { findNavItem } from '@/app/routes';
import { Button, Icon } from '@/components/ui';
import { useUiStore } from '@/stores/uiStore';

export function Topbar() {
  const { t } = useTranslation();
  const { pathname } = useLocation();
  const setPaletteOpen = useUiStore((s) => s.setPaletteOpen);
  const setMobileNavOpen = useUiStore((s) => s.setMobileNavOpen);
  const slug = findNavItem(pathname)?.slug ?? pathname.replace(/^\//, '');
  const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform);

  return (
    <header className="flex h-topbar shrink-0 items-center gap-3 border-b border-border px-4 lg:px-8">
      <Button
        variant="ghost"
        iconOnly
        className="lg:hidden"
        aria-label={t('shell.openMenu')}
        onClick={() => setMobileNavOpen(true)}
      >
        <Icon name="menu" />
      </Button>

      <nav aria-label={t('shell.breadcrumb')} className="min-w-0 flex-1">
        <ol className="m-0 flex list-none items-center gap-2 truncate p-0 font-mono text-sm">
          <li className="text-text-muted">{t('shell.root')}</li>
          <li aria-hidden="true" className="text-text-muted">
            /
          </li>
          <li aria-current="page" className="truncate text-text">
            {slug}
          </li>
        </ol>
      </nav>

      <button
        type="button"
        onClick={() => setPaletteOpen(true)}
        className="flex min-h-11 items-center gap-3 rounded-pill border border-border bg-surface px-4 text-sm text-text-secondary transition-colors hover:border-action hover:text-text"
        aria-keyshortcuts="Control+K Meta+K"
      >
        <Icon name="search" size={16} />
        <span className="hidden sm:inline">{t('shell.search')}</span>
        <kbd className="label hidden rounded-md border border-border px-1.5 py-0.5 font-sans text-[11px] text-text-muted sm:inline">
          {isMac ? '⌘ K' : 'Ctrl K'}
        </kbd>
      </button>
    </header>
  );
}
