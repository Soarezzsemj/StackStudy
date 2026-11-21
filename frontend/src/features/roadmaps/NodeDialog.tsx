import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Chip, Dialog, Icon, TextField } from '@/components/ui';
import { errorMessage, fieldErrors } from '@/features/auth/errorMessage';
import type { ProgressStatus, RoadmapNode, RoadmapStage } from '@/lib/api/roadmaps';
import { cn } from '@/lib/cn';
import { useNodeProgress } from './queries';
import { formatHours, nodeState, remainingHours } from './roadmapLogic';
import { TopicActions } from './TopicActions';

const STATUSES: ProgressStatus[] = ['TODO', 'DOING', 'DONE'];

/** Painel do nó: descrição, materiais, horas e status. */
export function NodeDialog({
  slug,
  node,
  stage,
  premiumEnabled,
  onClose,
}: {
  slug: string;
  node: RoadmapNode;
  stage: RoadmapStage;
  premiumEnabled: boolean;
  onClose: () => void;
}) {
  const { t, i18n } = useTranslation();
  const progress = useNodeProgress(slug);
  const [hours, setHours] = useState(String(node.manualHours || ''));
  const state = nodeState(node, stage);
  const errors = fieldErrors(progress.error);
  const h = (n: number) => formatHours(n, i18n.language);

  function save(status: ProgressStatus) {
    const manual = hours.trim() === '' ? 0 : Number(hours.replace(',', '.'));
    progress.mutate({
      nodeId: node.id,
      input: { status, manualHours: Number.isFinite(manual) ? manual : undefined },
    });
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    save(node.status);
  }

  return (
    <Dialog title={node.title} onClose={onClose}>
      <div className="flex flex-col gap-5">
        <p className="flex flex-wrap items-center gap-2 text-sm">
          <Chip tone={state === 'done' ? 'action' : state === 'current' ? 'space' : 'neutral'}>
            {t(`roadmaps.state.${state}`)}
          </Chip>
          <span className="text-text-secondary">
            {t('roadmaps.stage', { position: stage.position, title: stage.title })}
          </span>
          {node.optional && <Chip>{t('roadmaps.optional')}</Chip>}
        </p>

        {stage.locked ? (
          <div className="card-inner flex flex-col gap-3 p-4">
            <p className="flex items-center gap-2 font-semibold">
              <Icon name="lock" size={18} />
              {t('roadmaps.locked.title')}
            </p>
            <p className="text-sm text-text-secondary">{t('roadmaps.locked.body')}</p>
            {/* Sem pagamento nesta fase: o botão fica desativado. */}
            <Button variant="secondary" disabled aria-describedby={`soon-${node.id}`}>
              {t('roadmaps.locked.plans')}
            </Button>
            <span id={`soon-${node.id}`} className="text-xs text-text-muted">
              {premiumEnabled ? t('roadmaps.locked.soon') : ''}
            </span>
          </div>
        ) : (
          <>
            {node.description && <p className="text-text-secondary">{node.description}</p>}

            <dl className="m-0 grid grid-cols-3 gap-3 text-sm">
              <div className="card-inner p-3">
                <dt className="label text-text-muted">{t('roadmaps.node.estimated')}</dt>
                <dd className="tabular m-0 mt-1 text-lg font-semibold">
                  {h(node.estimatedHours)} h
                </dd>
              </div>
              <div className="card-inner p-3">
                <dt className="label text-text-muted">{t('roadmaps.node.spent')}</dt>
                <dd className="tabular m-0 mt-1 text-lg font-semibold">{h(node.hoursSpent)} h</dd>
              </div>
              <div className="card-inner p-3">
                <dt className="label text-text-muted">{t('roadmaps.node.left')}</dt>
                <dd className="tabular m-0 mt-1 text-lg font-semibold">
                  {h(remainingHours(node))} h
                </dd>
              </div>
            </dl>
            {node.focusHours > 0 && (
              <p className="text-xs text-text-muted">
                {t('roadmaps.node.focus', { hours: h(node.focusHours) })}
              </p>
            )}

            <section aria-labelledby={`res-${node.id}`}>
              <h3 id={`res-${node.id}`} className="label mb-2 text-text-muted">
                {t('roadmaps.node.resources')}
              </h3>
              {node.resources.length === 0 ? (
                <p className="text-sm text-text-secondary">{t('roadmaps.node.noResources')}</p>
              ) : (
                <ul className="m-0 flex list-none flex-col gap-2 p-0">
                  {node.resources.map((r) => (
                    <li key={r.id}>
                      <a
                        href={r.url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="card-inner flex min-h-11 items-center gap-3 px-3 py-2 text-sm hover:border-text-secondary"
                      >
                        <span className="flex-1">
                          <span className="font-semibold">{r.title}</span>
                          <span className="ml-2 text-xs text-text-muted">
                            {t(`roadmaps.resourceKind.${r.kind}`)}
                          </span>
                        </span>
                        <Icon name="external" size={16} />
                        <span className="sr-only">{t('roadmaps.node.newTab')}</span>
                      </a>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <form onSubmit={onSubmit} className="flex items-end gap-3">
              <TextField
                label={t('roadmaps.node.manualHours')}
                hint={t('roadmaps.node.manualHint')}
                inputMode="decimal"
                value={hours}
                onChange={(e) => setHours(e.target.value)}
                error={errors.manualHours ? t('roadmaps.node.hoursError') : undefined}
                className="w-28"
              />
              <Button type="submit" variant="secondary" disabled={progress.isPending}>
                {t('roadmaps.node.saveHours')}
              </Button>
            </form>

            <div
              role="group"
              aria-label={t('roadmaps.node.status')}
              className="flex flex-wrap gap-2"
            >
              {STATUSES.map((s) => (
                <Button
                  key={s}
                  variant={node.status === s ? 'primary' : 'secondary'}
                  aria-pressed={node.status === s}
                  disabled={progress.isPending}
                  onClick={() => save(s)}
                  className={cn(node.status === s && 'pointer-events-none')}
                >
                  {s === 'DONE' && <Icon name="check" size={16} />}
                  {t(`roadmaps.status.${s}`)}
                </Button>
              ))}
            </div>
            {progress.error && !errors.manualHours && (
              <p role="alert" className="text-sm text-danger">
                {errorMessage(t, progress.error)}
              </p>
            )}

            <TopicActions slug={slug} node={node} onNavigate={onClose} />
          </>
        )}
      </div>
    </Dialog>
  );
}
