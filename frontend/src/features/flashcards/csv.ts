import { CSV_MAX_CHARS } from '@/lib/api/flashcards';

/** Tags digitadas ("a b, c;d") → lista normalizada (minúsculas, sem repetir), como no backend. */
export function parseTags(text: string): string[] {
  const out: string[] = [];
  for (const part of text.split(/[\s,;]+/)) {
    const tag = part.trim().toLowerCase();
    if (tag && !out.includes(tag)) out.push(tag);
  }
  return out;
}

/** Arquivo grande demais para importar (o backend recusa acima disso). */
export function csvTooLarge(text: string): boolean {
  return text.length > CSV_MAX_CHARS;
}

/** Baixa um texto como arquivo (CSV exportado). */
export function downloadText(fileName: string, content: string, type = 'text/csv;charset=utf-8') {
  // BOM para o Excel abrir acentos corretamente.
  const blob = new Blob(['﻿', content], { type });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
