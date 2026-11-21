import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '@/app/router';
import { useAuthStore } from '@/stores/authStore';
import { jsonResponse, mockApi, renderWithProviders, testUser } from '@/test/utils';
import {
  buildPayload,
  canGoTo,
  EMPTY_DRAFT,
  passwordStrength,
  suggestedGoal,
  validateStep,
} from './signupLogic';

const PASSWORD = 'correct horse battery staple';
const config = {
  googleEnabled: false,
  emailVerificationEnabled: false,
  profilePhotoEnabled: false,
  termsVersion: '2026-11-21',
  privacyVersion: '2026-11-21',
};

function setup(overrides: Parameters<typeof mockApi>[0] = {}) {
  useAuthStore.getState().clear();
  const fetchMock = mockApi({
    'GET /api/auth/config': () => jsonResponse(200, config),
    'GET /api/roadmaps/suggest': (_init, url) =>
      String(url).includes('Direito')
        ? jsonResponse(200, { slug: 'direito', title: 'Direito', area: 'Direito' })
        : new Response(null, { status: 204 }),
    ...overrides,
  });
  const user = userEvent.setup();
  const rendered = renderWithProviders(routes, { initialEntries: ['/signup'] });
  return { user, fetchMock, ...rendered };
}

async function fillAccount(user: ReturnType<typeof userEvent.setup>) {
  await user.type(await screen.findByLabelText('E-mail'), 'maria@example.com');
  await user.type(screen.getByLabelText('Senha'), PASSWORD);
  await user.click(screen.getByRole('button', { name: 'Continuar' }));
  await screen.findByRole('heading', { name: 'Seu perfil' });
}

async function fillProfile(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Nome'), 'Maria Souza');
  await user.type(screen.getByLabelText('Apelido'), 'Mari');
  await user.click(screen.getByRole('radio', { name: 'Direito' }));
  await user.type(screen.getByLabelText('Curso (opcional)'), 'Direito');
  await user.type(screen.getByLabelText('Período (opcional)'), '3º semestre');
}

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('Cadastro em 3 passos', () => {
  it('não avança com passo inválido; erros só depois de tocar e ligados ao campo', async () => {
    const { user } = setup();
    const email = await screen.findByLabelText('E-mail');
    // Sem tocar, sem erro.
    expect(email).not.toHaveAttribute('aria-invalid');
    expect(screen.getByRole('button', { name: /Etapa 2 Perfil/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: /Etapa 3 Rotina/ })).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Continuar com Google' })).not.toBeInTheDocument();

    await user.type(email, 'maria@');
    await user.tab();
    expect(email).toHaveAttribute('aria-invalid', 'true');
    const describedBy = email.getAttribute('aria-describedby')!;
    expect(document.getElementById(describedBy)).toHaveTextContent('Confira o e-mail');

    // Tentar avançar marca todos e leva o foco ao primeiro erro.
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    expect(screen.getByRole('heading', { name: 'Sua conta' })).toBeInTheDocument();
    expect(email).toHaveFocus();
    expect(screen.getByLabelText('Senha')).toHaveAttribute('aria-invalid', 'true');
  });

  it('indicador de força da senha com 4 níveis e Mostrar/Ocultar', async () => {
    const { user } = setup();
    const password = await screen.findByLabelText('Senha');
    const strength = screen.getByTestId('password-strength');
    expect(strength).toHaveTextContent('Use pelo menos 10 caracteres');
    await user.type(password, 'abc');
    expect(strength).toHaveTextContent('Força: fraca.');
    await user.clear(password);
    await user.type(password, '1234567890');
    expect(strength).toHaveTextContent('muito comum');
    await user.clear(password);
    await user.type(password, 'estudo diario');
    expect(strength).toHaveTextContent(/Força: (razoável|boa)\./);
    await user.clear(password);
    await user.type(password, PASSWORD);
    expect(strength).toHaveTextContent('Força: forte.');

    expect(password).toHaveAttribute('type', 'password');
    await user.click(screen.getByRole('button', { name: 'Mostrar' }));
    expect(password).toHaveAttribute('type', 'text');
    expect(screen.getByRole('button', { name: 'Ocultar' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('voltar preserva o que foi digitado; a prévia do perfil acompanha a digitação', async () => {
    const { user } = setup();
    await fillAccount(user);
    // O foco vai para o título do passo.
    expect(screen.getByRole('heading', { name: 'Seu perfil' })).toHaveFocus();
    const preview = screen.getAllByRole('region', { name: 'Prévia do seu perfil' })[0]!;
    expect(within(preview).getByTestId('preview-name')).toHaveTextContent('Seu nome');
    await fillProfile(user);
    expect(within(preview).getByTestId('preview-name')).toHaveTextContent('Maria Souza');
    expect(within(preview).getByTestId('preview-nickname')).toHaveTextContent('@Mari');
    expect(within(preview).getByText('Direito')).toBeInTheDocument();
    expect(within(preview).getByText('Direito · 3º semestre')).toBeInTheDocument();
    // Sugestão de roadmap pelo curso.
    expect(
      await screen.findByText('Com base no seu curso, vamos sugerir o roadmap de Direito.'),
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Voltar' }));
    expect(await screen.findByLabelText('E-mail')).toHaveValue('maria@example.com');
    expect(screen.getByLabelText('Senha')).toHaveValue(PASSWORD);
    await user.click(screen.getByRole('button', { name: /Etapa 2 Perfil/ }));
    expect(screen.getByLabelText('Nome')).toHaveValue('Maria Souza');
    expect(screen.getByRole('radio', { name: 'Direito' })).toHaveAttribute('aria-checked', 'true');
  });

  it('envio final: aceites obrigatórios, loading, payload e Cockpit; senha nunca no navegador', async () => {
    let release: () => void = () => {};
    const gate = new Promise<void>((r) => (release = r));
    const logSpy = vi.spyOn(console, 'log');
    const { user, fetchMock, router } = setup({
      'POST /api/auth/register': async () => {
        await gate;
        return jsonResponse(201, {
          accessToken: 'tok',
          tokenType: 'Bearer',
          expiresIn: 900,
          user: { ...testUser, name: 'Maria Souza', nickname: 'Mari' },
        });
      },
    });
    await fillAccount(user);
    await fillProfile(user);
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await screen.findByRole('heading', { name: 'Sua rotina de estudos' });

    // Sugestão de meta pelo período (3º semestre).
    expect(screen.getByText(/de 10 a 20 h por semana/)).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: 'Aparecer nas salas de estudo' })).toHaveAttribute(
      'aria-checked',
      'false',
    );
    expect(
      await screen.findByRole('switch', { name: 'Seguir o roadmap de Direito' }),
    ).toHaveAttribute('aria-checked', 'true');
    await user.click(screen.getByRole('radio', { name: /Tempo corrido/ }));

    // Sem os aceites, não envia; o erro fica ligado à caixa.
    await user.click(screen.getByRole('button', { name: 'Criar minha conta' }));
    const terms = screen.getByRole('checkbox', { name: /Termos de Uso/ });
    expect(terms).toHaveAttribute('aria-invalid', 'true');
    expect(document.getElementById(terms.getAttribute('aria-describedby')!)).toHaveTextContent(
      'aceite os Termos de Uso',
    );
    expect(fetchMock.mock.calls.some(([, i]) => i?.method === 'POST')).toBe(false);
    // Links legais abrem em nova aba (o rascunho só existe nesta página).
    const consentLinks = screen
      .getAllByRole('link', { name: 'Termos de Uso' })
      .filter((a) => a.getAttribute('target') === '_blank');
    expect(consentLinks).toHaveLength(1);
    expect(consentLinks[0]).toHaveAttribute('href', '/termos');

    await user.click(terms);
    await user.click(screen.getByRole('checkbox', { name: 'Tenho 18 anos ou mais' }));
    await user.click(screen.getByRole('button', { name: 'Criar minha conta' }));
    const busy = await screen.findByRole('button', { name: 'Criando sua conta…' });
    expect(busy).toBeDisabled();

    const post = fetchMock.mock.calls.find(([, i]) => i?.method === 'POST')!;
    const body = JSON.parse(String(post[1]!.body)) as Record<string, unknown>;
    expect(body).toMatchObject({
      email: 'maria@example.com',
      name: 'Maria Souza',
      nickname: 'Mari',
      area: 'LAW',
      course: 'Direito',
      period: '3º semestre',
      focusRhythm: 'FREE',
      followRoadmap: 'direito',
      roomsVisible: false,
      acceptTerms: true,
      confirmAge: true,
    });
    expect(body).not.toHaveProperty('role');

    // Nada da senha em armazenamento do navegador nem no console.
    const stored = JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage });
    expect(stored).not.toContain(PASSWORD);
    expect(JSON.stringify(logSpy.mock.calls)).not.toContain(PASSWORD);

    release();
    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
    expect(useAuthStore.getState().user?.nickname).toBe('Mari');
  });

  it('e-mail já cadastrado: volta ao passo 1 com mensagem neutra no campo', async () => {
    const { user } = setup({
      'POST /api/auth/register': () =>
        jsonResponse(409, { code: 'EMAIL_ALREADY_REGISTERED', message: 'x', details: [] }),
    });
    await fillAccount(user);
    await fillProfile(user);
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await screen.findByRole('heading', { name: 'Sua rotina de estudos' });
    await user.click(screen.getByRole('checkbox', { name: /Termos de Uso/ }));
    await user.click(screen.getByRole('checkbox', { name: 'Tenho 18 anos ou mais' }));
    await user.click(screen.getByRole('button', { name: 'Criar minha conta' }));
    const email = await screen.findByLabelText('E-mail');
    await waitFor(() => expect(email).toHaveAttribute('aria-invalid', 'true'));
    expect(document.getElementById(email.getAttribute('aria-describedby')!)).toHaveTextContent(
      'Se ele já é seu, entre ou recupere o acesso',
    );
  });

  it('com a flag do Google ligada, mostra o botão', async () => {
    setup({ 'GET /api/auth/config': () => jsonResponse(200, { ...config, googleEnabled: true }) });
    expect(await screen.findByRole('link', { name: 'Continuar com Google' })).toHaveAttribute(
      'href',
      '/api/auth/google/start',
    );
  });
});

describe('lógica do cadastro', () => {
  it('validação por passo e barra de etapas', () => {
    expect(validateStep('account', EMPTY_DRAFT)).toMatchObject({
      email: 'signup.errors.emailRequired',
      password: 'signup.errors.passwordRequired',
    });
    const account = { ...EMPTY_DRAFT, email: 'a@b.co', password: PASSWORD };
    expect(validateStep('account', account)).toEqual({});
    expect(validateStep('account', { ...account, password: 'qwertyuiop' }).password).toBe(
      'signup.errors.passwordCommon',
    );
    expect(canGoTo('profile', EMPTY_DRAFT)).toBe(false);
    expect(canGoTo('profile', account)).toBe(true);
    expect(canGoTo('routine', account)).toBe(false);
    expect(
      validateStep('profile', { ...account, name: '<b>', nickname: 'a', area: '' }),
    ).toMatchObject({
      name: 'signup.errors.nameInvalid',
      nickname: 'signup.errors.nicknameInvalid',
      area: 'signup.errors.areaRequired',
    });
  });

  it('força da senha, meta sugerida e payload', () => {
    expect(passwordStrength('').level).toBe(0);
    expect(passwordStrength('curta').level).toBe(1);
    expect(passwordStrength('Senha12345').level).toBe(1);
    expect(passwordStrength('abcdefghijk').level).toBe(2);
    expect(passwordStrength('Abcdefgh12k').level).toBe(3);
    expect(passwordStrength(PASSWORD).level).toBe(4);
    expect(suggestedGoal('')).toEqual({ min: 8, max: 20 });
    expect(suggestedGoal('1º período')).toEqual({ min: 6, max: 12 });
    expect(suggestedGoal('9º semestre')).toEqual({ min: 12, max: 25 });
    const p = buildPayload(
      {
        ...EMPTY_DRAFT,
        email: ' a@b.co ',
        name: ' Ana ',
        nickname: 'Ana',
        area: 'OTHER',
        course: '  ',
      },
      'pt-BR',
      undefined,
    );
    expect(p.email).toBe('a@b.co');
    expect(p.course).toBeUndefined();
    expect(p.followRoadmap).toBeUndefined();
    expect(p.roomsVisible).toBe(false);
  });
});
