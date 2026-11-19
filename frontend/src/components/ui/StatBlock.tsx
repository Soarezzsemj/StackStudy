import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';

export interface StatBlockProps {
  label: ReactNode;
  value: ReactNode;
  unit?: ReactNode;
  hint?: ReactNode;
  className?: string;
}

export function StatBlock({ label, value, unit, hint, className }: StatBlockProps) {
  return (
    <div className={cn('flex flex-col gap-1', className)}>
      <dt className="label text-text-muted">{label}</dt>
      <dd className="m-0 flex items-baseline gap-1">
        <span className="tabular text-4xl font-[650] leading-none tracking-card">{value}</span>
        {unit && <span className="text-sm text-text-secondary">{unit}</span>}
      </dd>
      {hint && <dd className="m-0 text-sm text-text-secondary">{hint}</dd>}
    </div>
  );
}
