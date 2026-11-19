import { useTranslation } from 'react-i18next';
import { useNetworkStore } from '@/stores/networkStore';

/** Aviso discreto quando o backend (plano free) está acordando e as respostas demoram. */
export function ServerWakeNotice() {
  const { t } = useTranslation();
  const visible = useNetworkStore((s) => s.slowRequests > 0);

  // A região "status" fica sempre montada, para o leitor de tela anunciar quando o texto aparece.
  return (
    <div
      role="status"
      aria-live="polite"
      className="pointer-events-none fixed inset-x-0 bottom-[calc(var(--statusbar-height)+12px)] z-50 flex justify-center px-4"
    >
      {visible && (
        <p className="m-0 flex items-center gap-3 rounded-pill border border-space-glow/40 bg-surface-raised px-4 py-2.5 text-sm text-text-on-space shadow-lg">
          <span
            aria-hidden="true"
            className="h-2 w-2 shrink-0 animate-pulse rounded-full bg-action"
          />
          {t('network.waking')}
        </p>
      )}
    </div>
  );
}
