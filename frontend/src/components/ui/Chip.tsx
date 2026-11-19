import type { HTMLAttributes } from 'react';
import { cn } from '@/lib/cn';

export type ChipTone = 'neutral' | 'action' | 'warning' | 'danger' | 'space';

const toneClass: Record<ChipTone, string> = {
  neutral: 'border-border bg-surface-raised text-text-secondary',
  action: 'border-action/40 bg-action/10 text-action',
  warning: 'border-warning/40 bg-warning/10 text-warning',
  danger: 'border-danger/40 bg-danger/10 text-danger',
  space: 'border-space-glow/40 bg-space-deep/60 text-text-on-space',
};

export interface ChipProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: ChipTone;
  /** Ponto colorido opcional (ex.: cor da matéria). */
  dotColor?: string;
}

export function Chip({ tone = 'neutral', dotColor, className, children, ...rest }: ChipProps) {
  return (
    <span
      className={cn(
        'label inline-flex h-7 items-center gap-2 rounded-pill border px-3',
        toneClass[tone],
        className,
      )}
      {...rest}
    >
      {dotColor && (
        <span
          aria-hidden="true"
          data-testid="chip-dot"
          className="h-2 w-2 rounded-full"
          style={{ backgroundColor: dotColor }}
        />
      )}
      {children}
    </span>
  );
}
