import { describe, expect, it } from 'vitest';
import { api, ApiError, refreshSession } from './client';
import { authApi } from './auth';
import { useAuthStore } from '@/stores/authStore';
import { authBody, jsonResponse, mockFetch, testUser } from '@/test/utils';

describe('api client', () => {
  it('sends the access token and parses JSON', async () => {
    useAuthStore.getState().setSession('token-1', testUser);
    const fetchMock = mockFetch(async () => jsonResponse(200, { ok: true }));

    await expect(api.get('/me')).resolves.toEqual({ ok: true });

    const init = fetchMock.mock.calls[0]![1]!;
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer token-1');
    expect(init.credentials).toBe('include');
  });

  it('on 401 refreshes the session once and retries with the new token', async () => {
    useAuthStore.getState().setSession('expired', testUser);
    const fetchMock = mockFetch(async (url, init) => {
      if (url.endsWith('/api/auth/refresh')) return jsonResponse(200, authBody('fresh'));
      const auth = (init?.headers as Record<string, string>).Authorization;
      return auth === 'Bearer fresh'
        ? jsonResponse(200, testUser)
        : jsonResponse(401, { code: 'UNAUTHORIZED', message: 'Authentication required' });
    });

    await expect(api.get('/me')).resolves.toEqual(testUser);

    expect(fetchMock.mock.calls.map(([u]) => String(u))).toEqual([
      '/api/me',
      '/api/auth/refresh',
      '/api/me',
    ]);
    expect(useAuthStore.getState().accessToken).toBe('fresh');
    expect(useAuthStore.getState().status).toBe('authenticated');
  });

  it('concurrent 401s share a single refresh request (rotation-safe)', async () => {
    useAuthStore.getState().setSession('expired', testUser);
    const fetchMock = mockFetch(async (url, init) => {
      if (url.endsWith('/api/auth/refresh')) return jsonResponse(200, authBody('fresh'));
      const auth = (init?.headers as Record<string, string>).Authorization;
      return auth === 'Bearer fresh' ? jsonResponse(200, { url }) : jsonResponse(401, {});
    });

    await Promise.all([api.get('/a'), api.get('/b'), api.get('/c')]);

    const refreshCalls = fetchMock.mock.calls.filter(([u]) => String(u).endsWith('/refresh'));
    expect(refreshCalls).toHaveLength(1);
  });

  it('when refresh fails, clears the session and surfaces the 401', async () => {
    useAuthStore.getState().setSession('expired', testUser);
    mockFetch(async (url) =>
      url.endsWith('/api/auth/refresh')
        ? jsonResponse(401, { code: 'INVALID_REFRESH_TOKEN', message: 'nope' })
        : jsonResponse(401, { code: 'UNAUTHORIZED', message: 'Authentication required' }),
    );

    const error = await api.get('/me').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).code).toBe('UNAUTHORIZED');
    expect(useAuthStore.getState().status).toBe('anonymous');
    expect(useAuthStore.getState().accessToken).toBeNull();
  });

  it('maps error bodies to ApiError with code and details', async () => {
    mockFetch(async () =>
      jsonResponse(400, {
        code: 'VALIDATION_ERROR',
        message: 'Request validation failed',
        details: [{ field: 'email', message: 'must be a well-formed email address' }],
      }),
    );

    const error = (await api.post('/auth/register', {}).catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(400);
    expect(error.code).toBe('VALIDATION_ERROR');
    expect(error.details[0]?.field).toBe('email');
  });

  it('turns network failures into NETWORK_ERROR (POST: no retry)', async () => {
    mockFetch(async () => {
      throw new TypeError('Failed to fetch');
    });
    const error = (await api.post('/auth/logout').catch((e: unknown) => e)) as ApiError;
    expect(error.code).toBe('NETWORK_ERROR');
    expect(error.status).toBe(0);
  });
});

describe('auth api', () => {
  it('login stores the session; logout clears it', async () => {
    const fetchMock = mockFetch(async (url) =>
      url.endsWith('/logout')
        ? new Response(null, { status: 204 })
        : jsonResponse(200, authBody('t1')),
    );

    await authApi.login({ email: 'ada@example.com', password: 'secret-123' });
    expect(useAuthStore.getState()).toMatchObject({ status: 'authenticated', accessToken: 't1' });
    // Login é público: não envia o header Bearer.
    const loginHeaders = fetchMock.mock.calls[0]![1]!.headers as Record<string, string>;
    expect(loginHeaders.Authorization).toBeUndefined();

    await authApi.logout();
    expect(useAuthStore.getState()).toMatchObject({ status: 'anonymous', accessToken: null });
  });

  it('bootstrap marks the user anonymous when there is no refresh cookie', async () => {
    mockFetch(async () => jsonResponse(401, { code: 'INVALID_REFRESH_TOKEN', message: 'x' }));
    await expect(refreshSession()).resolves.toBeNull();
    expect(useAuthStore.getState().status).toBe('anonymous');
  });
});
