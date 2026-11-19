import { useEffect } from 'react';
import { authApi } from '@/lib/api/auth';
import { useAuthStore } from '@/stores/authStore';

/** Ao abrir o app, tenta restaurar a sessão pelo cookie de refresh. */
export function useSessionBootstrap() {
  const status = useAuthStore((s) => s.status);
  useEffect(() => {
    if (status === 'unknown') void authApi.bootstrap();
  }, [status]);
}
