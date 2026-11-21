import type { ChipTone, IconName } from '@/components/ui';
import type { AttendanceStatus, Standing } from '@/lib/api/grades';

/** Nota com 2 casas no idioma da interface; sem valor vira travessão. */
export function formatGrade(value: number | null | undefined, locale: string): string {
  if (value === null || value === undefined) return '—';
  return new Intl.NumberFormat(locale, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
}

/** Número curto (pesos, %, limites): até 2 casas, sem zeros sobrando. */
export function formatNumber(value: number | null | undefined, locale: string): string {
  if (value === null || value === undefined) return '—';
  return new Intl.NumberFormat(locale, { maximumFractionDigits: 2 }).format(value);
}

/** Situação → cor: aprovando (lima), em risco (aviso), impossível/reprovado (erro). */
export function standingTone(standing: Standing): ChipTone {
  switch (standing) {
    case 'PASSED':
    case 'SECURED':
    case 'ON_TRACK':
      return 'action';
    case 'AT_RISK':
    case 'EXAM':
      return 'warning';
    case 'IMPOSSIBLE':
    case 'FAILED':
      return 'danger';
    default:
      return 'neutral';
  }
}

/** Ícone da situação, para não depender só da cor. */
export function standingIcon(standing: Standing): IconName {
  switch (standing) {
    case 'PASSED':
    case 'SECURED':
      return 'check';
    case 'AT_RISK':
    case 'EXAM':
      return 'alert';
    case 'IMPOSSIBLE':
    case 'FAILED':
      return 'ban';
    default:
      return 'target';
  }
}

export function attendanceTone(status: AttendanceStatus): ChipTone {
  switch (status) {
    case 'OK':
      return 'action';
    case 'WARNING':
      return 'warning';
    case 'OVER':
      return 'danger';
    default:
      return 'neutral';
  }
}

/** Cor (variável CSS) de uma faixa de risco, para barras e textos. */
export function toneColor(tone: ChipTone): string | undefined {
  if (tone === 'warning') return 'var(--warning)';
  if (tone === 'danger') return 'var(--danger)';
  if (tone === 'action') return 'var(--action)';
  return 'var(--border-control)';
}
