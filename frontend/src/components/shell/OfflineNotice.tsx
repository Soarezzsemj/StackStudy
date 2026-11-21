import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Icon } from '@/components/ui';

/**
 * Sem internet: avisa que nada será salvo até a conexão voltar (o servidor dormindo é outro aviso,
 * o "Acordando o servidor…"). A região fica sempre montada para o leitor de tela anunciar.
 */
export function OfflineNotice() {
  const { t } = useTranslation();
  const [offline, setOffline] = useState(
    () => typeof navigator !== 'undefined' && !navigator.onLine,
  );

  useEffect(() => {
    const on = () => setOffline(false);
    const off = () => setOffline(true);
    window.addEventListener('online', on);
    window.addEventListener('offline', off);
    return () => {
      window.removeEventListener('online', on);
      window.removeEventListener('offline', off);
    };
  }, []);

  return (
    <div
      role="status"
      aria-live="assertive"
      className="pointer-events-none fixed inset-x-0 top-3 z-50 flex justify-center px-4"
    >
      {offline && (
        <p className="m-0 flex items-center gap-3 rounded-pill border border-warning/50 bg-surface-raised px-4 py-2.5 text-sm text-text shadow-lg">
          <Icon name="alert" size={16} className="text-warning" />
          {t('network.offline')}
        </p>
      )}
    </div>
  );
}
