import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Button, TextField } from '@/components/ui';
import { authApi } from '@/lib/api/auth';
import { AuthLayout } from './AuthLayout';
import { errorMessage } from './errorMessage';

export function LoginPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const from = (location.state as { from?: string } | null)?.from ?? '/';

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
      footer={
        <>
          {t('auth.login.noAccount')}{' '}
          <Link to="/register" className="font-semibold text-action hover:underline">
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
