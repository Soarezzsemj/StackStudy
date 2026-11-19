import { useNetworkStore } from '@/stores/networkStore';
import { SLOW_REQUEST_MS } from './config';

/** Marca a promessa como "lenta" se ela passar de `thresholdMs`, até terminar. */
export function trackSlow<T>(promise: Promise<T>, thresholdMs = SLOW_REQUEST_MS): Promise<T> {
  let marked = false;
  const timer = setTimeout(() => {
    marked = true;
    useNetworkStore.getState().slowStarted();
  }, thresholdMs);
  return promise.finally(() => {
    clearTimeout(timer);
    if (marked) useNetworkStore.getState().slowEnded();
  });
}

/** Espera `ms`, mas para antes se o signal for abortado. */
export function sleep(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(signal.reason);
      return;
    }
    const timer = setTimeout(resolve, ms);
    signal?.addEventListener(
      'abort',
      () => {
        clearTimeout(timer);
        reject(signal.reason);
      },
      { once: true },
    );
  });
}
