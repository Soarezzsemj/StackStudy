import type { TFunction } from 'i18next';
import { ApiError } from '@/lib/api/client';

/** Traduz o erro da API pelo `code`; se não conhecer, usa uma mensagem genérica. */
export function errorMessage(t: TFunction, error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'NETWORK_ERROR') return t('errors.network');
    return t(`errors.${error.code}`, { defaultValue: t('errors.generic') });
  }
  return t('errors.generic');
}

/** Nome do campo → primeira mensagem de validação do backend. */
export function fieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError)) return {};
  return error.details.reduce<Record<string, string>>((acc, d) => {
    acc[d.field] ??= d.message;
    return acc;
  }, {});
}
