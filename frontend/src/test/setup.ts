import '@testing-library/jest-dom/vitest';
import { afterEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import i18n from '@/i18n';
import { useAuthStore } from '@/stores/authStore';
import { useUiStore } from '@/stores/uiStore';
import { useNetworkStore } from '@/stores/networkStore';
import { useStackStore } from '@/stores/stackStore';
import { useDeepWorkStore } from '@/stores/deepWorkStore';
import { DEFAULT_FILTERS } from '@/features/stack/filters';
import { __resetClientForTests } from '@/lib/api/client';
import { __resetWakeForTests } from '@/lib/api/wake';

// O React Router cria `new Request(url, { signal })` com o AbortSignal do jsdom,
// que o Request nativo do Node recusa. Nos testes, tiramos o signal.
const NativeRequest = globalThis.Request;
class TestRequest extends NativeRequest {
  constructor(input: RequestInfo | URL, init?: RequestInit) {
    const { signal: _signal, ...rest } = init ?? {};
    super(input, rest);
  }
}
globalThis.Request = TestRequest as typeof Request;

// O jsdom não tem estas APIs do navegador que o cmdk usa.
globalThis.ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};
Element.prototype.scrollIntoView ??= function scrollIntoView() {};

// Code splitting não é assunto dos testes de tela: as páginas carregam direto (sem lazy), para
// não depender do tempo de import. O build continua dividindo por rota.
vi.mock('@/app/lazyPages', async () => {
  const [
    cockpit,
    stack,
    deepWork,
    flashcards,
    deck,
    review,
    grades,
    profile,
    notFound,
    design,
    legal,
  ] = await Promise.all([
    import('@/features/cockpit/CockpitPage'),
    import('@/features/stack/StackPage'),
    import('@/features/deepwork/DeepWorkPage'),
    import('@/features/flashcards/FlashcardsPage'),
    import('@/features/flashcards/DeckPage'),
    import('@/features/flashcards/ReviewPage'),
    import('@/features/grades/GradesPage'),
    import('@/features/profile/ProfilePage'),
    import('@/pages/NotFoundPage'),
    import('@/pages/DesignPage'),
    import('@/pages/LegalPage'),
  ]);
  return {
    CockpitPage: cockpit.CockpitPage,
    StackPage: stack.StackPage,
    DeepWorkPage: deepWork.DeepWorkPage,
    FlashcardsPage: flashcards.FlashcardsPage,
    DeckPage: deck.DeckPage,
    ReviewPage: review.ReviewPage,
    GradesPage: grades.GradesPage,
    ProfilePage: profile.ProfilePage,
    NotFoundPage: notFound.NotFoundPage,
    DesignPage: design.DesignPage,
    LegalPage: legal.LegalPage,
  };
});

// Os testes conferem os textos em pt-BR (idioma padrão).
void i18n.changeLanguage('pt-BR');

afterEach(() => {
  cleanup();
  useAuthStore.setState({
    status: 'unknown',
    accessToken: null,
    user: null,
    sessionExpired: false,
  });
  useUiStore.setState({ paletteOpen: false, mobileNavOpen: false });
  useDeepWorkStore.setState({
    mode: 'POMODORO',
    subjectId: '',
    taskId: '',
    completedFocus: 0,
    breakState: null,
  });
  localStorage.clear();
  useNetworkStore.setState({ slowRequests: 0 });
  useStackStore.setState({ filters: DEFAULT_FILTERS, selectedTaskId: null, dialog: null });
  __resetClientForTests();
  __resetWakeForTests();
  vi.useRealTimers();
});
