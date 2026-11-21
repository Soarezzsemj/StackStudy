import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { ErrorState, PageTitle } from '@/components/ui';
import { useActiveSession } from '@/features/deepwork/queries';
import { useNow } from '@/lib/useNow';
import { useAuthStore } from '@/stores/authStore';
import { useStackStore } from '@/stores/stackStore';
import { CockpitSkeleton } from './CockpitSkeleton';
import {
  DeadlinesCard,
  MiniHeatmapCard,
  NextExamCard,
  RoadmapCard,
  OnboardingCard,
  QuickActions,
  RiskCard,
  SummaryCards,
} from './CockpitSections';
import { NextStepCard, type NextStepHandlers } from './NextStepCard';
import { firstName, greetingKey, isBrandNew, nextStep, type OnboardingStepKey } from './nextStep';
import { useCockpit } from './queries';

/**
 * Tela inicial: saudação, próximo passo sugerido, resumo do dia, prazos, radar de risco, próxima
 * prova e mini heatmap. Tudo de uma chamada (GET /api/cockpit).
 */
export function CockpitPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const openDialog = useStackStore((s) => s.openDialog);
  const cockpit = useCockpit();
  const { data: active } = useActiveSession();
  // Atualiza a saudação e os "em N dias" de minuto em minuto.
  const now = new Date(useNow(true, 60_000));

  const name = firstName(user?.name);
  const title = name
    ? t(`cockpit.greeting.${greetingKey(now)}`, { name })
    : t(`cockpit.greeting.${greetingKey(now)}NoName`);
  const date = new Intl.DateTimeFormat(i18n.language, {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  }).format(now);

  function newTask() {
    navigate('/stack');
    openDialog({ kind: 'task' });
  }
  function setup(step: OnboardingStepKey) {
    if (step === 'focus') {
      navigate('/deep-work');
      return;
    }
    navigate('/stack');
    if (step === 'semester') openDialog({ kind: 'semesterForm' });
    else if (step === 'subject') openDialog({ kind: 'subject' });
    else openDialog({ kind: 'task' });
  }
  const handlers: NextStepHandlers = {
    goToSession: () => navigate('/deep-work'),
    setup,
    openStack: () => navigate('/stack'),
    review: () => navigate('/flashcards/review'),
    startFocus: () => navigate('/deep-work'),
  };

  return (
    <>
      <PageTitle title={title} description={date} />
      {cockpit.isPending ? (
        <CockpitSkeleton />
      ) : cockpit.isError ? (
        <ErrorState message={t('cockpit.loadError')} onRetry={() => void cockpit.refetch()} />
      ) : (
        <div className="flex flex-col gap-6">
          <NextStepCard
            step={nextStep(cockpit.data, { activeSession: !!active, now })}
            on={handlers}
          />
          <QuickActions
            focusActive={!!active}
            onFocus={() => navigate('/deep-work')}
            onReview={() => navigate('/flashcards/review')}
            onNewTask={newTask}
          />
          {isBrandNew(cockpit.data) ? (
            // Sem semestre/matéria: o checklist no lugar de cartões zerados.
            <OnboardingCard data={cockpit.data} onStep={setup} />
          ) : (
            <>
              <SummaryCards data={cockpit.data} />
              <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_380px]">
                <div className="flex min-w-0 flex-col gap-6">
                  <DeadlinesCard data={cockpit.data} onNewTask={newTask} />
                  <MiniHeatmapCard data={cockpit.data} />
                </div>
                <aside className="flex flex-col gap-6">
                  {!(
                    cockpit.data.onboarding.hasTask && cockpit.data.onboarding.hasFocusSession
                  ) && <OnboardingCard data={cockpit.data} onStep={setup} />}
                  <RiskCard data={cockpit.data} />
                  <NextExamCard data={cockpit.data} now={now} />
                  <RoadmapCard data={cockpit.data} />
                </aside>
              </div>
            </>
          )}
        </div>
      )}
    </>
  );
}
