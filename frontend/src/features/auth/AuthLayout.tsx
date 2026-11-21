import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Card, Wordmark } from '@/components/ui';
import { LegalLinks } from '@/components/shell/LegalLinks';

export function AuthLayout({
  title,
  subtitle,
  children,
  footer,
  notice,
}: {
  title: string;
  subtitle: string;
  children: ReactNode;
  footer: ReactNode;
  /** Aviso no topo do cartão (ex.: sessão expirou). */
  notice?: ReactNode;
}) {
  const { t } = useTranslation();
  return (
    <main id="main" className="flex min-h-dvh items-center justify-center px-4 py-10">
      <div className="flex w-full max-w-md flex-col gap-8">
        <div className="flex flex-col gap-2">
          <Wordmark className="text-2xl" />
          <p className="text-text-secondary">{t('auth.tagline')}</p>
        </div>
        <Card variant="hero" className="p-8">
          <h1 className="page-title mb-3 text-[40px]">{title}</h1>
          <p className="mb-8 text-text-on-space">{subtitle}</p>
          {notice}
          {children}
        </Card>
        <p className="text-center text-sm text-text-secondary">{footer}</p>
        <LegalLinks className="justify-center text-xs" />
      </div>
    </main>
  );
}
