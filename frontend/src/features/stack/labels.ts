import { SUBJECT_COLORS } from '@/lib/api/stack';

const COLOR_KEYS = ['blue', 'amber', 'purple', 'cyan', 'pink', 'lime'] as const;

/** Chave de tradução do nome da cor (`stack.subjects.colors.*`), para aria-label. */
export function colorKey(color: string): string {
  const index = SUBJECT_COLORS.indexOf(color.toLowerCase() as (typeof SUBJECT_COLORS)[number]);
  return `stack.subjects.colors.${COLOR_KEYS[index] ?? 'blue'}`;
}

/** Formata uma data `YYYY-MM-DD` (sem fuso) no idioma da interface. */
export function formatLocalDate(value: string, locale: string): string {
  const [y, m, d] = value.split('-').map(Number);
  return new Intl.DateTimeFormat(locale, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(new Date(y ?? 1970, (m ?? 1) - 1, d ?? 1));
}
