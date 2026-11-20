import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/cn';
import { Button } from './Button';

/** Carregando: anunciado para leitores de tela via role="status". */
export function LoadingState({ label, className }: { label?: string; className?: string }) {
  const { t } = useTranslation();
  return (
    <div
      role="status"
      aria-live="polite"
      className={cn('flex items-center gap-3 py-6 text-sm text-text-muted', className)}
    >
      <span
        aria-hidden="true"
        className="h-4 w-4 animate-spin rounded-full border-2 border-border-control border-t-action motion-reduce:animate-none"
      />
      {label ?? t('common.loading')}
    </div>
  );
}

/** Erro com botão de tentar de novo. */
export function ErrorState({
  message,
  onRetry,
  className,
}: {
  message: string;
  onRetry?: () => void;
  className?: string;
}) {
  const { t } = useTranslation();
  return (
    <div
      role="alert"
      className={cn(
        'flex flex-wrap items-center gap-3 rounded-inner border border-danger/40 bg-danger/10 px-4 py-3 text-sm text-danger',
        className,
      )}
    >
      <span className="flex-1">{message}</span>
      {onRetry && (
        <Button variant="secondary" onClick={onRetry}>
          {t('common.retry')}
        </Button>
      )}
    </div>
  );
}

/** Estado vazio com título, texto e ação opcional. */
export function EmptyState({
  title,
  body,
  action,
  className,
}: {
  title: string;
  body?: string;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        'flex flex-col items-start gap-3 rounded-inner border border-dashed border-border px-5 py-6',
        className,
      )}
    >
      <p className="font-semibold text-text">{title}</p>
      {body && <p className="text-sm text-text-secondary">{body}</p>}
      {action}
    </div>
  );
}
