import type { TFunction } from 'i18next';
import type { RoadmapDetail, RoadmapItem, RoadmapNode, RoadmapStage } from '@/lib/api/roadmaps';

/** Estado visual do nó (nunca só cor: cada um tem ícone e texto). */
export type NodeState = 'done' | 'current' | 'pending' | 'locked';

export function nodeState(node: RoadmapNode, stage: Pick<RoadmapStage, 'locked'>): NodeState {
  if (stage.locked) return 'locked';
  if (node.status === 'DONE') return 'done';
  if (node.current || node.status === 'DOING') return 'current';
  return 'pending';
}

/** Horas que faltam no nó (estimado − gasto, nunca negativo). */
export function remainingHours(node: Pick<RoadmapNode, 'estimatedHours' | 'hoursSpent'>): number {
  return Math.max(0, round1(node.estimatedHours - node.hoursSpent));
}

/** Quanto do estimado já foi feito (0–100). Sem estimativa, 0. */
export function nodePercent(node: Pick<RoadmapNode, 'estimatedHours' | 'hoursSpent'>): number {
  if (node.estimatedHours <= 0) return 0;
  return Math.min(100, Math.round((node.hoursSpent / node.estimatedHours) * 100));
}

function round1(n: number): number {
  return Math.round(n * 10) / 10;
}

export function formatHours(hours: number, locale: string): string {
  return new Intl.NumberFormat(locale, { maximumFractionDigits: 1 }).format(hours);
}

/** "Estruturas de dados, em andamento, 3 horas restantes, opcional". */
export function nodeLabel(
  t: TFunction,
  node: RoadmapNode,
  state: NodeState,
  locale: string,
): string {
  const parts = [node.title, t(`roadmaps.state.${state}`)];
  if (state !== 'done' && state !== 'locked') {
    const left = remainingHours(node);
    parts.push(t('roadmaps.hoursLeft', { count: left, hours: formatHours(left, locale) }));
  }
  if (node.optional) parts.push(t('roadmaps.optional'));
  return parts.join(', ');
}

/** Linha do mapa: o tópico principal no centro e os subtópicos de cada lado. */
export interface MapRow {
  main: RoadmapNode;
  left: RoadmapNode[];
  right: RoadmapNode[];
}

export function buildRows(stage: RoadmapStage): MapRow[] {
  const mains = stage.nodes.filter((n) => n.kind === 'MAIN');
  return mains.map((main) => {
    const subs = stage.nodes.filter((n) => n.kind === 'SUB' && n.parentId === main.id);
    return {
      main,
      left: subs.filter((s) => s.side === 'LEFT'),
      right: subs.filter((s) => s.side !== 'LEFT'),
    };
  });
}

/** Ordem do teclado (setas): etapa → tópico → subtópicos da esquerda → da direita. */
export function keyboardOrder(stages: RoadmapStage[]): string[] {
  const out: string[] = [];
  for (const stage of stages) {
    for (const row of buildRows(stage)) {
      out.push(row.main.id, ...row.left.map((n) => n.id), ...row.right.map((n) => n.id));
    }
  }
  return out;
}

/** Próximo id na ordem do teclado (para no começo e no fim). */
export function moveFocus(order: string[], fromId: string, key: string): string | undefined {
  const i = order.indexOf(fromId);
  if (i < 0) return order[0];
  switch (key) {
    case 'ArrowDown':
    case 'ArrowRight':
      return order[Math.min(order.length - 1, i + 1)];
    case 'ArrowUp':
    case 'ArrowLeft':
      return order[Math.max(0, i - 1)];
    case 'Home':
      return order[0];
    case 'End':
      return order[order.length - 1];
    default:
      return undefined;
  }
}

export interface Located {
  node: RoadmapNode;
  stage: RoadmapStage;
}

export function findNode(detail: RoadmapDetail, nodeId: string | undefined): Located | undefined {
  if (!nodeId) return undefined;
  for (const stage of detail.stages) {
    const node = stage.nodes.find((n) => n.id === nodeId);
    if (node) return { node, stage };
  }
  return undefined;
}

/** Tópico atual (o que o servidor marcou) ou nada (concluído / só falta conteúdo bloqueado). */
export function currentNode(detail: RoadmapDetail): Located | undefined {
  return findNode(detail, detail.following?.currentNodeId);
}

/** Etiqueta de tag para o flashcard ("Estruturas de dados" → "estruturas-de-dados"). */
export function topicTag(title: string): string {
  return title
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 40);
}

/** Roadmap a mostrar em /roadmaps: o principal; senão o primeiro seguido; senão nenhum. */
export function defaultSlug(items: RoadmapItem[]): string | undefined {
  return (items.find((i) => i.primary) ?? items.find((i) => i.following))?.slug;
}

/** Data AAAA-MM-DD (UTC) formatada sem deslocar o dia. */
export function formatDay(iso: string, locale: string): string {
  const [y, m, d] = iso.split('-').map(Number);
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeZone: 'UTC' }).format(
    new Date(Date.UTC(y!, (m ?? 1) - 1, d ?? 1)),
  );
}
