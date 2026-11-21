import { useTranslation } from 'react-i18next';
import { Card, Skeleton } from '@/components/ui';

/** Esqueleto genérico enquanto o arquivo da tela baixa (code splitting). */
export function RouteFallback() {
  const { t } = useTranslation();
  return (
    <div role="status" aria-live="polite" className="flex flex-col gap-6">
      <span className="sr-only">{t('common.loading')}</span>
      <Skeleton className="h-12 w-2/3 max-w-xl" />
      <Skeleton className="h-4 w-1/2 max-w-md" />
      <div className="grid gap-4 sm:grid-cols-2">
        <Card as="div" className="flex flex-col gap-3">
          <Skeleton className="h-5 w-40" />
          <Skeleton className="h-16 w-full" />
        </Card>
        <Card as="div" className="flex flex-col gap-3">
          <Skeleton className="h-5 w-32" />
          <Skeleton className="h-16 w-full" />
        </Card>
      </div>
    </div>
  );
}
