import { useTranslation } from 'react-i18next';
import { Card, Skeleton } from '@/components/ui';

/**
 * Esqueleto do Cockpit com o mesmo desenho da tela: com o servidor acordando (até ~2 min no plano
 * grátis) a pessoa vê a estrutura, e o aviso "Acordando o servidor…" aparece por cima.
 */
export function CockpitSkeleton() {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col gap-6" role="status" aria-live="polite">
      <span className="sr-only">{t('cockpit.loading')}</span>
      <Card variant="hero" as="div">
        <div className="flex items-center gap-4">
          <Skeleton className="h-12 w-12 rounded-full" />
          <div className="flex flex-1 flex-col gap-2">
            <Skeleton className="h-5 w-2/3" />
            <Skeleton className="h-4 w-1/2" />
          </div>
          <Skeleton className="hidden h-11 w-40 rounded-pill sm:block" />
        </div>
      </Card>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {[0, 1, 2, 3].map((i) => (
          <Card as="div" key={i} className="flex flex-col gap-3 p-5">
            <Skeleton className="h-3 w-24" />
            <Skeleton className="h-9 w-20" />
            <Skeleton className="h-2 w-full" />
          </Card>
        ))}
      </div>
      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
        <Card as="div" className="flex flex-col gap-3">
          <Skeleton className="h-5 w-40" />
          {[0, 1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-10 w-full" />
          ))}
        </Card>
        <Card as="div" className="flex flex-col gap-3">
          <Skeleton className="h-5 w-32" />
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
        </Card>
      </div>
    </div>
  );
}
