import { useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Icon, type IconName } from '@/components/ui';
import type { RoadmapDetail, RoadmapNode, RoadmapStage } from '@/lib/api/roadmaps';
import { cn } from '@/lib/cn';
import {
  buildRows,
  formatHours,
  keyboardOrder,
  moveFocus,
  nodeLabel,
  nodePercent,
  nodeState,
  remainingHours,
  type NodeState,
} from './roadmapLogic';
import { StageBadge } from './StageBadge';

/*
 * Mapa da trilha: tópicos principais no centro, ligados por uma linha pontilhada; subtópicos nas
 * laterais (LEFT/RIGHT) com linha e bolinha. Tudo CSS (sem biblioteca de grafos). Em tela estreita
 * vira uma coluna só (os subtópicos descem para baixo do tópico).
 *
 * Teclado: um nó só fica no Tab (roving tabindex); setas, Home e End andam pela trilha.
 */

const STATE_ICON: Record<NodeState, IconName> = {
  done: 'check',
  current: 'target',
  pending: 'clock',
  locked: 'lock',
};

const STATE_CLASS: Record<NodeState, string> = {
  done: 'border-action bg-action/5',
  current: 'border-space-glow bg-space-deep/40 shadow-action',
  pending: 'border-border-control bg-surface',
  locked: 'border-border bg-surface text-text-muted',
};

interface MapProps {
  detail: RoadmapDetail;
  onOpen: (nodeId: string) => void;
}

export function RoadmapMap({ detail, onOpen }: MapProps) {
  const { t } = useTranslation();
  const order = useMemo(() => keyboardOrder(detail.stages), [detail.stages]);
  const initial = detail.following?.currentNodeId ?? order[0];
  const [focusId, setFocusId] = useState<string | undefined>(initial);
  const refs = useRef(new Map<string, HTMLButtonElement>());
  const tabStop = focusId && order.includes(focusId) ? focusId : order[0];

  function onKeyDown(e: KeyboardEvent<HTMLButtonElement>, id: string) {
    const next = moveFocus(order, id, e.key);
    if (!next) return;
    e.preventDefault();
    setFocusId(next);
    refs.current.get(next)?.focus();
  }

  const nodeProps = (node: RoadmapNode, stage: RoadmapStage) => ({
    node,
    stage,
    tabIndex: node.id === tabStop ? 0 : -1,
    onClick: () => {
      setFocusId(node.id);
      onOpen(node.id);
    },
    onKeyDown: (e: KeyboardEvent<HTMLButtonElement>) => onKeyDown(e, node.id),
    buttonRef: (el: HTMLButtonElement | null) => {
      if (el) refs.current.set(node.id, el);
      else refs.current.delete(node.id);
    },
  });

  return (
    <div className="relative" role="group" aria-label={t('roadmaps.map.label')}>
      {/* Linha vertical pontilhada que liga os tópicos principais, de etapa em etapa. */}
      <span
        aria-hidden="true"
        className="absolute bottom-6 left-1/2 top-6 -translate-x-1/2 border-l-2 border-dotted border-border-control"
      />
      <ol className="relative m-0 flex list-none flex-col gap-10 p-0">
        {detail.stages.map((stage) => (
          <li key={stage.id} aria-labelledby={`stage-${stage.id}`}>
            <StageHeader stage={stage} />
            <ul className="m-0 flex list-none flex-col gap-6 p-0">
              {buildRows(stage).map((row) => (
                <li
                  key={row.main.id}
                  className="flex flex-col items-center gap-3 md:grid md:grid-cols-[minmax(0,1fr)_14rem_minmax(0,1fr)] md:items-center md:gap-0"
                >
                  <div className="order-2 flex w-full flex-col items-center gap-2 md:order-1 md:items-end">
                    {row.left.map((sub) => (
                      <div
                        key={sub.id}
                        className="flex w-full max-w-xs items-center md:justify-end"
                      >
                        <MapNode {...nodeProps(sub, stage)} compact />
                        <Connector side="LEFT" />
                      </div>
                    ))}
                  </div>
                  <div className="order-1 w-full max-w-xs md:order-2">
                    <MapNode {...nodeProps(row.main, stage)} />
                  </div>
                  <div className="order-3 flex w-full flex-col items-center gap-2 md:items-start">
                    {row.right.map((sub) => (
                      <div key={sub.id} className="flex w-full max-w-xs items-center">
                        <Connector side="RIGHT" />
                        <MapNode {...nodeProps(sub, stage)} compact />
                      </div>
                    ))}
                  </div>
                </li>
              ))}
            </ul>
          </li>
        ))}
      </ol>
    </div>
  );
}

function StageHeader({ stage }: { stage: RoadmapStage }) {
  const { t } = useTranslation();
  return (
    <div className="relative mb-5 flex justify-center">
      <p
        id={`stage-${stage.id}`}
        className="flex flex-wrap items-center justify-center gap-2 rounded-pill border border-border bg-surface-raised px-4 py-2"
      >
        <span className="label text-text">
          {t('roadmaps.stage', { position: stage.position, title: stage.title })}
        </span>
        <span className="tabular text-xs text-text-secondary">
          {t('roadmaps.stageProgress', { done: stage.doneCount, total: stage.topicCount })}
        </span>
        <StageBadge stage={stage} />
      </p>
    </div>
  );
}

/** Linha pontilhada + bolinha entre o subtópico e o tópico (só em tela larga). */
function Connector({ side }: { side: 'LEFT' | 'RIGHT' }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'hidden shrink-0 items-center md:flex',
        side === 'LEFT' ? 'flex-row' : 'flex-row-reverse',
      )}
    >
      <span className="w-8 border-t-2 border-dotted border-border-control" />
      <span className="h-2.5 w-2.5 rounded-full border-2 border-border-control bg-bg" />
    </span>
  );
}

interface MapNodeProps {
  node: RoadmapNode;
  stage: RoadmapStage;
  compact?: boolean;
  tabIndex: number;
  onClick: () => void;
  onKeyDown: (e: KeyboardEvent<HTMLButtonElement>) => void;
  buttonRef: (el: HTMLButtonElement | null) => void;
}

function MapNode({ node, stage, compact, tabIndex, onClick, onKeyDown, buttonRef }: MapNodeProps) {
  const { t, i18n } = useTranslation();
  const state = nodeState(node, stage);
  const label = nodeLabel(t, node, state, i18n.language);
  const pct = nodePercent(node);
  return (
    <button
      ref={buttonRef}
      type="button"
      tabIndex={tabIndex}
      aria-label={label}
      aria-current={node.current ? 'step' : undefined}
      data-state={state}
      onClick={onClick}
      onKeyDown={onKeyDown}
      className={cn(
        'relative flex min-h-11 w-full flex-col gap-1 rounded-inner border-2 text-left transition-colors hover:border-text-secondary',
        compact ? 'px-3 py-2' : 'px-4 py-3',
        node.optional && 'border-dashed',
        STATE_CLASS[state],
      )}
    >
      <span className={cn('font-semibold leading-snug', compact ? 'text-sm' : 'text-base')}>
        {node.title}
      </span>
      <span
        className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-text-secondary"
        aria-hidden="true"
      >
        <span className={cn('inline-flex items-center gap-1', state === 'done' && 'text-action')}>
          <Icon name={STATE_ICON[state]} size={14} />
          {t(`roadmaps.state.${state}`)}
        </span>
        {state !== 'done' && state !== 'locked' && (
          <span className="tabular">
            {t('roadmaps.hoursShort', { hours: formatHours(remainingHours(node), i18n.language) })}
          </span>
        )}
        {node.optional && <span>{t('roadmaps.optional')}</span>}
      </span>
      {state === 'current' && (
        <span
          aria-hidden="true"
          className="mt-1 h-1.5 w-full overflow-hidden rounded-pill bg-border"
        >
          <span className="block h-full rounded-pill bg-action" style={{ width: `${pct}%` }} />
        </span>
      )}
    </button>
  );
}
