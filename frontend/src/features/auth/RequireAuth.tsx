import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from '@/stores/authStore';

export function FullScreenLoader() {
  const { t } = useTranslation();
  return (
    <div className="flex h-dvh items-center justify-center" role="status">
      <span className="label text-text-muted">{t('common.loading')}</span>
    </div>
  );
}

/** Protege as rotas privadas. Espera a sessão carregar antes de decidir. */
export function RequireAuth() {
  const status = useAuthStore((s) => s.status);
  const location = useLocation();
  if (status === 'unknown') return <FullScreenLoader />;
  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <Outlet />;
}

/** Para login e cadastro: quem já está logado vai direto para o app. */
export function RedirectIfAuthenticated() {
  const status = useAuthStore((s) => s.status);
  if (status === 'unknown') return <FullScreenLoader />;
  if (status === 'authenticated') return <Navigate to="/" replace />;
  return <Outlet />;
}
