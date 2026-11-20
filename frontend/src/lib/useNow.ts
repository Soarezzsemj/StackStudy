import { useEffect, useState } from 'react';

/**
 * "Agora" em ms, atualizado a cada `intervalMs` enquanto `enabled`. Serve só para redesenhar:
 * os timers calculam o tempo a partir de timestamps, nunca contando ticks.
 */
export function useNow(enabled: boolean, intervalMs = 1000): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!enabled) return;
    setNow(Date.now());
    const id = window.setInterval(() => setNow(Date.now()), intervalMs);
    return () => window.clearInterval(id);
  }, [enabled, intervalMs]);
  return now;
}
