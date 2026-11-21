import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { Button, ErrorState, Icon, PageTitle } from '@/components/ui';
import { ADMIN_RANGES, type AdminRange } from '@/lib/api/admin';
import { ApiError } from '@/lib/api/client';
import { cn } from '@/lib/cn';
import { useAuthStore } from '@/stores/authStore';
import {
  ActivityCard,
  CardSkeleton,
  ErrorsCard,
  FunnelCard,
  HealthCard,
  OverviewTop,
  ProvidersCard,
  RestrictedBanner,
  SignupsCard,
  UsageCard,
} from './AdminSections';
import { buildCsv, downloadCsv } from './adminLogic';
import { ForbiddenPage } from './ForbiddenPage';
import {
  adminKeys,
  useAdminErrors,
  useAdminHealth,
  useAdminOverview,
  useAdminSignups,
} from './queries';

/**
 * Admin: Status. Só para quem tem papel ADMIN: sem o papel no perfil, nem monta as consultas (a
 * página 403 aparece direto, sem piscar o conteúdo). O servidor confere de novo a cada chamada.
 */
export function AdminStatusPage() {
  const isAdmin = useAuthStore((s) => s.user?.role === 'ADMIN');
  if (!isAdmin) return <ForbiddenPage />;
  return <AdminStatus />;
}

function AdminStatus() {
  const { t, i18n } = useTranslation();
  const qc = useQueryClient();
  const [range, setRange] = useState<AdminRange>('7d');
  const overview = useAdminOverview(range);
  const health = useAdminHealth();
  const signups = useAdminSignups();
  const errors = useAdminErrors();

  // Papel revogado no servidor (o perfil local ainda dizia ADMIN): 403 vira a página de acesso negado.
  const forbidden = [overview, health, signups, errors].some(
    (q) => q.error instanceof ApiError && q.error.status === 403,
  );
  if (forbidden) return <ForbiddenPage />;

  const fetching = [overview, health, signups, errors].some((q) => q.isFetching);
  const updatedAt = overview.dataUpdatedAt
    ? new Intl.DateTimeFormat(i18n.language, { timeStyle: 'medium' }).format(overview.dataUpdatedAt)
    : null;

  function exportCsv() {
    const csv = buildCsv({
      overview: overview.data,
      health: health.data,
      signups: signups.data,
      errors: errors.data,
    });
    downloadCsv(
      csv,
      `stackstudy-admin-status-${range}-${new Date().toISOString().slice(0, 10)}.csv`,
    );
  }

  return (
    <>
      <PageTitle
        title={t('admin.title')}
        description={t('admin.description')}
        actions={
          <>
            <div
              role="group"
              aria-label={t('admin.range.label')}
              className="flex rounded-pill border border-border p-1"
            >
              {ADMIN_RANGES.map((r) => (
                <button
                  key={r}
                  type="button"
                  aria-pressed={range === r}
                  onClick={() => setRange(r)}
                  className={cn(
                    'min-h-9 rounded-pill px-4 text-sm font-semibold',
                    range === r
                      ? 'bg-action text-action-ink'
                      : 'text-text-secondary hover:text-text',
                  )}
                >
                  {t(`admin.range.${r}`)}
                </button>
              ))}
            </div>
            <Button
              variant="secondary"
              disabled={fetching}
              onClick={() => void qc.invalidateQueries({ queryKey: adminKeys.all })}
            >
              <Icon
                name="arrowRight"
                size={18}
                className={cn(fetching && 'animate-spin motion-reduce:animate-none')}
              />
              {fetching ? t('admin.refreshing') : t('admin.refresh')}
            </Button>
            <Button variant="secondary" disabled={!overview.data} onClick={exportCsv}>
              <Icon name="archive" size={18} />
              {t('admin.exportCsv')}
            </Button>
          </>
        }
      />
      <div className="flex flex-col gap-6">
        <RestrictedBanner />
        <p className="text-xs text-text-muted" aria-live="polite">
          {updatedAt ? t('admin.updatedAt', { time: updatedAt }) : t('admin.loading')}
          {' · '}
          {t('admin.autoRefresh')}
        </p>

        {overview.isPending ? (
          <div className="grid gap-4 xl:grid-cols-[320px_minmax(0,1fr)]" role="status">
            <span className="sr-only">{t('admin.loading')}</span>
            <CardSkeleton lines={2} />
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              {[0, 1, 2, 3].map((i) => (
                <CardSkeleton key={i} lines={1} />
              ))}
            </div>
          </div>
        ) : overview.isError ? (
          <ErrorState message={t('admin.loadError')} onRetry={() => void overview.refetch()} />
        ) : (
          <>
            <OverviewTop overview={overview.data} />
            <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
              <ActivityCard overview={overview.data} />
              <HealthCard query={health} />
            </div>
            <div className="grid gap-6 lg:grid-cols-2">
              <FunnelCard overview={overview.data} />
              <UsageCard overview={overview.data} />
            </div>
          </>
        )}

        <ProvidersCard health={health.data} />
        <div className="grid gap-6 xl:grid-cols-2">
          <SignupsCard query={signups} />
          <ErrorsCard query={errors} />
        </div>
      </div>
    </>
  );
}
