import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { routes } from '@/app/router';
import i18n from '@/i18n';
import { apiRequest, refreshSession } from '@/lib/api/client';
import { isChunkLoadError, safeRedirect } from '@/lib/errors';
import { OfflineNotice } from '@/components/shell/OfflineNotice';
import { RouteErrorPage } from '@/pages/RouteErrorPage';
import { useAuthStore } from '@/stores/authStore';
import { jsonResponse, mockApi, mockFetch, renderWithProviders, testUser } from '@/test/utils';

// ---------------------------------------------------------------------------
// contraste (WCAG 2.1, AA = 4,5:1 para texto normal), direto dos tokens
// ---------------------------------------------------------------------------

const tokens = Object.fromEntries(
  [
    ...readFileSync(resolve(process.cwd(), 'src/styles/tokens.css'), 'utf-8').matchAll(
      /--([a-z0-9-]+):\s*(#[0-9a-f]{6})\s*;/gi,
    ),
  ].map((m) => [m[1]!, m[2]!]),
);

function luminance(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r! + 0.7152 * g! + 0.0722 * b!;
}

function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi! + 0.05) / (lo! + 0.05);
}

describe('acessibilidade: contraste dos tokens', () => {
  const backgrounds = ['bg', 'surface', 'surface-raised'];
  const texts = [
    'text',
    'text-secondary',
    'text-muted',
    'text-on-space',
    'action',
    'danger',
    'warning',
  ];

  it.each(texts.flatMap((fg) => backgrounds.map((bg) => [fg, bg] as const)))(
    '%s sobre %s tem pelo menos 4,5:1',
    (fg, bg) => {
      expect(tokens[fg], fg).toBeDefined();
      expect(contrast(tokens[fg]!, tokens[bg]!)).toBeGreaterThanOrEqual(4.5);
    },
  );

  it('texto do botão de ação e foco (lima) visível', () => {
    expect(contrast(tokens['action-ink']!, tokens['action']!)).toBeGreaterThanOrEqual(4.5);
    // Contorno de foco é a cor de ação: 3:1 contra o fundo (WCAG 1.4.11).
    expect(contrast(tokens['action']!, tokens['bg']!)).toBeGreaterThanOrEqual(3);
  });

  it('o CSS respeita prefers-reduced-motion', () => {
    const css = readFileSync(resolve(process.cwd(), 'src/styles/index.css'), 'utf-8');
    expect(css).toMatch(/@media \(prefers-reduced-motion: reduce\)[\s\S]*animation-duration/);
  });
});

// ---------------------------------------------------------------------------
// termos e privacidade
// ---------------------------------------------------------------------------

describe('Termos e Privacidade', () => {
  it('abrem sem login, em pt-BR e en, com as seções da LGPD', async () => {
    useAuthStore.getState().clear();
    mockApi({});
    renderWithProviders(routes, { initialEntries: ['/privacy'] });
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Política de Privacidade' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '7. Seus direitos' })).toBeInTheDocument();
    expect(screen.getByText(/art\. 7º, V, da LGPD/)).toBeInTheDocument();
    const footer = screen.getByRole('navigation', { name: 'Documentos legais' });
    await userEvent.click(within(footer).getByRole('link', { name: 'Termos' }));
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Termos de uso' }),
    ).toBeInTheDocument();

    await act(() => i18n.changeLanguage('en'));
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Terms of use' }),
    ).toBeInTheDocument();
    await act(() => i18n.changeLanguage('pt-BR'));
  });

  it('o endereço antigo do cadastro leva ao novo, com o rodapé legal', async () => {
    useAuthStore.getState().clear();
    mockApi({});
    const { router } = renderWithProviders(routes, { initialEntries: ['/register'] });
    await screen.findByRole('heading', { level: 1, name: 'Criar conta' });
    expect(router.state.location.pathname).toBe('/signup');
    // O aceite com links fica no passo 3 (testado em SignupPage.test.tsx).
    expect(screen.getByRole('navigation', { name: 'Documentos legais' })).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// robustez: sessão expirada, sem rede, 429, erro de tela
// ---------------------------------------------------------------------------

describe('robustez', () => {
  it('sessão expirada: volta ao login com o aviso', async () => {
    useAuthStore.getState().setSession('old-token', testUser);
    mockApi({
      'GET /api/cockpit': () => jsonResponse(401, { code: 'UNAUTHORIZED', message: 'x' }),
      'POST /api/auth/refresh': () =>
        jsonResponse(401, { code: 'INVALID_REFRESH_TOKEN', message: 'x' }),
    });
    const { router } = renderWithProviders(routes, { initialEntries: ['/'] });
    expect(
      await screen.findByText('Sua sessão expirou. Entre de novo para continuar.'),
    ).toBeVisible();
    expect(router.state.location.pathname).toBe('/login');
  });

  it('logout normal não mostra "sessão expirou"', () => {
    useAuthStore.getState().setSession('t', testUser);
    useAuthStore.getState().clear();
    expect(useAuthStore.getState().sessionExpired).toBe(false);
  });

  it('sem rede ou com 429 no refresh, quem está logado continua logado', async () => {
    useAuthStore.getState().setSession('t', testUser);
    mockFetch(async () => {
      throw new TypeError('Failed to fetch');
    });
    expect(await refreshSession()).toBeNull();
    expect(useAuthStore.getState().status).toBe('authenticated');

    mockFetch(async () => jsonResponse(429, { code: 'RATE_LIMITED', message: 'x' }));
    expect(await refreshSession()).toBeNull();
    expect(useAuthStore.getState().status).toBe('authenticated');
  });

  it('429 vira mensagem clara no login', async () => {
    useAuthStore.getState().clear();
    mockApi({
      'POST /api/auth/login': () => jsonResponse(429, { code: 'RATE_LIMITED', message: 'x' }),
    });
    renderWithProviders(routes, { initialEntries: ['/login'] });
    await userEvent.type(await screen.findByLabelText('E-mail'), 'ada@example.com');
    await userEvent.type(screen.getByLabelText('Senha'), 'whatever-1');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));
    expect(await screen.findByText(/Muitas tentativas seguidas/)).toBeInTheDocument();
    // 429 nunca dispara refresh nem repete o POST.
    await expect(
      apiRequest('/auth/login', { method: 'POST', body: {}, auth: false }),
    ).rejects.toMatchObject({ status: 429, code: 'RATE_LIMITED' });
  });

  it('aviso de sem internet aparece e some com os eventos do navegador', async () => {
    render(<OfflineNotice />);
    expect(screen.queryByText(/Você está sem internet/)).not.toBeInTheDocument();
    act(() => {
      window.dispatchEvent(new Event('offline'));
    });
    expect(screen.getByText(/Você está sem internet/)).toBeInTheDocument();
    act(() => {
      window.dispatchEvent(new Event('online'));
    });
    expect(screen.queryByText(/Você está sem internet/)).not.toBeInTheDocument();
  });

  it('erro numa tela mostra a página de erro (e "nova versão" quando o arquivo sumiu)', async () => {
    function Boom(): never {
      throw new Error('kaboom');
    }
    function MissingChunk(): never {
      throw new TypeError('Failed to fetch dynamically imported module: /assets/Stack-1.js');
    }
    const router = createMemoryRouter(
      [
        { path: '/', element: <Boom />, errorElement: <RouteErrorPage /> },
        { path: '/old', element: <MissingChunk />, errorElement: <RouteErrorPage /> },
      ],
      { initialEntries: ['/'] },
    );
    const silence = console.error;
    console.error = () => {};
    try {
      render(<RouterProvider router={router} />);
      expect(await screen.findByRole('heading', { name: 'Algo deu errado' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Recarregar' })).toBeInTheDocument();
      await act(() => router.navigate('/old'));
      expect(await screen.findByRole('heading', { name: 'Nova versão no ar' })).toBeInTheDocument();
    } finally {
      console.error = silence;
    }
    expect(isChunkLoadError(new Error('x'))).toBe(false);
  });

  it('depois do login só redireciona para caminhos internos', () => {
    expect(safeRedirect('/stack')).toBe('/stack');
    expect(safeRedirect('/flashcards/decks/1?x=2')).toBe('/flashcards/decks/1?x=2');
    for (const bad of [
      '//evil.com',
      '/\\evil.com',
      'https://evil.com',
      '/javascript:alert(1)',
      42,
    ]) {
      expect(safeRedirect(bad), String(bad)).toBe('/');
    }
  });

  it('o primeiro Tab no app é o "Pular para o conteúdo"', async () => {
    useAuthStore.getState().setSession('t', testUser);
    mockApi({ 'GET /api/cockpit': () => jsonResponse(500, { code: 'INTERNAL_ERROR' }) });
    renderWithProviders(routes, { initialEntries: ['/'] });
    await screen.findByRole('navigation', { name: 'Navegação principal' });
    await userEvent.tab();
    const skip = screen.getByRole('link', { name: 'Pular para o conteúdo' });
    expect(skip).toHaveFocus();
    expect(skip).toHaveAttribute('href', '#main');
    expect(document.getElementById('main')).toHaveAttribute('tabindex', '-1');
  });

  it('rota desconhecida dentro do app mostra o 404', async () => {
    useAuthStore.getState().setSession('t', testUser);
    mockApi({});
    renderWithProviders(routes, { initialEntries: ['/nao-existe'] });
    expect(await screen.findByRole('heading', { level: 1, name: '404' })).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByRole('link', { name: 'Voltar ao Cockpit' })).toHaveAttribute('href', '/'),
    );
  });
});
