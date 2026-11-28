import { useEffect, useId, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/cn';
import { Button } from './Button';
import { Icon } from './Icon';

export interface DialogProps {
  title: ReactNode;
  onClose: () => void;
  children: ReactNode;
  /** Rodapé com as ações (ex.: Cancelar / Salvar). */
  footer?: ReactNode;
  className?: string;
}

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Diálogo modal acessível: foco vai para o primeiro campo, Tab fica preso dentro, Esc fecha e o
 * foco volta para quem abriu.
 */
export function Dialog({ title, onClose, children, footer, className }: DialogProps) {
  const { t } = useTranslation();
  const titleId = useId();
  const panelRef = useRef<HTMLDivElement>(null);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const panel = panelRef.current;
    const first =
      panel?.querySelector<HTMLElement>('[data-autofocus]') ??
      panel?.querySelector<HTMLElement>('input, select, textarea') ??
      panel?.querySelector<HTMLElement>(FOCUSABLE);
    first?.focus();

    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        e.stopPropagation();
        onCloseRef.current();
        return;
      }
      if (e.key !== 'Tab' || !panel) return;
      const items = Array.from(panel.querySelectorAll<HTMLElement>(FOCUSABLE));
      if (items.length === 0) return;
      const firstItem = items[0]!;
      const lastItem = items[items.length - 1]!;
      if (e.shiftKey && document.activeElement === firstItem) {
        e.preventDefault();
        lastItem.focus();
      } else if (!e.shiftKey && document.activeElement === lastItem) {
        e.preventDefault();
        firstItem.focus();
      }
    }
    document.addEventListener('keydown', onKeyDown, true);
    return () => {
      document.removeEventListener('keydown', onKeyDown, true);
      previous?.focus?.();
    };
  }, []);

  return createPortal(
    <div className="fixed inset-0 z-40 flex items-start justify-center overflow-y-auto p-4 sm:pt-[10vh]">
      <button
        type="button"
        tabIndex={-1}
        aria-label={t('common.close')}
        className="fixed inset-0 h-full w-full cursor-default bg-black/60 backdrop-blur-sm"
        onClick={onClose}
      />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className={cn('card card-hero relative z-10 w-full max-w-xl p-6 shadow-dialog', className)}
      >
        <header className="mb-5 flex items-start justify-between gap-4">
          <h2 id={titleId} className="card-title">
            {title}
          </h2>
          <Button
            variant="ghost"
            iconOnly
            aria-label={t('common.close')}
            className="-mr-2 -mt-2"
            onClick={onClose}
          >
            <Icon name="close" />
          </Button>
        </header>
        {children}
        {footer && <footer className="mt-6 flex flex-wrap justify-end gap-3">{footer}</footer>}
      </div>
    </div>,
    document.body,
  );
}
