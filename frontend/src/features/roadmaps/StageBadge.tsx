import { useTranslation } from 'react-i18next';
import { Chip, Icon } from '@/components/ui';
import type { RoadmapStage } from '@/lib/api/roadmaps';

/** Selo Grátis/Premium da etapa (com cadeado quando bloqueada). */
export function StageBadge({ stage }: { stage: Pick<RoadmapStage, 'access' | 'locked'> }) {
  const { t } = useTranslation();
  if (stage.locked) {
    return (
      <Chip tone="space">
        <Icon name="lock" size={12} />
        {t('roadmaps.access.locked')}
      </Chip>
    );
  }
  return stage.access === 'PREMIUM' ? (
    <Chip tone="space">{t('roadmaps.access.PREMIUM')}</Chip>
  ) : (
    <Chip tone="action">{t('roadmaps.access.FREE')}</Chip>
  );
}
