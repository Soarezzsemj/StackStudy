/**
 * O arquivo de uma tela (code splitting) não baixou: em geral, um deploy novo tirou a versão
 * antiga do ar enquanto a aba estava aberta. Recarregar resolve.
 */
export function isChunkLoadError(error: unknown): boolean {
  if (!(error instanceof Error)) return false;
  return (
    error.name === 'ChunkLoadError' ||
    /Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module/i.test(
      error.message,
    )
  );
}

/**
 * Destino depois do login: só caminhos internos ("/stack"). Barra dupla, barra invertida ou URL
 * completa viram "/" (evita redirecionar para outro site, inclusive o bypass do React Router 6 com
 * "\" — GHSA-wrjc-x8rr-h8h6).
 */
export function safeRedirect(path: unknown): string {
  if (typeof path !== 'string' || !path.startsWith('/')) return '/';
  if (path.startsWith('//') || path.includes('\\') || /^\/+[a-z][a-z0-9+.-]*:/i.test(path)) {
    return '/';
  }
  return path;
}
