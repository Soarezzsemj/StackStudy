import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Button, TextField } from '@/components/ui';
import { authApi } from '@/lib/api/auth';
import { safeRedirect } from '@/lib/errors';
import { useAuthStore } from '@/stores/authStore';
import { AuthLayout } from './AuthLayout';
import { errorMessage } from './errorMessage';

export function LoginPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const from = safeRedirect((location.state as { from?: string } | null)?.from);
  const expired = useAuthStore((s) => s.sessionExpired);
  // Volta do login com Google (AUTH_GOOGLE_ENABLED): e-mail não verificado ou erro.
  const google = new URLSearchParams(location.search).get('google');

  const login = useMutation({
    mutationFn: authApi.login,
    onSuccess: () => navigate(from, { replace: true }),
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    login.mutate({ email, password });
  }

  return (
    <AuthLayout
      title={t('auth.login.title')}
      subtitle={t('auth.login.subtitle')}
      notice={
        google === 'unverified' || google === 'error' ? (
          <p
            role="status"
            className="mb-6 rounded-inner border border-warning/40 bg-warning/10 px-4 py-3 text-sm text-text"
          >
            {google === 'unverified'
              ? t('auth.login.googleUnverified')
              : t('auth.login.googleError')}
          </p>
        ) : (
          expired && (
            <p
              role="status"
              className="mb-6 rounded-inner border border-warning/40 bg-warning/10 px-4 py-3 text-sm text-text"
            >
              {t('auth.login.expired')}
            </p>
          )
        )
      }
      footer={
        <>
          {t('auth.login.noAccount')}{' '}
          <Link to="/signup" className="font-semibold text-action hover:underline">
            {t('auth.login.toRegister')}
          </Link>
        </>
      }
    >
      <form className="flex flex-col gap-5" onSubmit={onSubmit} noValidate>
        <TextField
          label={t('auth.email')}
          type="email"
          name="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <TextField
          label={t('auth.password')}
          type="password"
          name="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        {login.isError && (
          <p role="alert" className="text-sm text-danger">
            {errorMessage(t, login.error)}
          </p>
        )}
        <Button type="submit" className="mt-2 w-full" disabled={login.isPending}>
          {login.isPending ? t('common.loading') : t('auth.login.submit')}
        </Button>
      </form>
    </AuthLayout>
  );
}
