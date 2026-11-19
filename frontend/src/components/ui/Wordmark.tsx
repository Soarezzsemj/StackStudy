import { cn } from '@/lib/cn';

export function Wordmark({ className }: { className?: string }) {
  return (
    <span className={cn('text-xl font-bold lowercase tracking-card', className)}>
      stack<span className="text-action">.</span>study
    </span>
  );
}
