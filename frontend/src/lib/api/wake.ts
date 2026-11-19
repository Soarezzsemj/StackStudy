import { WAKE_TIMEOUT_MS, WAKE_URL } from './config';
import { trackSlow } from './slow';

let wakePromise: Promise<void> | null = null;

/**
 * Acorda o backend (plano free hiberna) com um ping `no-cors` direto no Render, uma vez só.
 * Ele não passa pela Vercel, então não sofre o limite de tempo do rewrite.
 * Nunca falha: erro ou timeout só encerram a espera.
 */
export function wakeServer(url: string = WAKE_URL, timeoutMs: number = WAKE_TIMEOUT_MS) {
  if (!url) return Promise.resolve();
  if (!wakePromise) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    wakePromise = trackSlow(
      fetch(url, { mode: 'no-cors', cache: 'no-store', signal: controller.signal })
        .then(() => undefined)
        .catch(() => undefined)
        .finally(() => clearTimeout(timer)),
    );
  }
  return wakePromise;
}

/** Só para testes: permite pingar de novo. */
export function __resetWakeForTests() {
  wakePromise = null;
}
