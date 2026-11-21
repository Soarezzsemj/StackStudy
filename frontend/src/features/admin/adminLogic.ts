import type {
  AdminErrorRow,
  AdminHealth,
  AdminOverview,
  AdminSignup,
  Metric,
} from '@/lib/api/admin';

/*
 * Lógica pura do painel (sem React): variação, porcentagens, formatos e o CSV exportado no
 * cliente a partir do que já foi carregado (nenhuma chamada nova à API).
 */

/** Variação % em relação ao período anterior; null quando não dá para comparar (anterior 0). */
export function variation(m: Metric): number | null {
  if (m.previous === 0) return m.value === 0 ? 0 : null;
  return Math.round(((m.value - m.previous) / m.previous) * 1000) / 10;
}

/** Parte de um total em % (1 casa); 0 quando o total é 0. */
export function pct(part: number, total: number): number {
  if (total <= 0) return 0;
  return Math.round((part / total) * 1000) / 10;
}

export function retentionRate(o: AdminOverview['retentionD7']): {
  value: number | null;
  previous: number | null;
} {
  return {
    value: o.cohort > 0 ? pct(o.retained, o.cohort) : null,
    previous: o.previousCohort > 0 ? pct(o.previousRetained, o.previousCohort) : null,
  };
}

/** Pontos percentuais entre duas taxas (para a retenção). */
export function pointsDelta(value: number | null, previous: number | null): number | null {
  if (value === null || previous === null) return null;
  return Math.round((value - previous) * 10) / 10;
}

export function formatBytes(bytes: number | undefined, locale: string): string {
  if (bytes === undefined) return '—';
  const units = ['B', 'KB', 'MB', 'GB'];
  let v = bytes;
  let i = 0;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i++;
  }
  return `${new Intl.NumberFormat(locale, { maximumFractionDigits: i === 0 ? 0 : 1 }).format(v)} ${units[i]}`;
}

/** Uptime curto: "3 d 4 h", "2 h 15 min", "45 min". */
export function formatUptime(seconds: number): string {
  const d = Math.floor(seconds / 86400);
  const h = Math.floor((seconds % 86400) / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (d > 0) return `${d} d ${h} h`;
  if (h > 0) return `${h} h ${m} min`;
  return `${m} min`;
}

/** Status HTTP → tom de cor: 5xx é erro do servidor (vermelho), 4xx é aviso. */
export function statusTone(status: number): 'danger' | 'warning' {
  return status >= 500 ? 'danger' : 'warning';
}

/** Campo de CSV (RFC 4180) e proteção contra fórmula de planilha (=, +, -, @). */
function cell(value: unknown): string {
  let s = value === null || value === undefined ? '' : String(value);
  if (/^[=+\-@]/.test(s)) s = `'${s}`;
  return /[",\n\r;]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

function rows(lines: unknown[][]): string {
  return lines.map((l) => l.map(cell).join(',')).join('\r\n');
}

export interface ExportData {
  overview?: AdminOverview;
  health?: AdminHealth;
  signups?: AdminSignup[];
  errors?: AdminErrorRow[];
}

/** CSV com uma seção por bloco do painel, só com o que está na tela. */
export function buildCsv(d: ExportData): string {
  const parts: string[] = [];
  const o = d.overview;
  if (o) {
    const r = retentionRate(o.retentionD7);
    parts.push(
      rows([
        ['metric', 'value', 'previous'],
        ['range', o.range, ''],
        ['generated_at', o.generatedAt, ''],
        ['total_users', o.totalUsers, ''],
        ['new_users', o.newUsers.value, o.newUsers.previous],
        ['online_now', o.onlineNow, ''],
        ['daily_active', o.dailyActive.value, o.dailyActive.previous],
        ['weekly_active', o.weeklyActive.value, o.weeklyActive.previous],
        ['retention_d7_pct', r.value ?? '', r.previous ?? ''],
        ['focus_hours_per_active', o.focusHoursPerActive, ''],
      ]),
      rows([
        ['day', 'active_users', 'signups'],
        ...o.series.map((s) => [s.day, s.activeUsers, s.signups]),
      ]),
      rows([
        ['funnel_step', 'users'],
        ['registered', o.funnel.registered],
        ['first_subject', o.funnel.firstSubject],
        ['first_focus_session', o.funnel.firstFocusSession],
        ['returned_7d', o.funnel.returned7d],
      ]),
      rows([
        ['feature', 'weekly_active_users', 'pct'],
        ['stack', o.usage.stack, pct(o.usage.stack, o.usage.weeklyActive)],
        ['deep_work', o.usage.deepWork, pct(o.usage.deepWork, o.usage.weeklyActive)],
        ['flashcards', o.usage.flashcards, pct(o.usage.flashcards, o.usage.weeklyActive)],
        ['grades', o.usage.grades, pct(o.usage.grades, o.usage.weeklyActive)],
      ]),
    );
  }
  if (d.health) {
    const h = d.health;
    parts.push(
      rows([
        ['health', 'value'],
        ['db_up', h.dbUp],
        ['db_latency_ms', h.dbLatencyMs ?? ''],
        ['db_size_bytes', h.dbSizeBytes ?? ''],
        ['postgres_version', h.postgresVersion ?? ''],
        ['uptime_seconds', h.uptimeSeconds],
        ['heap_used_bytes', h.heapUsedBytes],
        ['heap_max_bytes', h.heapMaxBytes],
        ['app_version', h.appVersion ?? ''],
        ['commit', h.commit ?? ''],
        ['starts_last_24h', h.startsLast24h],
      ]),
    );
  }
  if (d.signups) {
    parts.push(
      rows([
        ['email_masked', 'course', 'created_at'],
        ...d.signups.map((s) => [s.email, s.course ?? '', s.createdAt]),
      ]),
    );
  }
  if (d.errors) {
    parts.push(
      rows([
        ['route', 'status', 'count', 'last_seen_at'],
        ...d.errors.map((e) => [e.route, e.status, e.count, e.lastSeenAt]),
      ]),
    );
  }
  return parts.join('\r\n\r\n') + '\r\n';
}

export function downloadCsv(content: string, fileName: string) {
  // BOM: o Excel abre acentos certos.
  const blob = new Blob(['﻿', content], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
