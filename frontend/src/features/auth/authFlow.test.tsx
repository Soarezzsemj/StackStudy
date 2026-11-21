import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '@/app/router';
import { useAuthStore } from '@/stores/authStore';
import {
  authBody,
  jsonResponse,
  mockApi,
  mockFetch,
  renderWithProviders,
  testUser,
} from '@/test/utils';

describe('auth flow (UI)', () => {
  it('anonymous users are redirected from a private route to login', async () => {
    useAuthStore.getState().clear();
    renderWithProviders(routes, { initialEntries: ['/stack'] });
    expect(await screen.findByRole('heading', { name: 'Entrar' })).toBeInTheDocument();
  });

  it('logs in and lands on the app shell', async () => {
    useAuthStore.getState().clear();
    const fetchMock = mockApi({ 'POST /api/auth/login': () => jsonResponse(200, authBody('t1')) });
    const { router } = renderWithProviders(routes, { initialEntries: ['/login'] });

    await userEvent.type(await screen.findByLabelText('E-mail'), 'ada@example.com');
    await userEvent.type(screen.getByLabelText('Senha'), 'correct-horse');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(
      await screen.findByRole('heading', { level: 1, name: /^(Bom dia|Boa tarde|Boa noite)/ }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/');
    expect(screen.getByRole('navigation', { name: 'Navegação principal' })).toBeInTheDocument();
    expect(screen.getByText('Nenhuma sessão ativa')).toBeInTheDocument();
    expect(screen.getByText(testUser.name)).toBeInTheDocument();

    const [url, init] = fetchMock.mock.calls[0]!;
    expect(String(url)).toBe('/api/auth/login');
    expect(JSON.parse(String(init!.body))).toEqual({
      email: 'ada@example.com',
      password: 'correct-horse',
    });
  });

  it('shows a translated error for invalid credentials', async () => {
    useAuthStore.getState().clear();
    mockFetch(async () =>
      jsonResponse(401, { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password' }),
    );
    renderWithProviders(routes, { initialEntries: ['/login'] });

    await userEvent.type(await screen.findByLabelText('E-mail'), 'ada@example.com');
    await userEvent.type(screen.getByLabelText('Senha'), 'wrong-pass');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('E-mail ou senha incorretos.');
    expect(useAuthStore.getState().status).toBe('anonymous');
  });

  it('registers and shows field errors from the backend', async () => {
    useAuthStore.getState().clear();
    mockFetch(async () =>
      jsonResponse(400, {
        code: 'VALIDATION_ERROR',
        message: 'Request validation failed',
        details: [{ field: 'password', message: 'must have between 8 and 72 characters' }],
      }),
    );
    renderWithProviders(routes, { initialEntries: ['/register'] });

    await userEvent.type(await screen.findByLabelText('Nome'), 'Ada');
    await userEvent.type(screen.getByLabelText('E-mail'), 'ada@example.com');
    await userEvent.type(screen.getByLabelText('Senha'), 'short');
    await userEvent.click(screen.getByRole('button', { name: 'Criar conta' }));

    expect(await screen.findByText('must have between 8 and 72 characters')).toBeInTheDocument();
    expect(screen.getByLabelText('Senha')).toHaveAttribute('aria-invalid', 'true');
  });

  it('command palette opens with Ctrl+K and navigates', async () => {
    useAuthStore.getState().setSession('t1', testUser);
    mockApi();
    const { router } = renderWithProviders(routes, { initialEntries: ['/'] });
    await screen.findByRole('heading', { level: 1, name: /^(Bom dia|Boa tarde|Boa noite)/ });

    await userEvent.keyboard('{Control>}k{/Control}');
    const option = await screen.findByRole('option', { name: /Ir para Flashcards/ });
    await userEvent.click(option);

    await waitFor(() => expect(router.state.location.pathname).toBe('/flashcards'));
    // A tela é carregada sob demanda (code splitting): espera o arquivo chegar.
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Flashcards' }, { timeout: 5000 }),
    ).toBeInTheDocument();
  });
});
