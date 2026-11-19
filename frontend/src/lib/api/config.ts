/** Base da API. Em produção é relativa (`/api`), e a Vercel repassa para o Render. */
export const API_URL = ((import.meta.env.VITE_API_URL as string | undefined) || '/api').replace(
  /\/+$/,
  '',
);

/** URL opcional para "acordar" o backend no carregamento (ex.: health do Render). */
export const WAKE_URL = (import.meta.env.VITE_WAKE_URL as string | undefined) || '';

/** Depois desse tempo, a requisição conta como lenta e o aviso de "acordando" aparece. */
export const SLOW_REQUEST_MS = 3000;

/** Esperas entre as novas tentativas de um GET (backoff): até 3 tentativas extras. */
export const RETRY_DELAYS_MS = [1000, 2000, 4000] as const;

/** Status que indicam servidor dormindo, subindo ou em deploy. */
export const RETRYABLE_STATUS = new Set([502, 503, 504]);

/** Limite de espera do wake-up ping. Com ~0,1 CPU (Render free) o boot medido passou de 100 s. */
export const WAKE_TIMEOUT_MS = 150_000;
