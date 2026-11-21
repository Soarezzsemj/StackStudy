import { API_URL } from '@/lib/api/config';
import { useAuthStore } from '@/stores/authStore';

/**
 * Envia a foto de perfil (multipart) depois do cadastro. Só é usado com PROFILE_PHOTO_ENABLED; o
 * servidor confere o tipo pelo conteúdo e regrava a imagem sem metadados.
 */
export async function uploadProfilePhoto(file: File): Promise<void> {
  const body = new FormData();
  body.append('photo', file);
  const token = useAuthStore.getState().accessToken;
  const res = await fetch(API_URL + '/me/photo', {
    method: 'POST',
    body,
    credentials: 'include',
    headers: token ? { Authorization: 'Bearer ' + token } : {},
  });
  if (!res.ok) throw new Error('photo upload failed');
}
