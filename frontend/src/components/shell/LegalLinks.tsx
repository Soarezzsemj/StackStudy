import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { LEGAL_DOCUMENTS } from '@/content/legal/documents';
import { cn } from '@/lib/cn';
import { CONTACT_EMAIL } from '@/lib/site';

const linkClass = 'text-text-muted underline-offset-4 hover:text-text hover:underline';

/**
 * Termos, Privacidade e Contato (rodapé do login/cadastro, das páginas públicas, do app e do
 * Perfil). `label` distingue dois blocos na mesma tela.
 */
export function LegalLinks({ className, label }: { className?: string; label?: string }) {
  const { t } = useTranslation();
  return (
    <nav
      aria-label={label ?? t('legal.nav')}
      className={cn('flex flex-wrap gap-x-4 gap-y-1', className)}
    >
      <Link to={LEGAL_DOCUMENTS.terms.path} className={linkClass}>
        {t('legal.terms.short')}
      </Link>
      <Link to={LEGAL_DOCUMENTS.privacy.path} className={linkClass}>
        {t('legal.privacy.short')}
      </Link>
      <a href={`mailto:${CONTACT_EMAIL}`} className={linkClass}>
        {t('legal.contact')}
      </a>
    </nav>
  );
}
