import { browserTimeZone } from '@/lib/api/deepwork';
import { useAuthStore } from '@/stores/authStore';

/**
 * Fuso usado nas contas por dia (heatmap, estatísticas, fila de flashcards): o escolhido no
 * Perfil ou, sem preferência, o do navegador (comportamento de antes).
 */
export function useTimeZone(): string {
  const preferred = useAuthStore((s) => s.user?.timeZone);
  return preferred || browserTimeZone();
}
