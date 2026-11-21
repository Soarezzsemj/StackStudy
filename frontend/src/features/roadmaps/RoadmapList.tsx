import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Icon, ProgressBar } from '@/components/ui';
import type { RoadmapDetail, RoadmapNode, RoadmapStage } from '@/lib/api/roadmaps';
import { cn } from '@/lib/cn';
import { buildRows, formatHours, nodeLabel, nodeState, remainingHours } from './roadmapLogic';
import { StageBadge } from './StageBadge';

/** Lista: etapas expansíveis; cada tópico abre o mesmo painel do mapa. */
export function RoadmapList({
  detail,
  onOpen,
}: {
  detail: RoadmapDetail;
  onOpen: (nodeId: string) => void;
}) {
  // Abre a etapa do tópico atual (ou a primeira).
  const currentStage =
    detail.stages.find((s) => s.nodes.some((n) => n.current))?.id ?? detail.stages[0]?.id;
  const [open, setOpen] = useState<Set<string>>(() => new Set(currentStage ? [currentStage] : []));
  const toggle = (id: string) =>
    setOpen((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  return (
    <ol className="m-0 flex list-none flex-col gap-3 p-0">
      {detail.stages.map((stage) => (
        <StageItem
          key={stage.id}
          stage={stage}
          expanded={open.has(stage.id)}
          onToggle={() => toggle(stage.id)}
          onOpen={onOpen}
        />
      ))}
    </ol>
  );
}

function StageItem({
  stage,
  expanded,
  onToggle,
  onOpen,
}: {
  stage: RoadmapStage;
  expanded: boolean;
  onToggle: () => void;
  onOpen: (nodeId: string) => void;
}) {
  const { t } = useTranslation();
  const panelId = `stage-panel-${stage.id}`;
  const title = t('roadmaps.stage', { position: stage.position, title: stage.title });
  const pct = stage.topicCount ? Math.round((stage.doneCount / stage.topicCount) * 100) : 0;
  return (
    <li className="card-inner overflow-hidden">
      <h3 className="m-0">
        <button
          type="button"
          aria-expanded={expanded}
          aria-controls={panelId}
          onClick={onToggle}
          className="flex min-h-11 w-full flex-wrap items-center gap-3 px-4 py-3 text-left"
        >
          <Icon
            name="arrowRight"
            size={16}
            className={cn('shrink-0 transition-transform', expanded && 'rotate-90')}
          />
          <span className="label flex-1 text-text">{title}</span>
          <span className="tabular text-xs text-text-secondary">
            {t('roadmaps.stageProgress', { done: stage.doneCount, total: stage.topicCount })}
          </span>
          <StageBadge stage={stage} />
        </button>
      </h3>
      <div className="px-4 pb-2">
        <ProgressBar value={pct} label={`${title}: ${pct}%`} />
      </div>
      {expanded && (
        <ul id={panelId} className="m-0 flex list-none flex-col gap-1 p-2">
          {buildRows(stage).map((row) => (
            <li key={row.main.id}>
              <NodeRow node={row.main} stage={stage} onOpen={onOpen} />
              {[...row.left, ...row.right].length > 0 && (
                <ul className="m-0 ml-6 flex list-none flex-col gap-1 border-l-2 border-dotted border-border-control p-0 pl-3">
                  {[...row.left, ...row.right].map((sub) => (
                    <li key={sub.id}>
                      <NodeRow node={sub} stage={stage} onOpen={onOpen} />
                    </li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </ul>
      )}
    </li>
  );
}

const ICON = { done: 'check', current: 'target', pending: 'clock', locked: 'lock' } as const;

function NodeRow({
  node,
  stage,
  onOpen,
}: {
  node: RoadmapNode;
  stage: RoadmapStage;
  onOpen: (nodeId: string) => void;
}) {
  const { t, i18n } = useTranslation();
  const state = nodeState(node, stage);
  return (
    <button
      type="button"
      aria-label={nodeLabel(t, node, state, i18n.language)}
      aria-current={node.current ? 'step' : undefined}
      data-state={state}
      onClick={() => onOpen(node.id)}
      className={cn(
        'flex min-h-11 w-full items-center gap-3 rounded-inner px-3 py-2 text-left hover:bg-surface-raised',
        node.current && 'bg-space-deep/40',
      )}
    >
      <Icon
        name={ICON[state]}
        size={18}
        className={cn('shrink-0', state === 'done' ? 'text-action' : 'text-text-secondary')}
      />
      <span className={cn('flex-1', node.kind === 'SUB' ? 'text-sm' : 'font-semibold')}>
        {node.title}
        {node.optional && (
          <span className="ml-2 text-xs text-text-muted">{t('roadmaps.optional')}</span>
        )}
      </span>
      <span className="text-xs text-text-secondary" aria-hidden="true">
        {t(`roadmaps.state.${state}`)}
      </span>
      {state !== 'done' && state !== 'locked' && (
        <span className="tabular w-14 text-right text-xs text-text-muted" aria-hidden="true">
          {t('roadmaps.hoursShort', { hours: formatHours(remainingHours(node), i18n.language) })}
        </span>
      )}
    </button>
  );
}
