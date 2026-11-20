/** Número da API → valor de campo (vazio quando não há). */
export function toInput(value: number | undefined): string {
  return value === undefined ? '' : String(value);
}

/**
 * Valor de um campo numérico → número ou null (vazio). Inválido também vira null; quem valida de
 * verdade é o backend, que devolve o erro por campo.
 */
export function numberOrNull(text: string): number | null {
  const trimmed = text.trim().replace(',', '.');
  if (trimmed === '') return null;
  const value = Number(trimmed);
  return Number.isFinite(value) ? value : null;
}
