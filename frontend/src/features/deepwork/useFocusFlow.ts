import { useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import type { ActiveSession, CompleteInput } from '@/lib/api/deepwork';
import { useDeepWorkStore } from '@/stores/deepWorkStore';
import { notify, playChime } from './alerts';
import { DEFAULT_SETTINGS, nextBreak } from './pomodoro';
import { useDeepWorkSettings, useSessionActions } from './queries';

/** Som e notificação conforme as preferências. */
export function useAlert() {
  const { data: settings = DEFAULT_SETTINGS } = useDeepWorkSettings();
  return useCallback(
    (title: string, body: string) => {
      if (settings.soundEnabled) playChime();
      if (settings.notificationsEnabled) notify(title, body);
    },
    [settings.soundEnabled, settings.notificationsEnabled],
  );
}

/**
 * Conclui a sessão de foco. No Pomodoro, conta o ciclo e já começa a pausa (curta ou longa) no
 * navegador. Usado pelo botão "Concluir" e pelo fim automático do timer.
 */
export function useCompleteFocus() {
  const { t } = useTranslation();
  const { complete } = useSessionActions();
  const { data: settings = DEFAULT_SETTINGS } = useDeepWorkSettings();
  const alert = useAlert();
  const { mutate } = complete;

  const finish = useCallback(
    (session: ActiveSession, input?: CompleteInput, options?: { automatic?: boolean }) => {
      mutate(
        { id: session.id, input },
        {
          onSuccess: () => {
            if (session.mode !== 'POMODORO') return;
            const store = useDeepWorkStore.getState();
            const pause = nextBreak(store.completedFocus + 1, settings, Date.now());
            store.focusCompleted(pause);
            if (options?.automatic) {
              alert(
                t('deepWork.alerts.focusDoneTitle'),
                t(`deepWork.alerts.focusDoneBody.${pause.kind}`, {
                  minutes: Math.round(pause.durationSeconds / 60),
                }),
              );
            }
          },
        },
      );
    },
    [mutate, settings, alert, t],
  );

  return { finish, isPending: complete.isPending, error: complete.error };
}
