import { cn } from '@/lib/cn';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost';

/** Estilo de botão para outros elementos (ex.: links que parecem botões). */
export function buttonClass(variant: ButtonVariant = 'primary', className?: string) {
  return cn('btn', `btn-${variant}`, className);
}
