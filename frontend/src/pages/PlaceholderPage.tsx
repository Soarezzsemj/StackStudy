import { useTranslation } from 'react-i18next';
import { Card, Chip, PageTitle } from '@/components/ui';

/** Página provisória de cada módulo até a fase dele chegar. */
export function PlaceholderPage({ pageKey, phase }: { pageKey: string; phase: number }) {
  const { t } = useTranslation();
  return (
    <>
      <PageTitle
        title={t(`pages.${pageKey}.title`)}
        description={t(`pages.${pageKey}.description`)}
      />
      <Card className="max-w-2xl">
        <div className="flex flex-wrap items-center gap-3">
          <Chip tone="space">{t('common.phase', { n: phase })}</Chip>
          <span className="text-text-secondary">{t('common.comingSoon')}</span>
        </div>
      </Card>
    </>
  );
}
