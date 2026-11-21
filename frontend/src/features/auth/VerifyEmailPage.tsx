import { useEffect, useRef } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { LoadingState } from '@/components/ui';
import { authApi } from '@/lib/api/auth';
import { AuthLayout } from './AuthLayout';
import { errorMessage } from './errorMessage';

/** Link do e-mail de verificação (EMAIL_VERIFICATION_ENABLED): confirma e já entra. */
export function VerifyEmailPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const verify = useMutation({
    mutationFn: authApi.verifyEmail,
    onSuccess: () => navigate('/', { replace: true }),
  });
  const sent = useRef(false);
  const { mutate } = verify;

  useEffect(() => {
    // Uma vez só (o token é de uso único).
    if (sent.current || !token) return;
    sent.current = true;
    mutate(token);
  }, [token, mutate]);

  return (
    <AuthLayout
      title={t('signup.verify.pageTitle')}
      subtitle={t('signup.verify.pageSubtitle')}
      footer={
        <Link to="/login" className="font-semibold text-action hover:underline">
          {t('signup.toLogin')}
        </Link>
      }
    >
      {!token || verify.isError ? (
        <p role="alert" className="text-sm text-danger">
          {token ? errorMessage(t, verify.error) : t('errors.INVALID_VERIFICATION_TOKEN')}
        </p>
      ) : (
        <LoadingState />
      )}
    </AuthLayout>
  );
}
