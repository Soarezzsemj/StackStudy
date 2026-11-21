import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate, useParams } from 'react-router-dom';
import { Card, EmptyState, ErrorState, Icon, PageTitle, Skeleton } from '@/components/ui';
import { ApiError } from '@/lib/api/client';
import { cn } from '@/lib/cn';
import {
  CurrentTopicCard,
  ExploreCard,
  GoalCard,
  LaunchBanner,
  SampleNote,
  SuggestCard,
  SummaryCard,
} from './RoadmapCards';
import { NodeDialog } from './NodeDialog';
import { useRoadmapDetail, useRoadmapList } from './queries';
import { RoadmapList } from './RoadmapList';
import { RoadmapMap } from './RoadmapMap';
import { defaultSlug, findNode } from './roadmapLogic';

type View = 'map' | 'list';

/**
 * Roadmaps. /roadmaps abre o roadmap principal (ou convida a escolher um); /roadmaps/:slug abre
 * qualquer um, seguido ou não.
 */
export function RoadmapsPage() {
  const { t } = useTranslation();
  const { slug } = useParams();
  const list = useRoadmapList();

  if (!slug) {
    if (list.isPending) return <PageSkeleton />;
    if (list.isError) {
      return (
        <Layout aside={<Aside />}>
          <ErrorState message={t('roadmaps.loadError')} onRetry={() => void list.refetch()} />
        </Layout>
      );
    }
    const chosen = defaultSlug(list.data.items);
    if (chosen) return <Navigate to={`/roadmaps/${chosen}`} replace />;
    return (
      <Layout banner={!list.data.premiumEnabled} aside={<Aside />}>
        <Card>
          <EmptyState title={t('roadmaps.empty.title')} body={t('roadmaps.empty.body')} />
        </Card>
      </Layout>
    );
  }
  return <RoadmapView slug={slug} />;
}

function RoadmapView({ slug }: { slug: string }) {
  const { t } = useTranslation();
  const detail = useRoadmapDetail(slug);
  const [view, setView] = useState<View>('map');
  const [openId, setOpenId] = useState<string>();

  if (detail.isPending) return <PageSkeleton />;
  if (detail.isError) {
    const missing = detail.error instanceof ApiError && detail.error.status === 404;
    return (
      <Layout aside={<Aside slug={slug} />}>
        {missing ? (
          <Card>
            <EmptyState title={t('roadmaps.notFound')} body={t('roadmaps.empty.body')} />
          </Card>
        ) : (
          <ErrorState message={t('roadmaps.loadError')} onRetry={() => void detail.refetch()} />
        )}
      </Layout>
    );
  }

  const data = detail.data;
  const opened = findNode(data, openId);
  return (
    <Layout
      banner={!data.premiumEnabled}
      aside={<Aside slug={slug} goal={<GoalCard detail={data} />} />}
    >
      <SummaryCard detail={data} />
      <CurrentTopicCard detail={data} onOpen={setOpenId} />
      <Card
        title={t('roadmaps.trail.title')}
        aria-label={t('roadmaps.trail.title')}
        actions={
          <div
            role="group"
            aria-label={t('roadmaps.trail.view')}
            className="flex rounded-pill border border-border p-1"
          >
            {(['map', 'list'] as const).map((v) => (
              <button
                key={v}
                type="button"
                aria-pressed={view === v}
                onClick={() => setView(v)}
                className={cn(
                  'inline-flex min-h-9 items-center gap-2 rounded-pill px-4 text-sm font-semibold',
                  view === v ? 'bg-action text-action-ink' : 'text-text-secondary hover:text-text',
                )}
              >
                <Icon name={v === 'map' ? 'roadmap' : 'list'} size={16} />
                {t(`roadmaps.trail.${v}`)}
              </button>
            ))}
          </div>
        }
      >
        <p className="mb-6 text-sm text-text-secondary">{t('roadmaps.trail.hint')}</p>
        {view === 'map' ? (
          <RoadmapMap detail={data} onOpen={setOpenId} />
        ) : (
          <RoadmapList detail={data} onOpen={setOpenId} />
        )}
        {data.sample && (
          <div className="mt-6">
            <SampleNote />
          </div>
        )}
      </Card>
      {opened && (
        <NodeDialog
          key={opened.node.id}
          slug={data.slug}
          node={opened.node}
          stage={opened.stage}
          premiumEnabled={data.premiumEnabled}
          onClose={() => setOpenId(undefined)}
        />
      )}
    </Layout>
  );
}

function Layout({
  banner,
  aside,
  children,
}: {
  banner?: boolean;
  aside: ReactNode;
  children: ReactNode;
}) {
  const { t } = useTranslation();
  return (
    <>
      <PageTitle title={t('roadmaps.title')} description={t('roadmaps.description')} />
      <div className="flex flex-col gap-6">
        {banner && <LaunchBanner />}
        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_340px]">
          <div className="flex min-w-0 flex-col gap-6">{children}</div>
          <aside className="flex flex-col gap-6" aria-label={t('roadmaps.aside')}>
            {aside}
          </aside>
        </div>
      </div>
    </>
  );
}

function Aside({ slug, goal }: { slug?: string; goal?: ReactNode }) {
  return (
    <>
      {goal}
      <ExploreCard activeSlug={slug} />
      <SuggestCard />
    </>
  );
}

function PageSkeleton() {
  const { t } = useTranslation();
  return (
    <>
      <PageTitle title={t('roadmaps.title')} description={t('roadmaps.description')} />
      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_340px]" role="status">
        <span className="sr-only">{t('roadmaps.loading')}</span>
        <div className="flex flex-col gap-6" aria-hidden="true">
          <Skeleton className="h-56 w-full rounded-card" />
          <Skeleton className="h-40 w-full rounded-card" />
          <Skeleton className="h-96 w-full rounded-card" />
        </div>
        <div className="flex flex-col gap-6" aria-hidden="true">
          <Skeleton className="h-40 w-full rounded-card" />
          <Skeleton className="h-64 w-full rounded-card" />
        </div>
      </div>
    </>
  );
}
