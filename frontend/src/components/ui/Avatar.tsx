import { cn } from '@/lib/cn';
import { initials } from '@/lib/initials';

interface AvatarProps {
  name: string | undefined | null;
  /** Cor da paleta; sem cor, o roxo profundo de sempre. */
  color?: string | null;
  size?: 'sm' | 'lg';
  className?: string;
}

/**
 * Avatar de iniciais (sem upload: o plano grátis não guarda arquivos). Decorativo: o nome vem
 * sempre escrito ao lado.
 */
export function Avatar({ name, color, size = 'sm', className }: AvatarProps) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'flex shrink-0 items-center justify-center rounded-full font-bold',
        size === 'lg' ? 'h-20 w-20 text-2xl' : 'h-9 w-9 text-sm',
        // Cores da paleta são claras: texto escuro (ink) tem contraste; no padrão, texto claro.
        color ? 'text-action-ink' : 'bg-space-deep text-text-on-space',
        className,
      )}
      style={color ? { backgroundColor: color } : undefined}
    >
      {initials(name)}
    </span>
  );
}
