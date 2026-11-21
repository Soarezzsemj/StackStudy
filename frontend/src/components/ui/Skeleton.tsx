import { cn } from '@/lib/cn';

/**
 * Bloco cinza no lugar do conteúdo enquanto carrega (servidor acordando: nunca tela em branco).
 * Decorativo; quem usa anuncia o carregamento com um texto para leitores de tela.
 */
export function Skeleton({ className }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'block animate-pulse rounded-inner bg-surface-raised motion-reduce:animate-none',
        className,
      )}
    />
  );
}
