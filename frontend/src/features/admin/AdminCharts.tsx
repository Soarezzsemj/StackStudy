import { useTranslation } from 'react-i18next';
import {
  Area,
  AreaChart,
  Bar,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { AdminOverview } from '@/lib/api/admin';

/*
 * Gráficos do painel (Recharts). O SVG é decorativo: cada gráfico fica num role="img" com um
 * aria-label que já diz os números (e a tabela/CSV têm os dados completos).
 */

const AXIS = { stroke: 'var(--text-muted)', fontSize: 12 } as const;
const TOOLTIP = {
  contentStyle: {
    background: 'var(--surface-raised)',
    border: '1px solid var(--border)',
    borderRadius: 12,
    color: 'var(--text)',
  },
  labelStyle: { color: 'var(--text-secondary)' },
} as const;

function dayLabel(iso: string, locale: string): string {
  const [y, m, d] = iso.split('-').map(Number);
  return new Intl.DateTimeFormat(locale, {
    day: '2-digit',
    month: '2-digit',
    timeZone: 'UTC',
  }).format(new Date(Date.UTC(y!, m! - 1, d!)));
}

/** Mini gráfico do cartão "online agora": ativos por dia, sem eixos. */
export function Sparkline({ series }: { series: AdminOverview['series'] }) {
  const { t } = useTranslation();
  const values = series.map((s) => s.activeUsers);
  return (
    <div
      role="img"
      aria-label={t('admin.charts.sparkline', {
        days: series.length,
        min: values.length ? Math.min(...values) : 0,
        max: values.length ? Math.max(...values) : 0,
      })}
      className="h-14 w-full"
    >
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={series} margin={{ top: 4, right: 0, bottom: 0, left: 0 }}>
          <defs>
            <linearGradient id="spark" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="var(--action)" stopOpacity={0.4} />
              <stop offset="100%" stopColor="var(--action)" stopOpacity={0} />
            </linearGradient>
          </defs>
          <Area
            type="monotone"
            dataKey="activeUsers"
            stroke="var(--action)"
            strokeWidth={2}
            fill="url(#spark)"
            isAnimationActive={false}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

/** Usuários ativos por dia (linha) com os novos cadastros (barras). */
export function ActivityChart({ series }: { series: AdminOverview['series'] }) {
  const { t, i18n } = useTranslation();
  const active = series.reduce((sum, s) => sum + s.activeUsers, 0);
  const signups = series.reduce((sum, s) => sum + s.signups, 0);
  const peak = series.reduce((m, s) => Math.max(m, s.activeUsers), 0);
  const data = series.map((s) => ({ ...s, label: dayLabel(s.day, i18n.language) }));
  return (
    <div
      role="img"
      aria-label={t('admin.charts.activity', { days: series.length, peak, signups, active })}
      className="h-64 w-full"
    >
      <ResponsiveContainer width="100%" height="100%">
        <ComposedChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
          <CartesianGrid stroke="var(--border)" vertical={false} />
          <XAxis dataKey="label" tick={AXIS} tickLine={false} axisLine={false} />
          <YAxis allowDecimals={false} tick={AXIS} tickLine={false} axisLine={false} />
          <Tooltip {...TOOLTIP} />
          <Legend wrapperStyle={{ color: 'var(--text-secondary)', fontSize: 12 }} />
          <Bar
            dataKey="signups"
            name={t('admin.charts.signups')}
            fill="var(--space-glow)"
            radius={[6, 6, 0, 0]}
            barSize={14}
            isAnimationActive={false}
          />
          <Line
            type="monotone"
            dataKey="activeUsers"
            name={t('admin.charts.active')}
            stroke="var(--action)"
            strokeWidth={2.5}
            dot={false}
            isAnimationActive={false}
          />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}
