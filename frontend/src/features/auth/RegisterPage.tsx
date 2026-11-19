import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Button, TextField } from '@/components/ui';
import { authApi } from '@/lib/api/auth';
import { isLanguage, DEFAULT_LANGUAGE } from '@/i18n';
import { AuthLayout } from './AuthLayout';
import { errorMessage, fieldErrors } from './errorMessage';

export function RegisterPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  const register = useMutation({
    mutationFn: authApi.register,
    onSuccess: () => navigate('/', { replace: true }),
  });
  const fields = fieldErrors(register.error);

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    register.mutate({
      name,
      email,
      password,
      locale: isLanguage(i18n.language) ? i18n.language : DEFAULT_LANGUAGE,
    });
  }

  return (
    <AuthLayout
      title={t('auth.register.title')}
      subtitle={t('auth.register.subtitle')}
      footer={
        <>
          {t('auth.register.haveAccount')}{' '}
          <Link to="/login" className="font-semibold text-action hover:underline">
            {t('auth.register.toLogin')}
          </Link>
        </>
      }
    >
      <form className="flex flex-col gap-5" onSubmit={onSubmit} noValidate>
        <TextField
          label={t('auth.name')}
          name="name"
          autoComplete="name"
          required
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={fields.name}
        />
        <TextField
          label={t('auth.email')}
          type="email"
          name="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={fields.email}
        />
        <TextField
          label={t('auth.password')}
          type="password"
          name="password"
          autoComplete="new-password"
          required
          minLength={8}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          hint={t('auth.passwordHint')}
          error={fields.password}
        />
        {register.isError && (
          <p role="alert" className="text-sm text-danger">
            {errorMessage(t, register.error)}
          </p>
        )}
        <Button type="submit" className="mt-2 w-full" disabled={register.isPending}>
          {register.isPending ? t('common.loading') : t('auth.register.submit')}
        </Button>
      </form>
    </AuthLayout>
  );
}
