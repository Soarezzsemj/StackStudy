import { useEffect, useState } from 'react';

/** Devolve `value` só depois de `delayMs` sem mudanças (ex.: busca enquanto digita). */
export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(id);
  }, [value, delayMs]);
  return debounced;
}
