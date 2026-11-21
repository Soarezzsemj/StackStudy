/** Iniciais do avatar: primeira letra do primeiro e do último nome ("Ada Lovelace" → "AL"). */
export function initials(name: string | undefined | null): string {
  const parts = (name ?? '').trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  const first = parts[0]!.charAt(0);
  const last = parts.length > 1 ? parts[parts.length - 1]!.charAt(0) : '';
  return (first + last).toLocaleUpperCase();
}
