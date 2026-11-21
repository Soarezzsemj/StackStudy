import { changeLanguage, isLanguage } from '@/i18n';
import { useAuthStore } from '@/stores/authStore';

/**
 * Ao entrar (login, cadastro ou sessão restaurada), aplica o idioma salvo na conta. Só quando o
 * usuário muda: trocar o idioma depois (Perfil ou barra lateral) não é desfeito por um refresh.
 */
export function syncUserLanguage(): () => void {
  return useAuthStore.subscribe((state, prev) => {
    const user = state.user;
    if (!user || user.id === prev.user?.id) return;
    if (isLanguage(user.locale)) void changeLanguage(user.locale);
  });
}
