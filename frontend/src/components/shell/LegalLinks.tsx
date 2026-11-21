import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { cn } from '@/lib/cn';

/** Links de Termos e Privacidade (rodapé do login/cadastro, das páginas públicas e do app). */
export function LegalLinks({ className }: { className?: string }) {
  const { t } = useTranslation();
  return (
    <nav aria-label={t('legal.nav')} className={cn('flex flex-wrap gap-x-4 gap-y-1', className)}>
      <Link
        to="/terms"
        className="text-text-muted underline-offset-4 hover:text-text hover:underline"
      >
        {t('legal.terms.short')}
      </Link>
      <Link
        to="/privacy"
        className="text-text-muted underline-offset-4 hover:text-text hover:underline"
      >
        {t('legal.privacy.short')}
      </Link>
    </nav>
  );
}
