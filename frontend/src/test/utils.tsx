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
