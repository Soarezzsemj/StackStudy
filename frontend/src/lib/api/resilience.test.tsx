import { describe, expect, it, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import { api, ApiError } from './client';
import { authApi } from './auth';
import { wakeServer } from './wake';
import { ServerWakeNotice } from '@/components/shell/ServerWakeNotice';
import { useNetworkStore } from '@/stores/networkStore';
import { jsonResponse, mockFetch } from '@/test/utils';

const WAKING = 'Acordando o servidor… isso pode levar até 1 minuto.';

describe('retry com backoff', () => {
  it('GET repete em 502/503/504 e devolve a resposta boa', async () => {
    vi.useFakeTimers();
    const statuses = [503, 502, 200];
    const fetchMock = mockFetch(async () => {
      const status = statuses.shift()!;
      return status === 200 ? jsonResponse(200, { ok: true }) : new Response('', { status });
    });

    const result = api.get('/health-check');
    await vi.advanceTimersByTimeAsync(1000); // 1ª espera
    await vi.advanceTimersByTimeAsync(2000); // 2ª espera

    await expect(result).resolves.toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('GET repete em falha de rede e desiste depois de 3 tentativas extras', async () => {
    vi.useFakeTimers();
    const fetchMock = mockFetch(async () => {
      throw new TypeError('Failed to fetch');
    });

    const result = api.get('/me').catch((e: unknown) => e);
    await vi.advanceTimersByTimeAsync(1000 + 2000 + 4000);

    const error = (await result) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe('NETWORK_ERROR');
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it('respeita o tempo de backoff antes de tentar de novo', async () => {
    vi.useFakeTimers();
    const fetchMock = mockFetch(async () => new Response('', { status: 504 }));

    void api.get('/me').catch(() => undefined);
    await vi.advanceTimersByTimeAsync(999);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('POST nunca repete, mesmo com 503', async () => {
    const fetchMock = mockFetch(async () => new Response('', { status: 503 }));

    const error = (await api.post('/auth/login', {}).catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(503);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('GET não repete em erros que não são de disponibilidade (ex.: 500, 404)', async () => {
    const fetchMock = mockFetch(async () => jsonResponse(500, { code: 'INTERNAL_ERROR' }));

    await expect(api.get('/me')).rejects.toMatchObject({ status: 500 });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});

describe('aviso de servidor acordando', () => {
  it('aparece só depois de 3 s e some quando a resposta chega', async () => {
    vi.useFakeTimers();
    let respond!: (res: Response) => void;
    mockFetch(() => new Promise<Response>((resolve) => (respond = resolve)));
    render(<ServerWakeNotice />);

    const pending = api.get('/me');
    await act(() => vi.advanceTimersByTimeAsync(2999));
    expect(screen.queryByText(WAKING)).not.toBeInTheDocument();

    await act(() => vi.advanceTimersByTimeAsync(1));
    expect(screen.getByRole('status')).toHaveTextContent(WAKING);

    await act(async () => {
      respond(jsonResponse(200, { ok: true }));
      await pending;
    });
    expect(screen.queryByText(WAKING)).not.toBeInTheDocument();
    expect(useNetworkStore.getState().slowRequests).toBe(0);
  });

  it('não aparece para respostas rápidas', async () => {
    vi.useFakeTimers();
    mockFetch(async () => jsonResponse(200, { ok: true }));
    render(<ServerWakeNotice />);

    await act(async () => {
      await api.get('/me');
      await vi.advanceTimersByTimeAsync(5000);
    });

    expect(screen.queryByText(WAKING)).not.toBeInTheDocument();
  });
});

describe('wake-up ping', () => {
  it('sem URL configurada, não faz nada', async () => {
    const fetchMock = mockFetch(async () => new Response(null, { status: 200 }));
    await wakeServer('');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('dispara um único fetch no-cors, mesmo chamado várias vezes', async () => {
    const fetchMock = mockFetch(async () => new Response(null, { status: 200 }));

    await Promise.all([
      wakeServer('https://api.example.com/actuator/health/liveness'),
      wakeServer('https://api.example.com/actuator/health/liveness'),
    ]);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(String(url)).toBe('https://api.example.com/actuator/health/liveness');
    expect(init?.mode).toBe('no-cors');
  });

  it('nunca rejeita, mesmo se o ping falhar', async () => {
    mockFetch(async () => {
      throw new TypeError('Failed to fetch');
    });
    await expect(
      wakeServer('https://api.example.com/actuator/health/liveness'),
    ).resolves.toBeUndefined();
  });

  it('o bootstrap da sessão faz o refresh depois do ping', async () => {
    const fetchMock = mockFetch(async () => jsonResponse(401, { code: 'INVALID_REFRESH_TOKEN' }));
    await expect(authApi.bootstrap()).resolves.toBeNull();
    // Sem VITE_WAKE_URL nos testes: só o refresh é chamado.
    expect(fetchMock.mock.calls.map(([u]) => String(u))).toEqual(['/api/auth/refresh']);
  });
});
