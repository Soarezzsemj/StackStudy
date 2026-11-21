import { Suspense } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Outlet } from 'react-router-dom';
import { LoadingState, Wordmark } from '@/components/ui';
import { LegalLinks } from '@/components/shell/LegalLinks';
import { useAuthStore } from '@/stores/authStore';

/** Layout das páginas públicas (Termos, Privacidade): abre com ou sem login. */
export function PublicPage() {
  const { t } = useTranslation();
  const authenticated = useAuthStore((s) => s.status === 'authenticated');
  return (
    <div className="flex min-h-dvh flex-col">
      <header className="flex items-center justify-between gap-4 border-b border-border px-4 py-4 lg:px-10">
        <Link to={authenticated ? '/' : '/login'} aria-label={t('legal.backHome')}>
          <Wordmark className="text-xl" />
        </Link>
        <Link
          to={authenticated ? '/' : '/login'}
          className="text-sm text-text-secondary hover:text-text"
        >
          {authenticated ? t('legal.backToApp') : t('legal.backToLogin')}
        </Link>
      </header>
      <main id="main" className="mx-auto w-full max-w-3xl flex-1 px-4 py-10">
        <Suspense fallback={<LoadingState />}>
          <Outlet />
        </Suspense>
      </main>
      <footer className="border-t border-border px-4 py-6 text-sm lg:px-10">
        <LegalLinks />
      </footer>
    </div>
  );
}
