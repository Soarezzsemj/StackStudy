import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Card, Chip, EmptyState, ErrorState, Icon, ProgressBar, Skeleton } from '@/components/ui';
import type { AdminErrorRow, AdminHealth, AdminOverview, AdminSignup } from '@/lib/api/admin';
import { cn } from '@/lib/cn';
import { ActivityChart, Sparkline } from './AdminCharts';
import {
  formatBytes,
  formatUptime,
  pct,
  pointsDelta,
  retentionRate,
  statusTone,
  variation,
} from './adminLogic';

// ---------------------------------------------------------------------------
// estados
// ---------------------------------------------------------------------------

export function CardSkeleton({ lines = 3, className }: { lines?: number; className?: string }) {
  return (
    <Card as="div" className={cn('flex flex-col gap-3', className)}>
      <Skeleton className="h-4 w-32" />
      {Array.from({ length: lines }, (_, i) => (
        <Skeleton key={i} className="h-8 w-full" />
      ))}
    </Card>
  );
}

function State({
  query,
  children,
  empty,
  lines,
}: {
  query: { isPending: boolean; isError: boolean; refetch: () => unknown };
  children: ReactNode;
  empty?: boolean;
  lines?: number;
}) {
  const { t } = useTranslation();
  if (query.isPending) {
    return (
      <div className="flex flex-col gap-2" aria-hidden="true">
        {Array.from({ length: lines ?? 3 }, (_, i) => (
          <Skeleton key={i} className="h-8 w-full" />
        ))}
      </div>
    );
  }
  if (query.isError) {
    return <ErrorState message={t('admin.loadError')} onRetry={() => void query.refetch()} />;
  }
  if (empty) return <EmptyState title={t('admin.empty')} />;
  return <>{children}</>;
}

// ---------------------------------------------------------------------------
// topo
// ---------------------------------------------------------------------------

export function RestrictedBanner() {
  const { t } = useTranslation();
  return (
    <p
      role="note"
      className="flex items-center gap-3 rounded-inner border border-space-glow/40 bg-space-deep/30 px-4 py-3 text-sm text-text-on-space"
    >
      <Icon name="alert" size={18} className="shrink-0 text-space-glow" />
      {t('admin.restricted')}
    </p>
  );
}

function Delta({ value, unit = '%' }: { value: number | null; unit?: string }) {
  const { t, i18n } = useTranslation();
  if (value === null)
    return <span className="text-xs text-text-muted">{t('admin.kpi.noCompare')}</span>;
  const up = value > 0;
  const flat = value === 0;
  const formatted = `${up ? '+' : ''}${new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 1 }).format(value)}${unit}`;
  return (
    <span
      className={cn(
        'tabular text-xs font-semibold',
        flat ? 'text-text-muted' : up ? 'text-action' : 'text-danger',
      )}
    >
      <span aria-hidden="true">{flat ? '→' : up ? '↑' : '↓'} </span>
      {formatted}
      <span className="sr-only">
        {' '}
        {t(up ? 'admin.kpi.up' : flat ? 'admin.kpi.flat' : 'admin.kpi.down')}
      </span>
    </span>
  );
}

function Kpi({
  label,
  value,
  delta,
  hint,
}: {
  label: string;
  value: string;
  delta: ReactNode;
  hint: string;
}) {
  return (
    <Card as="div" className="flex flex-col gap-2 p-5">
      <dt className="label text-text-muted">{label}</dt>
      <dd className="tabular m-0 text-4xl font-[650] leading-none tracking-card">{value}</dd>
      <dd className="m-0 flex flex-wrap items-center gap-2 text-xs text-text-secondary">
        {delta}
        <span>{hint}</span>
      </dd>
    </Card>
  );
}

export function OverviewTop({ overview }: { overview: AdminOverview }) {
  const { t, i18n } = useTranslation();
  const n = (v: number) => new Intl.NumberFormat(i18n.language).format(v);
  const r = retentionRate(overview.retentionD7);
  return (
    <div className="grid gap-4 xl:grid-cols-[320px_minmax(0,1fr)]">
      <Card
        variant="hero"
        eyebrow={t('admin.online.eyebrow')}
        aria-label={t('admin.online.eyebrow')}
      >
        <p className="flex items-baseline gap-2">
          <span className="relative flex h-3 w-3" aria-hidden="true">
            <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-action opacity-60 motion-reduce:animate-none" />
            <span className="relative inline-flex h-3 w-3 rounded-full bg-action" />
          </span>
          <span className="tabular text-5xl font-[650] leading-none tracking-card">
            {n(overview.onlineNow)}
          </span>
          <span className="text-sm text-text-on-space">
            {t('admin.online.unit', { count: overview.onlineNow })}
          </span>
        </p>
        <p className="mb-3 mt-2 text-xs text-text-on-space">{t('admin.online.hint')}</p>
        <Sparkline series={overview.series.slice(-7)} />
      </Card>
      <dl
        className="m-0 grid gap-4 sm:grid-cols-2 xl:grid-cols-4"
        aria-label={t('admin.kpi.label')}
      >
        <Kpi
          label={t('admin.kpi.users')}
          value={n(overview.totalUsers)}
          delta={<Delta value={variation(overview.newUsers)} />}
          hint={t('admin.kpi.newInRange', {
            count: overview.newUsers.value,
            range: t(`admin.range.${overview.range}`),
          })}
        />
        <Kpi
          label={t('admin.kpi.dau')}
          value={n(overview.dailyActive.value)}
          delta={<Delta value={variation(overview.dailyActive)} />}
          hint={t('admin.kpi.vsYesterday')}
        />
        <Kpi
          label={t('admin.kpi.wau')}
          value={n(overview.weeklyActive.value)}
          delta={<Delta value={variation(overview.weeklyActive)} />}
          hint={t('admin.kpi.vsLastWeek')}
        />
        <Kpi
          label={t('admin.kpi.retention')}
          value={
            r.value === null ? '—' : `${new Intl.NumberFormat(i18n.language).format(r.value)}%`
          }
          delta={<Delta value={pointsDelta(r.value, r.previous)} unit=" p.p." />}
          hint={t('admin.kpi.retentionHint', { count: overview.retentionD7.cohort })}
        />
      </dl>
    </div>
  );
}

export function ActivityCard({ overview }: { overview: AdminOverview }) {
  const { t, i18n } = useTranslation();
  const empty = overview.series.every((s) => s.activeUsers === 0 && s.signups === 0);
  return (
    <Card title={t('admin.activity.title')} aria-label={t('admin.activity.title')}>
      <p className="mb-4 text-sm text-text-secondary">
        {t('admin.activity.focus', {
          hours: new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 2 }).format(
            overview.focusHoursPerActive,
          ),
          range: t(`admin.range.${overview.range}`),
        })}
      </p>
      {empty ? <EmptyState title={t('admin.empty')} /> : <ActivityChart series={overview.series} />}
    </Card>
  );
}

// ---------------------------------------------------------------------------
// funil e uso
// ---------------------------------------------------------------------------

function BarRow({
  label,
  value,
  total,
  detail,
}: {
  label: string;
  value: number;
  total: number;
  detail: string;
}) {
  const { i18n } = useTranslation();
  const p = pct(value, total);
  return (
    <li className="flex flex-col gap-1.5">
      <div className="flex items-baseline justify-between gap-3 text-sm">
        <span className="font-semibold">{label}</span>
        <span className="tabular text-text-secondary">
          {new Intl.NumberFormat(i18n.language).format(value)} · {detail}
        </span>
      </div>
      <ProgressBar value={p} label={`${label}: ${p}%`} />
    </li>
  );
}

export function FunnelCard({ overview }: { overview: AdminOverview }) {
  const { t } = useTranslation();
  const f = overview.funnel;
  const steps = [
    ['registered', f.registered],
    ['firstSubject', f.firstSubject],
    ['firstFocusSession', f.firstFocusSession],
    ['returned7d', f.returned7d],
  ] as const;
  return (
    <Card title={t('admin.funnel.title')} aria-label={t('admin.funnel.title')}>
      <p className="mb-4 text-sm text-text-secondary">{t('admin.funnel.hint')}</p>
      {f.registered === 0 ? (
        <EmptyState title={t('admin.empty')} />
      ) : (
        <ol className="m-0 flex list-none flex-col gap-4 p-0">
          {steps.map(([key, value]) => (
            <BarRow
              key={key}
              label={t(`admin.funnel.${key}`)}
              value={value}
              total={f.registered}
              detail={`${pct(value, f.registered)}%`}
            />
          ))}
        </ol>
      )}
    </Card>
  );
}

export function UsageCard({ overview }: { overview: AdminOverview }) {
  const { t } = useTranslation();
  const u = overview.usage;
  const items = [
    ['stack', u.stack],
    ['deepWork', u.deepWork],
    ['flashcards', u.flashcards],
    ['grades', u.grades],
  ] as const;
  return (
    <Card title={t('admin.usage.title')} aria-label={t('admin.usage.title')}>
      <p className="mb-4 text-sm text-text-secondary">
        {t('admin.usage.hint', { count: u.weeklyActive })}
      </p>
      {u.weeklyActive === 0 ? (
        <EmptyState title={t('admin.empty')} />
      ) : (
        <ul className="m-0 flex list-none flex-col gap-4 p-0">
          {items.map(([key, value]) => (
            <BarRow
              key={key}
              label={t(`admin.usage.${key}`)}
              value={value}
              total={u.weeklyActive}
              detail={`${pct(value, u.weeklyActive)}%`}
            />
          ))}
        </ul>
      )}
    </Card>
  );
}

// ---------------------------------------------------------------------------
// saúde e provedores
// ---------------------------------------------------------------------------

export function HealthCard({
  query,
}: {
  query: { data?: AdminHealth; isPending: boolean; isError: boolean; refetch: () => unknown };
}) {
  const { t, i18n } = useTranslation();
  const h = query.data;
  return (
    <Card title={t('admin.health.title')} aria-label={t('admin.health.title')}>
      <State query={query} lines={5}>
        {h && (
          <dl className="m-0 grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
            <dt className="text-text-muted">{t('admin.health.db')}</dt>
            <dd className="m-0 flex items-center gap-2">
              <Chip tone={h.dbUp ? 'action' : 'danger'}>
                <Icon name={h.dbUp ? 'check' : 'ban'} size={14} />
                {h.dbUp ? t('admin.health.up') : t('admin.health.down')}
              </Chip>
              {h.dbLatencyMs !== undefined && (
                <span className="tabular text-text-secondary">{h.dbLatencyMs} ms</span>
              )}
            </dd>
            <dt className="text-text-muted">{t('admin.health.dbSize')}</dt>
            <dd className="tabular m-0">{formatBytes(h.dbSizeBytes, i18n.language)}</dd>
            <dt className="text-text-muted">{t('admin.health.postgres')}</dt>
            <dd className="m-0">{h.postgresVersion ?? '—'}</dd>
            <dt className="text-text-muted">{t('admin.health.uptime')}</dt>
            <dd className="tabular m-0">{formatUptime(h.uptimeSeconds)}</dd>
            <dt className="text-text-muted">{t('admin.health.heap')}</dt>
            <dd className="tabular m-0">
              {formatBytes(h.heapUsedBytes, i18n.language)} /{' '}
              {formatBytes(h.heapMaxBytes, i18n.language)}
            </dd>
            <dt className="text-text-muted">{t('admin.health.version')}</dt>
            <dd className="tabular m-0 truncate">
              {h.appVersion ?? '—'}
              {h.commit && <span className="text-text-muted"> · {h.commit.slice(0, 7)}</span>}
            </dd>
            <dt className="text-text-muted">{t('admin.health.starts')}</dt>
            <dd className="tabular m-0">{h.startsLast24h}</dd>
          </dl>
        )}
      </State>
    </Card>
  );
}

const PROVIDERS = [
  ['render', 'ADMIN_LINK_RENDER'],
  ['neon', 'ADMIN_LINK_NEON'],
  ['vercel', 'ADMIN_LINK_VERCEL'],
  ['backup', 'ADMIN_LINK_BACKUP'],
] as const;

/** O app não mede horas do Render, CU-horas do Neon nem tráfego da Vercel: só leva ao painel. */
export function ProvidersCard({ health }: { health?: AdminHealth }) {
  const { t } = useTranslation();
  return (
    <Card title={t('admin.providers.title')} aria-label={t('admin.providers.title')}>
      <p className="mb-4 text-sm text-text-secondary">{t('admin.providers.hint')}</p>
      <ul className="m-0 grid list-none gap-3 p-0 sm:grid-cols-2">
        {PROVIDERS.map(([key, envVar]) => {
          const url = health?.links[key];
          return (
            <li key={key} className="card-inner flex flex-col gap-1 px-4 py-3">
              <span className="font-semibold">{t(`admin.providers.${key}.name`)}</span>
              <span className="text-xs text-text-muted">{t(`admin.providers.${key}.what`)}</span>
              {url ? (
                <a
                  href={url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="mt-1 inline-flex min-h-11 items-center gap-1 text-sm font-semibold text-action hover:underline"
                >
                  {t('admin.providers.open')}
                  <span className="sr-only">
                    {' '}
                    {t(`admin.providers.${key}.name`)} {t('admin.providers.newTab')}
                  </span>
                  <Icon name="arrowRight" size={16} />
                </a>
              ) : (
                <span className="mt-1 text-xs text-text-secondary">
                  {t('admin.providers.notConfigured', { env: envVar })}
                </span>
              )}
            </li>
          );
        })}
      </ul>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// tabelas
// ---------------------------------------------------------------------------

function Table({
  caption,
  head,
  children,
}: {
  caption: string;
  head: string[];
  children: ReactNode;
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-left text-sm">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr className="border-b border-border">
            {head.map((h) => (
              <th key={h} scope="col" className="label px-3 py-2 font-[650] text-text-muted">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}

function dateTime(iso: string, locale: string) {
  return new Intl.DateTimeFormat(locale, { dateStyle: 'short', timeStyle: 'short' }).format(
    new Date(iso),
  );
}

export function SignupsCard({
  query,
}: {
  query: { data?: AdminSignup[]; isPending: boolean; isError: boolean; refetch: () => unknown };
}) {
  const { t, i18n } = useTranslation();
  return (
    <Card title={t('admin.signups.title')} aria-label={t('admin.signups.title')}>
      <State query={query} empty={query.data?.length === 0}>
        <Table
          caption={t('admin.signups.title')}
          head={[t('admin.signups.email'), t('admin.signups.course'), t('admin.signups.date')]}
        >
          {query.data?.map((s, i) => (
            <tr
              key={`${s.email}-${s.createdAt}-${i}`}
              className="border-b border-border/60 last:border-0"
            >
              <td className="tabular px-3 py-2">{s.email}</td>
              <td className="px-3 py-2 text-text-secondary">{s.course ?? '—'}</td>
              <td className="tabular px-3 py-2 text-text-secondary">
                {dateTime(s.createdAt, i18n.language)}
              </td>
            </tr>
          ))}
        </Table>
      </State>
    </Card>
  );
}

export function ErrorsCard({
  query,
}: {
  query: { data?: AdminErrorRow[]; isPending: boolean; isError: boolean; refetch: () => unknown };
}) {
  const { t, i18n } = useTranslation();
  return (
    <Card title={t('admin.errors.title')} aria-label={t('admin.errors.title')}>
      <State query={query}>
        {query.data?.length === 0 ? (
          <p className="flex items-center gap-2 text-sm text-text-secondary">
            <Icon name="check" size={18} className="text-action" />
            {t('admin.errors.none')}
          </p>
        ) : (
          <Table
            caption={t('admin.errors.title')}
            head={[
              t('admin.errors.route'),
              t('admin.errors.status'),
              t('admin.errors.count'),
              t('admin.errors.last'),
            ]}
          >
            {query.data?.map((e) => (
              <tr
                key={`${e.route}-${e.status}`}
                className="border-b border-border/60 last:border-0"
              >
                <td className="px-3 py-2 font-mono text-xs">{e.route}</td>
                <td className="px-3 py-2">
                  <Chip tone={statusTone(e.status)}>{e.status}</Chip>
                </td>
                <td className="tabular px-3 py-2">
                  {new Intl.NumberFormat(i18n.language).format(e.count)}
                </td>
                <td className="tabular px-3 py-2 text-text-secondary">
                  {dateTime(e.lastSeenAt, i18n.language)}
                </td>
              </tr>
            ))}
          </Table>
        )}
      </State>
    </Card>
  );
}
