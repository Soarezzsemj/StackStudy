import { useTranslation } from 'react-i18next';
import { Button, Card, Icon, type IconName } from '@/components/ui';
import { formatDue } from '@/features/stack/due';
import type { NextStep } from './nextStep';

export interface NextStepHandlers {
  goToSession: () => void;
  setup: (step: 'semester' | 'subject') => void;
  openStack: () => void;
  review: () => void;
  startFocus: () => void;
}

/** Cartão hero com o próximo passo sugerido e um botão para fazer agora. */
export function NextStepCard({ step, on }: { step: NextStep; on: NextStepHandlers }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;

  let icon: IconName = 'arrowRight';
  let title: string;
  let body: string;
  let action: { label: string; run: () => void } | undefined;
  switch (step.kind) {
    case 'activeSession':
      icon = 'deepWork';
      title = t('cockpit.next.activeSession.title');
      body = t('cockpit.next.activeSession.body');
      action = { label: t('cockpit.next.activeSession.action'), run: on.goToSession };
      break;
    case 'setup':
      icon = 'stack';
      title = t(`cockpit.next.setup.${step.step}.title`);
      body = t(`cockpit.next.setup.${step.step}.body`);
      action = {
        label: t(`cockpit.next.setup.${step.step}.action`),
        run: () => on.setup(step.step),
      };
      break;
    case 'overdue':
      icon = 'alert';
      title = t('cockpit.next.overdue.title', { title: step.task.title });
      body = t('cockpit.next.overdue.body', {
        count: step.count,
        due: formatDue(step.task.dueAt, step.task.dueAllDay, lang),
      });
      action = { label: t('cockpit.next.overdue.action'), run: on.openStack };
      break;
    case 'exam':
      icon = 'calendar';
      title = t('cockpit.next.exam.title', { title: step.task.title });
      body = t('cockpit.next.exam.body', {
        count: step.days,
        subject: step.task.subjectName ?? '',
      });
      action = { label: t('cockpit.next.exam.action'), run: on.startFocus };
      break;
    case 'review':
      icon = 'flashcards';
      title = t('cockpit.next.review.title', { count: step.cards });
      body = t('cockpit.next.review.body');
      action = { label: t('cockpit.next.review.action'), run: on.review };
      break;
    case 'dueSoon':
      icon = 'calendar';
      title = t('cockpit.next.dueSoon.title', { title: step.task.title });
      body = t('cockpit.next.dueSoon.body', {
        due: formatDue(step.task.dueAt, step.task.dueAllDay, lang),
      });
      action = { label: t('cockpit.next.dueSoon.action'), run: on.openStack };
      break;
    case 'focus':
      icon = 'deepWork';
      title = t('cockpit.next.focus.title', { count: step.remainingMinutes });
      body = t('cockpit.next.focus.body');
      action = { label: t('cockpit.next.focus.action'), run: on.startFocus };
      break;
    default:
      icon = 'check';
      title = t('cockpit.next.allDone.title');
      body = t('cockpit.next.allDone.body');
  }

  return (
    <Card variant="hero" eyebrow={t('cockpit.next.eyebrow')} aria-label={t('cockpit.next.eyebrow')}>
      <div className="flex flex-col gap-5 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex min-w-0 items-start gap-4">
          <span
            aria-hidden="true"
            className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-action/10 text-action"
          >
            <Icon name={icon} size={24} />
          </span>
          <div className="min-w-0">
            <h2 className="card-title">{title}</h2>
            <p className="mt-1 text-sm text-text-on-space">{body}</p>
          </div>
        </div>
        {action && (
          <Button className="shrink-0" onClick={action.run}>
            {action.label}
            <Icon name="arrowRight" size={18} />
          </Button>
        )}
      </div>
    </Card>
  );
}
