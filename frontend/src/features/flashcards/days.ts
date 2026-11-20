/** Soma dias a uma data ISO (yyyy-mm-dd), sem fuso. */
export function addDays(iso: string, days: number): string {
  const d = new Date(`${iso}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/** `count` dias terminando em `end` (ISO), com zero nos dias sem dado. */
export function lastDays(
  end: string,
  count: number,
  data: { date: string; count: number }[],
): { date: string; count: number }[] {
  const byDate = new Map(data.map((d) => [d.date, d.count] as const));
  return Array.from({ length: count }, (_, i) => {
    const date = addDays(end, i - count + 1);
    return { date, count: byDate.get(date) ?? 0 };
  });
}
