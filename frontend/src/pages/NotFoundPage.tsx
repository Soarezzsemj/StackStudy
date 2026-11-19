import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { buttonClass, PageTitle } from '@/components/ui';

export function NotFoundPage() {
  const { t } = useTranslation();
  return (
    <>
      <PageTitle title={t('pages.notFound.title')} description={t('pages.notFound.description')} />
      <Link to="/" className={buttonClass('secondary')}>
        {t('pages.notFound.back')}
      </Link>
    </>
  );
}
