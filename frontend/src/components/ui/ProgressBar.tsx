import { cn } from '@/lib/cn';
import { clampPercent } from './scales';

export interface ProgressBarProps {
  value: number;
  max?: number;
  /** Nome acessível (obrigatório, a barra não tem texto próprio). */
  label: string;
  /** Cor da barra; o padrão é a cor de ação. */
  color?: string;
  showValue?: boolean;
  className?: string;
}

export function ProgressBar({
  value,
  max = 100,
  label,
  color,
  showValue = false,
  className,
}: ProgressBarProps) {
  const pct = clampPercent(value, max);
  const rounded = Math.round(pct);
  return (
    <div className={cn('flex items-center gap-3', className)}>
      <div
        role="progressbar"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={rounded}
        className="h-2 flex-1 overflow-hidden rounded-pill bg-surface-raised"
      >
        <div
          className="h-full rounded-pill bg-action transition-[width] duration-300"
          style={{ width: `${pct}%`, ...(color ? { backgroundColor: color } : {}) }}
        />
      </div>
      {showValue && <span className="tabular text-sm text-text-secondary">{rounded}%</span>}
    </div>
  );
}
