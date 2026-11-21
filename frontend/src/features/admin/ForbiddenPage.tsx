import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { buttonClass, Card, Icon } from '@/components/ui';

/** 403: área só para administradores. Não mostra nada do painel. */
export function ForbiddenPage() {
  const { t } = useTranslation();
  return (
    <Card className="mx-auto mt-6 max-w-lg p-8" role="alert">
      <p className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-danger/10 text-danger">
        <Icon name="ban" size={24} />
      </p>
      <h1 className="page-title mb-3 text-[40px]">{t('admin.forbidden.title')}</h1>
      <p className="mb-8 text-text-secondary">{t('admin.forbidden.body')}</p>
      <Link to="/" className={buttonClass('secondary')}>
        {t('admin.forbidden.back')}
      </Link>
    </Card>
  );
}
