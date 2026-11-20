import '@testing-library/jest-dom/vitest';
import { afterEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import i18n from '@/i18n';
import { useAuthStore } from '@/stores/authStore';
import { useUiStore } from '@/stores/uiStore';
import { useSessionStore } from '@/stores/sessionStore';
import { useNetworkStore } from '@/stores/networkStore';
import { useStackStore } from '@/stores/stackStore';
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

// Os testes conferem os textos em pt-BR (idioma padrão).
void i18n.changeLanguage('pt-BR');

afterEach(() => {
  cleanup();
  useAuthStore.setState({ status: 'unknown', accessToken: null, user: null });
  useUiStore.setState({ paletteOpen: false, mobileNavOpen: false });
  useSessionStore.setState({ active: null });
  useNetworkStore.setState({ slowRequests: 0 });
  useStackStore.setState({ filters: DEFAULT_FILTERS, selectedTaskId: null, dialog: null });
  __resetClientForTests();
  __resetWakeForTests();
  vi.useRealTimers();
});
