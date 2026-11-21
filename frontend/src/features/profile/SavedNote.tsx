import type { ReactNode } from 'react';
import { Icon } from '@/components/ui';

/** "Salvo" ao lado do botão, anunciado por leitores de tela (região sempre presente). */
export function SavedNote({ show, children }: { show: boolean; children: ReactNode }) {
  return (
    <p role="status" aria-live="polite" className="m-0 flex items-center gap-1 text-sm text-action">
      {show && (
        <>
          <Icon name="check" size={16} />
          {children}
        </>
      )}
    </p>
  );
}
