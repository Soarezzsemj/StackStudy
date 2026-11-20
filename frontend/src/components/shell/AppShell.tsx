import { useEffect } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Button, Icon } from '@/components/ui';
import { useUiStore } from '@/stores/uiStore';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';
import { StatusBar } from './StatusBar';
import { CommandPalette } from './CommandPalette';
import { DeepWorkEngine } from '@/features/deepwork/DeepWorkEngine';

export function AppShell() {
  const { t } = useTranslation();
  const mobileNavOpen = useUiStore((s) => s.mobileNavOpen);
  const setMobileNavOpen = useUiStore((s) => s.setMobileNavOpen);
  const { pathname } = useLocation();

  // Fecha o menu mobile ao navegar e ao apertar Esc.
  useEffect(() => setMobileNavOpen(false), [pathname, setMobileNavOpen]);
  useEffect(() => {
    if (!mobileNavOpen) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setMobileNavOpen(false);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [mobileNavOpen, setMobileNavOpen]);

  return (
    <div className="flex h-dvh flex-col overflow-hidden">
      <div className="flex min-h-0 flex-1">
        <aside className="hidden w-sidebar shrink-0 border-r border-border lg:block">
          <Sidebar />
        </aside>

        {mobileNavOpen && (
          <div className="fixed inset-0 z-30 lg:hidden">
            <button
              type="button"
              aria-label={t('shell.closeMenu')}
              className="absolute inset-0 h-full w-full bg-black/60"
              onClick={() => setMobileNavOpen(false)}
            />
            <aside
              aria-modal="true"
              role="dialog"
              aria-label={t('nav.label')}
              className="relative h-full w-[min(var(--sidebar-width),85vw)] border-r border-border bg-bg"
            >
              <Button
                variant="ghost"
                iconOnly
                aria-label={t('shell.closeMenu')}
                className="absolute right-3 top-3"
                onClick={() => setMobileNavOpen(false)}
              >
                <Icon name="close" />
              </Button>
              <Sidebar onNavigate={() => setMobileNavOpen(false)} />
            </aside>
          </div>
        )}

        <div className="flex min-w-0 flex-1 flex-col">
          <Topbar />
          <main id="main" className="min-h-0 flex-1 overflow-y-auto px-4 py-8 lg:px-10 lg:py-10">
            <Outlet />
          </main>
        </div>
      </div>
      <StatusBar />
      <CommandPalette />
      <DeepWorkEngine />
    </div>
  );
}
