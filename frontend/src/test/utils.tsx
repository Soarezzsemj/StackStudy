import type { ReactElement } from 'react';
import { render } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router-dom';
import { vi } from 'vitest';
import type { User } from '@/lib/api/types';

export const testUser: User = {
  id: '00000000-0000-0000-0000-000000000001',
  name: 'Ada Lovelace',
  email: 'ada@example.com',
  locale: 'pt-BR',
  createdAt: '2026-11-19T12:00:00Z',
};

export function jsonResponse(status: number, body?: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

export function authBody(token: string, user: User = testUser) {
  return { accessToken: token, tokenType: 'Bearer', expiresIn: 900, user };
}

/** Troca o fetch global por um mock e devolve o mock. */
export function mockFetch(impl: (url: string, init?: RequestInit) => Promise<Response>) {
  const fn = vi.fn((input: RequestInfo | URL, init?: RequestInit) => impl(String(input), init));
  vi.stubGlobal('fetch', fn);
  return fn;
}

type Handler = (init?: RequestInit, url?: string) => Response | Promise<Response>;

export const emptyPage = {
  items: [],
  page: 0,
  size: 100,
  totalElements: 0,
  totalPages: 0,
  hasNext: false,
};

/**
 * Mock de fetch roteado por `"MÉTODO /caminho"` (sem a query). Listas do The Stack sem handler
 * respondem vazio; o resto responde 404.
 */
export function mockApi(handlers: Record<string, Handler> = {}) {
  return mockFetch(async (url, init) => {
    const method = init?.method ?? 'GET';
    const path = url.split('?')[0] ?? url;
    const handler = handlers[`${method} ${path}`];
    if (handler) return handler(init, url);
    if (method === 'GET' && (path === '/api/semesters' || path === '/api/subjects')) {
      return jsonResponse(200, []);
    }
    if (method === 'GET' && path === '/api/tasks') return jsonResponse(200, emptyPage);
    return jsonResponse(404, { code: 'NOT_FOUND', message: 'Not found' });
  });
}

export function renderWithProviders(
  routes: RouteObject[],
  { initialEntries = ['/'] }: { initialEntries?: string[] } = {},
) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const router = createMemoryRouter(routes, { initialEntries });
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return { ...utils, router, queryClient };
}

export function renderUi(ui: ReactElement) {
  return render(ui);
}
