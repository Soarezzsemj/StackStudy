import type { Preferences, PreferencesPatch } from '@/lib/api/account';

/** Baixa um objeto como arquivo JSON (exportação de dados). */
export function downloadJson(data: unknown, fileName: string) {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export function exportFileName(today = new Date()): string {
  return `stackstudy-export-${today.toISOString().slice(0, 10)}.json`;
}

/** Navegador e sistema a partir do User-Agent, só para reconhecer o dispositivo. */
export function describeUserAgent(ua: string | undefined): { browser?: string; os?: string } {
  if (!ua) return {};
  const browser = /Edg\//.test(ua)
    ? 'Edge'
    : /OPR\/|Opera/.test(ua)
      ? 'Opera'
      : /Firefox\//.test(ua)
        ? 'Firefox'
        : /Chrome\//.test(ua)
          ? 'Chrome'
          : /Safari\//.test(ua)
            ? 'Safari'
            : undefined;
  const os = /Android/.test(ua)
    ? 'Android'
    : /iPhone|iPad|iPod/.test(ua)
      ? 'iOS'
      : /Windows/.test(ua)
        ? 'Windows'
        : /Mac OS X|Macintosh/.test(ua)
          ? 'macOS'
          : /Linux/.test(ua)
            ? 'Linux'
            : undefined;
  return { browser, os };
}

const FALLBACK_ZONES = [
  'America/Sao_Paulo',
  'America/Manaus',
  'America/Belem',
  'America/Fortaleza',
  'America/Recife',
  'America/Cuiaba',
  'America/Rio_Branco',
  'America/Noronha',
  'Europe/Lisbon',
  'UTC',
];

/** Fusos IANA que o navegador conhece (ou uma lista curta, se ele não souber listar). */
export function timeZoneOptions(): string[] {
  try {
    const intl = Intl as unknown as { supportedValuesOf?: (key: string) => string[] };
    const all = intl.supportedValuesOf?.('timeZone');
    if (all && all.length > 0) return all.includes('UTC') ? all : [...all, 'UTC'];
  } catch {
    // navegador antigo
  }
  return FALLBACK_ZONES;
}

/** Campos do formulário de preferências (texto, como nos inputs). */
export interface PreferencesForm {
  locale: string;
  /** '' = automático (navegador). */
  timeZone: string;
  gradeMethod: Preferences['gradeMethod'];
  gradeScaleMax: string;
  passingGrade: string;
  maxAbsencePct: string;
  focusMinutes: string;
  shortBreakMinutes: string;
  longBreakMinutes: string;
  longBreakEvery: string;
  /** Meta semanal em horas (a API guarda minutos). */
  weeklyGoalHours: string;
  newCardsPerDay: string;
  reviewsPerDay: string;
}

export function toPreferencesForm(p: Preferences): PreferencesForm {
  return {
    locale: p.locale,
    timeZone: p.timeZone ?? '',
    gradeMethod: p.gradeMethod,
    gradeScaleMax: String(p.gradeScaleMax),
    passingGrade: String(p.passingGrade),
    maxAbsencePct: String(p.maxAbsencePct),
    focusMinutes: String(p.focusMinutes),
    shortBreakMinutes: String(p.shortBreakMinutes),
    longBreakMinutes: String(p.longBreakMinutes),
    longBreakEvery: String(p.longBreakEvery),
    weeklyGoalHours: String(Math.round((p.weeklyGoalMinutes / 60) * 100) / 100),
    newCardsPerDay: String(p.newCardsPerDay),
    reviewsPerDay: String(p.reviewsPerDay),
  };
}

/** Texto do campo → número (vírgula ou ponto). Inválido vira NaN e o backend recusa. */
function num(text: string): number {
  const t = text.trim().replace(',', '.');
  return t === '' ? Number.NaN : Number(t);
}

/**
 * Formulário → PATCH. Manda todos os campos (o backend valida faixas e regras entre eles);
 * número inválido vai como null e não muda o valor salvo — por isso é checado antes, em
 * `invalidNumberFields`.
 */
export function toPreferencesPatch(f: PreferencesForm): PreferencesPatch {
  const n = (text: string) => {
    const v = num(text);
    return Number.isFinite(v) ? v : undefined;
  };
  const hours = n(f.weeklyGoalHours);
  return {
    locale: f.locale,
    timeZone: f.timeZone,
    gradeMethod: f.gradeMethod,
    gradeScaleMax: n(f.gradeScaleMax),
    passingGrade: n(f.passingGrade),
    maxAbsencePct: n(f.maxAbsencePct),
    focusMinutes: n(f.focusMinutes),
    shortBreakMinutes: n(f.shortBreakMinutes),
    longBreakMinutes: n(f.longBreakMinutes),
    longBreakEvery: n(f.longBreakEvery),
    weeklyGoalMinutes: hours === undefined ? undefined : Math.round(hours * 60),
    newCardsPerDay: n(f.newCardsPerDay),
    reviewsPerDay: n(f.reviewsPerDay),
  };
}

/** Campos numéricos vazios ou que não são número (validação no cliente, antes de enviar). */
export function invalidNumberFields(f: PreferencesForm): (keyof PreferencesForm)[] {
  const numeric: (keyof PreferencesForm)[] = [
    'gradeScaleMax',
    'passingGrade',
    'maxAbsencePct',
    'focusMinutes',
    'shortBreakMinutes',
    'longBreakMinutes',
    'longBreakEvery',
    'weeklyGoalHours',
    'newCardsPerDay',
    'reviewsPerDay',
  ];
  return numeric.filter((k) => !Number.isFinite(num(f[k])));
}
