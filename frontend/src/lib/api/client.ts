import { useAuthStore } from '@/stores/authStore';
import { API_URL, RETRY_DELAYS_MS, RETRYABLE_STATUS } from './config';
import { sleep, trackSlow } from './slow';
import type { ApiErrorBody, AuthResponse, FieldProblem } from './types';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: FieldProblem[];

  constructor(status: number, body: Partial<ApiErrorBody>) {
    super(body.message ?? `Request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.code = body.code ?? (status === 0 ? 'NETWORK_ERROR' : 'UNKNOWN');
    this.details = body.details ?? [];
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  /** Envia o access token e renova sozinho no 401. Padrão: true. */
  auth?: boolean;
  signal?: AbortSignal;
}

/** Caminho relativo à API: `/me` vira `/api/me`. */
export function apiUrl(path: string) {
  return `${API_URL}${path.startsWith('/') ? path : `/${path}`}`;
}

async function rawRequest(path: string, options: RequestOptions, token: string | null) {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;
  try {
    return await fetch(apiUrl(path), {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      credentials: 'include',
      signal: options.signal,
    });
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') throw err;
    throw new ApiError(0, { code: 'NETWORK_ERROR', message: 'Network error' });
  }
}

/**
 * Faz a requisição e, só para GET, tenta de novo com backoff em falha de rede ou 502/503/504
 * (servidor dormindo ou em deploy). POST/PUT/DELETE nunca repetem: podem ter efeito colateral.
 */
async function sendWithRetry(path: string, options: RequestOptions, token: string | null) {
  const retryable = (options.method ?? 'GET') === 'GET';
  for (let attempt = 0; ; attempt++) {
    const canRetry = retryable && attempt < RETRY_DELAYS_MS.length;
    try {
      const res = await rawRequest(path, options, token);
      if (!canRetry || !RETRYABLE_STATUS.has(res.status)) return res;
    } catch (err) {
      if (!canRetry || !(err instanceof ApiError && err.status === 0)) throw err;
    }
    await sleep(RETRY_DELAYS_MS[attempt]!, options.signal);
  }
}

async function parse<T>(res: Response): Promise<T> {
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  let data: unknown;
  try {
    data = text ? JSON.parse(text) : undefined;
  } catch {
    // Corpo que não é JSON (ex.: página de erro do proxy).
    data = undefined;
  }
  if (!res.ok) throw new ApiError(res.status, (data ?? {}) as Partial<ApiErrorBody>);
  return data as T;
}

let refreshInFlight: Promise<string | null> | null = null;

/**
 * Troca o cookie de refresh por um novo access token.
 * Chamadas ao mesmo tempo usam uma só requisição, porque o backend troca o token a cada refresh
 * e trata reuso como roubo. Por isso o refresh nunca é repetido automaticamente.
 */
export function refreshSession(): Promise<string | null> {
  if (!refreshInFlight) {
    refreshInFlight = trackSlow(
      (async () => {
        try {
          const res = await rawRequest('/auth/refresh', { method: 'POST' }, null);
          if (!res.ok) {
            useAuthStore.getState().clear();
            return null;
          }
          const data = (await res.json()) as AuthResponse;
          useAuthStore.getState().setSession(data.accessToken, data.user);
          return data.accessToken;
        } catch {
          useAuthStore.getState().clear();
          return null;
        }
      })(),
    ).finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

async function request<T>(path: string, options: RequestOptions): Promise<T> {
  const useAuth = options.auth ?? true;
  const token = useAuth ? useAuthStore.getState().accessToken : null;
  let res = await sendWithRetry(path, options, token);

  if (res.status === 401 && useAuth) {
    const fresh = await refreshSession();
    if (!fresh) return parse<T>(res);
    res = await sendWithRetry(path, options, fresh);
    if (res.status === 401) useAuthStore.getState().clear();
  }
  return parse<T>(res);
}

/**
 * Requisição JSON para a API (`path` relativo, ex.: `/me`). No 401, renova a sessão uma vez e
 * tenta de novo. Se demorar, mostra o aviso de servidor acordando.
 */
export function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return trackSlow(request<T>(path, options));
}

export const api = {
  get: <T>(path: string, signal?: AbortSignal) => apiRequest<T>(path, { signal }),
  post: <T>(path: string, body?: unknown) => apiRequest<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown) => apiRequest<T>(path, { method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown) => apiRequest<T>(path, { method: 'PATCH', body }),
  delete: <T>(path: string) => apiRequest<T>(path, { method: 'DELETE' }),
};

/** Só para testes: limpa o estado do módulo. */
export function __resetClientForTests() {
  refreshInFlight = null;
}
