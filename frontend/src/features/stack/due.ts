// Prazo das tarefas: o backend guarda um instante (UTC) e a flag "dia inteiro".
// Sem hora, mandamos o fim do dia local (23:59:59): a tarefa só atrasa quando o dia acaba.

const pad = (n: number) => String(n).padStart(2, '0');

export interface DueValue {
  dueAt: string | null;
  dueAllDay: boolean;
}

/** `date` no formato do `<input type="date">` e `time` do `<input type="time">` (ou vazio). */
export function toDue(date: string, time: string): DueValue {
  const d = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
  if (!d) return { dueAt: null, dueAllDay: false };
  const [y, m, day] = [Number(d[1]), Number(d[2]) - 1, Number(d[3])];
  const t = /^(\d{2}):(\d{2})$/.exec(time);
  if (t) {
    return {
      dueAt: new Date(y, m, day, Number(t[1]), Number(t[2])).toISOString(),
      dueAllDay: false,
    };
  }
  return { dueAt: new Date(y, m, day, 23, 59, 59).toISOString(), dueAllDay: true };
}

/** Caminho inverso, para preencher o formulário de edição no fuso local. */
export function fromDue(
  dueAt: string | undefined,
  dueAllDay: boolean,
): { date: string; time: string } {
  if (!dueAt) return { date: '', time: '' };
  const d = new Date(dueAt);
  const date = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  return { date, time: dueAllDay ? '' : `${pad(d.getHours())}:${pad(d.getMinutes())}` };
}

/** Data local de hoje no formato do `<input type="date">`. */
export function todayInput(now: Date = new Date()): string {
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/** Texto curto do prazo, no idioma da interface (ex.: "qua., 26 de nov. · 14:00"). */
export function formatDue(dueAt: string, dueAllDay: boolean, locale: string): string {
  const d = new Date(dueAt);
  const date = new Intl.DateTimeFormat(locale, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  }).format(d);
  if (dueAllDay) return date;
  const time = new Intl.DateTimeFormat(locale, { hour: '2-digit', minute: '2-digit' }).format(d);
  return `${date} · ${time}`;
}
