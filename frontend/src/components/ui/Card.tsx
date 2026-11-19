import type { HTMLAttributes, ReactNode } from 'react';
import { cn } from '@/lib/cn';

export interface CardProps extends Omit<HTMLAttributes<HTMLElement>, 'title'> {
  /** "hero" adiciona o brilho roxo (sessão ativa, timer, simulador, cartão de estudo). */
  variant?: 'default' | 'hero';
  title?: ReactNode;
  /** Rótulo pequeno acima do título. */
  eyebrow?: ReactNode;
  actions?: ReactNode;
  as?: 'section' | 'article' | 'div';
}

export function Card({
  variant = 'default',
  title,
  eyebrow,
  actions,
  as: Tag = 'section',
  className,
  children,
  ...rest
}: CardProps) {
  return (
    <Tag className={cn('card p-6', variant === 'hero' && 'card-hero', className)} {...rest}>
      {(title || eyebrow || actions) && (
        <header className="mb-4 flex items-start justify-between gap-4">
          <div>
            {eyebrow && <p className="label mb-1 text-text-muted">{eyebrow}</p>}
            {title && <h2 className="card-title">{title}</h2>}
          </div>
          {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
        </header>
      )}
      {children}
    </Tag>
  );
}
