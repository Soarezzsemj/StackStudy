/** Horas com uma casa decimal no idioma da interface (ex.: "12,5"). */
export function formatHours(seconds: number, locale: string): string {
  return new Intl.NumberFormat(locale, { maximumFractionDigits: 1 }).format(seconds / 3600);
}

/** Duração curta para listas e tooltips: "45 min", "2 h", "1 h 30 min". */
export function formatDuration(seconds: number): string {
  const totalMinutes = Math.round(Math.max(0, seconds) / 60);
  const h = Math.floor(totalMinutes / 60);
  const m = totalMinutes % 60;
  if (h === 0) return `${m} min`;
  return m === 0 ? `${h} h` : `${h} h ${m} min`;
}

/** Data ISO (sem fuso) por extenso curto: "seg., 3 de mar.". */
export function formatDay(isoDate: string, locale: string): string {
  const [y, m, d] = isoDate.split('-').map(Number);
  return new Intl.DateTimeFormat(locale, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  }).format(new Date(y ?? 1970, (m ?? 1) - 1, d ?? 1));
}
