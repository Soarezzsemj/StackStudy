import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Button, Icon } from '@/components/ui';
import type { RoadmapNode } from '@/lib/api/roadmaps';
import { useDeepWorkStore } from '@/stores/deepWorkStore';
import { useFlashcardStore } from '@/stores/flashcardStore';
import { useStackStore } from '@/stores/stackStore';
import { topicTag } from './roadmapLogic';

/**
 * Atalhos do tópico para as outras telas, reaproveitando os diálogos e endpoints que já existem:
 * nova tarefa (título do tópico), foco ligado ao tópico (as horas somam aqui) e nova carta (tag).
 */
export function TopicActions({
  slug,
  node,
  onNavigate,
}: {
  slug: string;
  node: RoadmapNode;
  onNavigate?: () => void;
}) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const openTask = useStackStore((s) => s.openDialog);
  const setTopic = useDeepWorkStore((s) => s.setTopic);
  const openCard = useFlashcardStore((s) => s.openDialog);

  function go(path: string, before: () => void) {
    before();
    onNavigate?.();
    navigate(path);
  }

  return (
    <div className="flex flex-wrap gap-2" role="group" aria-label={t('roadmaps.actions.label')}>
      <Button
        variant="secondary"
        onClick={() => go('/stack', () => openTask({ kind: 'task', title: node.title }))}
      >
        <Icon name="stack" size={18} />
        {t('roadmaps.actions.task')}
      </Button>
      <Button
        onClick={() => go('/deep-work', () => setTopic({ id: node.id, title: node.title, slug }))}
      >
        <Icon name="deepWork" size={18} />
        {t('roadmaps.actions.focus')}
      </Button>
      <Button
        variant="secondary"
        onClick={() =>
          go('/flashcards', () => openCard({ kind: 'card', tags: [topicTag(node.title)] }))
        }
      >
        <Icon name="flashcards" size={18} />
        {t('roadmaps.actions.cards')}
      </Button>
    </div>
  );
}
