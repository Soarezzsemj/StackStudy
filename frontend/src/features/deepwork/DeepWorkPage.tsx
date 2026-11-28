import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { Button, buttonClass, Card, ErrorState, LoadingState, PageTitle } from '@/components/ui';
import { useCurrentSubjects } from '@/features/stack/queries';
import type { StudySession } from '@/lib/api/deepwork';
import { hasModifier, isControlTarget, isTypingTarget } from '@/lib/keyboard';
import { useDeepWorkStore } from '@/stores/deepWorkStore';
import { useUiStore } from '@/stores/uiStore';
import { CompleteDialog, ManualSessionDialog, SettingsDialog } from './DeepWorkDialogs';
import { FocusHeatmap, HistoryCard, StatsSection } from './InsightsSections';
import { DEFAULT_SETTINGS } from './pomodoro';
import { useActiveSession, useDeepWorkSettings, useSessionActions } from './queries';
import { TimerCard } from './TimerCard';

type PageDialog =
  | { kind: 'complete' }
  | { kind: 'settings' }
  | { kind: 'manual'; session?: StudySession }
  | null;

/** Deep Work: timer grande no topo; estatísticas, heatmap e histórico abaixo. */
export function DeepWorkPage() {
  const { t } = useTranslation();
  const active = useActiveSession();
  const settingsQuery = useDeepWorkSettings();
  const settings = settingsQuery.data ?? DEFAULT_SETTINGS;
  const { list: subjects, subjects: allSubjects } = useCurrentSubjects();
  const subjectsById = useMemo(
    () => new Map((allSubjects.data ?? []).map((s) => [s.id, s] as const)),
    [allSubjects.data],
  );
  const { start, pause, resume } = useSessionActions();
  const [dialog, setDialog] = useState<PageDialog>(null);
  const paletteOpen = useUiStore((s) => s.paletteOpen);
  const session = active.data ?? null;

  function startFocus() {
    const { mode, subjectId, taskId, topic, setBreak, setTopic } = useDeepWorkStore.getState();
    if (!subjectId) return;
    setBreak(null);
    start.mutate(
      {
        subjectId,
        taskId: taskId || null,
        mode,
        plannedMinutes: mode === 'POMODORO' ? settings.focusMinutes : undefined,
        // Só manda o campo com tópico escolhido no roadmap.
        ...(topic ? { roadmapNodeId: topic.id } : {}),
      },
      // A sessão leva o tópico; a próxima começa sem ele.
      { onSuccess: () => setTopic(null) },
    );
  }

  // Espaço: inicia, pausa ou retoma (fora de campos e botões).
  useEffect(() => {
    if (dialog || paletteOpen) return;
    function onKeyDown(e: KeyboardEvent) {
      if (e.key !== ' ' || e.repeat || hasModifier(e)) return;
      // Sessão de sala: controlada pela tela da sala.
      if (session?.roomId) return;
      if (isTypingTarget(e.target) || isControlTarget(e.target)) return;
      e.preventDefault();
      if (session) {
        if (session.pausedAt) resume.mutate(session.id);
        else pause.mutate(session.id);
      } else {
        startFocus();
      }
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  });

  return (
    <>
      <PageTitle
        title={t('pages.deepWork.title')}
        eyebrow={t('pages.deepWork.eyebrow')}
        actions={
          <Button variant="secondary" onClick={() => setDialog({ kind: 'settings' })}>
            {t('deepWork.settings.open')}
          </Button>
        }
      />

      <div className="flex flex-col gap-6">
        {active.isPending ? (
          <LoadingState />
        ) : active.isError ? (
          <ErrorState message={t('deepWork.loadError')} onRetry={() => void active.refetch()} />
        ) : session?.roomId ? (
          <Card variant="hero" title={t('deepWork.inRoom.title')}>
            <p className="mb-4 text-sm text-text-on-space">{t('deepWork.inRoom.body')}</p>
            <Link to={`/rooms/${session.roomId}`} className={buttonClass('primary')}>
              {t('deepWork.inRoom.open')}
            </Link>
          </Card>
        ) : (
          <TimerCard
            active={session}
            settings={settings}
            subjects={subjects}
            subjectsById={subjectsById}
            onStart={startFocus}
            onComplete={() => setDialog({ kind: 'complete' })}
          />
        )}
        <p className="-mt-3 text-center text-xs text-text-muted">
          <span className="sr-only">{t('stack.shortcuts.label')}: </span>
          {t('deepWork.shortcutHint')}
        </p>

        <StatsSection />
        <FocusHeatmap />
        <HistoryCard
          subjectsById={subjectsById}
          onAdd={() => setDialog({ kind: 'manual' })}
          onEdit={(s) => setDialog({ kind: 'manual', session: s })}
        />
      </div>

      {dialog?.kind === 'complete' && session && (
        <CompleteDialog session={session} onClose={() => setDialog(null)} />
      )}
      {dialog?.kind === 'settings' && (
        <SettingsDialog settings={settings} onClose={() => setDialog(null)} />
      )}
      {dialog?.kind === 'manual' && (
        <ManualSessionDialog
          session={dialog.session}
          subjects={subjectsWith(subjects, dialog.session?.subjectId, subjectsById)}
          defaultSubjectId={useDeepWorkStore.getState().subjectId || undefined}
          onClose={() => setDialog(null)}
        />
      )}
    </>
  );
}

/** Matérias do semestre + a da sessão editada, se for de outro semestre. */
function subjectsWith(
  list: ReturnType<typeof useCurrentSubjects>['list'],
  extraId: string | undefined,
  byId: Map<string, (typeof list)[number]>,
) {
  const extra = extraId ? byId.get(extraId) : undefined;
  return extra && !list.some((s) => s.id === extra.id) ? [...list, extra] : list;
}
