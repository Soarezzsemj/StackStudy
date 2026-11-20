import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { formatClock } from '@/lib/time';
import { useNow } from '@/lib/useNow';
import { useDeepWorkStore } from '@/stores/deepWorkStore';
import { displaySeconds, isFocusOver } from './clock';
import { breakRemaining } from './pomodoro';
import { useActiveSession } from './queries';
import { useAlert, useCompleteFocus } from './useFocusFlow';

/**
 * Motor do Deep Work, montado no AppShell (vale em qualquer tela): conclui o Pomodoro quando o
 * tempo acaba, encerra a pausa, toca os alertas e mostra o tempo no título da aba. Não desenha nada.
 */
export function DeepWorkEngine() {
  const { t } = useTranslation();
  const { data: active } = useActiveSession();
  const breakState = useDeepWorkStore((s) => s.breakState);
  const setBreak = useDeepWorkStore((s) => s.setBreak);
  const now = useNow(!!active || !!breakState);
  const { finish } = useCompleteFocus();
  const alert = useAlert();
  const completing = useRef<string | null>(null);
  const baseTitle = useRef(document.title);

  // Fim do Pomodoro (também ao reabrir o app depois do fim): conclui uma vez só.
  useEffect(() => {
    if (!active || completing.current === active.id || !isFocusOver(active, now)) return;
    completing.current = active.id;
    finish(active, undefined, { automatic: true });
  }, [active, now, finish]);

  // Fim da pausa.
  useEffect(() => {
    if (!breakState || breakRemaining(breakState, now) > 0) return;
    setBreak(null);
    alert(t('deepWork.alerts.breakDoneTitle'), t('deepWork.alerts.breakDoneBody'));
  }, [breakState, now, setBreak, alert, t]);

  // Título da aba com o tempo.
  useEffect(() => {
    const original = baseTitle.current;
    if (active) {
      const label = active.pausedAt ? t('deepWork.phase.paused') : t('deepWork.phase.focus');
      document.title = `${formatClock(displaySeconds(active, now))} · ${label}`;
    } else if (breakState) {
      document.title = `${formatClock(breakRemaining(breakState, now))} · ${t(`deepWork.phase.${breakState.kind}`)}`;
    } else {
      document.title = original;
    }
  }, [active, breakState, now, t]);

  useEffect(() => {
    const original = baseTitle.current;
    return () => {
      document.title = original;
    };
  }, []);

  return null;
}
