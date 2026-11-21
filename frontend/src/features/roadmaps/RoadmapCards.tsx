import { useEffect, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import {
  Button,
  Card,
  Chip,
  EmptyState,
  ErrorState,
  Icon,
  ProgressBar,
  SelectField,
  Skeleton,
  TextAreaField,
  TextField,
} from '@/components/ui';
import { errorMessage, fieldErrors } from '@/features/auth/errorMessage';
import type { RoadmapDetail, RoadmapItem } from '@/lib/api/roadmaps';
import { cn } from '@/lib/cn';
import { useDebouncedValue } from '@/lib/useDebouncedValue';
import {
  useFollowRoadmap,
  useRoadmapGoal,
  useRoadmapList,
  useSuggestRoadmap,
  useUnfollowRoadmap,
} from './queries';
import { currentNode, formatDay, formatHours, nodePercent, remainingHours } from './roadmapLogic';
import { TopicActions } from './TopicActions';

/** Faixa do lançamento (flag do Premium desligada). */
export function LaunchBanner() {
  const { t } = useTranslation();
  return (
    <p
      role="note"
      className="flex items-center gap-3 rounded-inner border border-action/40 bg-action/5 px-4 py-3 text-sm"
    >
      <Icon name="star" size={18} className="shrink-0 text-action" />
      {t('roadmaps.launch')}
    </p>
  );
}

/** Aviso de conteúdo de exemplo. */
export function SampleNote() {
  const { t } = useTranslation();
  return <p className="text-xs text-text-muted">{t('roadmaps.sampleNote')}</p>;
}

// ---------------------------------------------------------------------------
// resumo
// ---------------------------------------------------------------------------

export function SummaryCard({ detail }: { detail: RoadmapDetail }) {
  const { t, i18n } = useTranslation();
  const follow = useFollowRoadmap();
  const unfollow = useUnfollowRoadmap();
  const s = detail.summary;
  const h = (n: number) => formatHours(n, i18n.language);
  const pct = new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 1 }).format(s.percent);

  return (
    <Card
      variant="hero"
      eyebrow={detail.area}
      aria-label={t('roadmaps.summary.label', { title: detail.title })}
    >
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <h2 className="card-title m-0">{detail.title}</h2>
          <p className="mt-1 text-sm text-text-on-space">{detail.description}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {detail.sample && <Chip tone="warning">{t('roadmaps.sample')}</Chip>}
          {detail.following?.primary && <Chip tone="action">{t('roadmaps.primary')}</Chip>}
        </div>
      </div>

      {detail.following ? (
        <>
          <p className="mt-5 flex items-baseline gap-3">
            <span className="tabular text-5xl font-[650] leading-none tracking-card">{pct}%</span>
            <span className="text-sm text-text-on-space">
              {t('roadmaps.summary.topics', { done: s.doneTopics, total: s.totalTopics })}
            </span>
          </p>
          <ProgressBar
            value={s.percent}
            label={t('roadmaps.summary.progress', { percent: pct })}
            className="mt-3"
          />
          <dl className="m-0 mt-5 grid grid-cols-2 gap-4 text-sm sm:grid-cols-3">
            <div>
              <dt className="label text-text-on-space">{t('roadmaps.summary.done')}</dt>
              <dd className="tabular m-0 mt-1 text-xl font-semibold">{h(s.hoursDone)} h</dd>
            </div>
            <div>
              <dt className="label text-text-on-space">{t('roadmaps.summary.left')}</dt>
              <dd className="tabular m-0 mt-1 text-xl font-semibold">{h(s.hoursRemaining)} h</dd>
            </div>
            <div className="col-span-2 sm:col-span-1">
              <dt className="label text-text-on-space">{t('roadmaps.summary.eta')}</dt>
              <dd className="m-0 mt-1 text-xl font-semibold">
                {s.completed
                  ? t('roadmaps.summary.completed')
                  : s.estimatedCompletion
                    ? formatDay(s.estimatedCompletion, i18n.language)
                    : t('roadmaps.summary.noGoal')}
              </dd>
            </div>
          </dl>
          <div className="mt-5 flex flex-wrap gap-2">
            {!detail.following.primary && (
              <Button
                variant="secondary"
                disabled={follow.isPending}
                onClick={() => follow.mutate({ slug: detail.slug, primary: true })}
              >
                {t('roadmaps.makePrimary')}
              </Button>
            )}
            <Button
              variant="ghost"
              disabled={unfollow.isPending}
              onClick={() => unfollow.mutate(detail.slug)}
            >
              {t('roadmaps.unfollow')}
            </Button>
          </div>
        </>
      ) : (
        <div className="mt-5 flex flex-wrap items-center gap-4">
          <p className="text-sm text-text-on-space">
            {t('roadmaps.summary.preview', {
              count: s.totalTopics,
              hours: h(s.totalHours),
            })}
          </p>
          <Button disabled={follow.isPending} onClick={() => follow.mutate({ slug: detail.slug })}>
            <Icon name="plus" size={18} />
            {t('roadmaps.follow')}
          </Button>
        </div>
      )}
      {follow.error && (
        <p role="alert" className="mt-3 text-sm text-danger">
          {errorMessage(t, follow.error)}
        </p>
      )}
    </Card>
  );
}

// ---------------------------------------------------------------------------
// tópico atual
// ---------------------------------------------------------------------------

export function CurrentTopicCard({
  detail,
  onOpen,
}: {
  detail: RoadmapDetail;
  onOpen: (nodeId: string) => void;
}) {
  const { t, i18n } = useTranslation();
  const located = currentNode(detail);
  if (!detail.following) return null;
  if (!located) {
    return (
      <Card title={t('roadmaps.current.title')}>
        <p className="text-sm text-text-secondary">
          {detail.summary.completed
            ? t('roadmaps.current.allDone')
            : t('roadmaps.current.noneOpen')}
        </p>
      </Card>
    );
  }
  const { node, stage } = located;
  const pct = nodePercent(node);
  return (
    <Card title={t('roadmaps.current.title')} aria-label={t('roadmaps.current.title')}>
      <p className="label text-text-muted">
        {t('roadmaps.stage', { position: stage.position, title: stage.title })}
      </p>
      <h3 className="mt-1 text-2xl font-[650] tracking-card">{node.title}</h3>
      {node.description && <p className="mt-2 text-sm text-text-secondary">{node.description}</p>}
      <ProgressBar
        value={pct}
        label={t('roadmaps.current.progress', {
          spent: formatHours(node.hoursSpent, i18n.language),
          total: formatHours(node.estimatedHours, i18n.language),
        })}
        showValue
        className="mt-4"
      />
      <p className="tabular mt-2 text-xs text-text-secondary">
        {t('roadmaps.hoursLeft', {
          count: remainingHours(node),
          hours: formatHours(remainingHours(node), i18n.language),
        })}
      </p>
      {node.resources.length > 0 && (
        <ul
          className="m-0 mt-4 flex list-none flex-wrap gap-2 p-0"
          aria-label={t('roadmaps.node.resources')}
        >
          {node.resources.map((r) => (
            <li key={r.id}>
              <a
                href={r.url}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex min-h-11 items-center gap-2 rounded-pill border border-border px-4 text-sm hover:border-text-secondary"
              >
                {r.title}
                <Icon name="external" size={14} />
                <span className="sr-only">{t('roadmaps.node.newTab')}</span>
              </a>
            </li>
          ))}
        </ul>
      )}
      <div className="mt-5 flex flex-col gap-3">
        <TopicActions slug={detail.slug} node={node} />
        <Button variant="ghost" className="self-start" onClick={() => onOpen(node.id)}>
          {t('roadmaps.current.details')}
        </Button>
      </div>
    </Card>
  );
}

// ---------------------------------------------------------------------------
// lateral: meta, explorar, sugerir
// ---------------------------------------------------------------------------

export function GoalCard({ detail }: { detail: RoadmapDetail }) {
  const { t } = useTranslation();
  const goal = useRoadmapGoal(detail.slug);
  const saved = detail.following?.weeklyGoalHours;
  const [value, setValue] = useState(String(saved ?? 6));
  useEffect(() => setValue(String(saved ?? 6)), [saved]);
  const errors = fieldErrors(goal.error);
  if (!detail.following) return null;

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    goal.mutate(Number(value));
  }

  return (
    <Card title={t('roadmaps.goal.title')} aria-label={t('roadmaps.goal.title')}>
      <form onSubmit={onSubmit} className="flex flex-col gap-3">
        <TextField
          label={t('roadmaps.goal.label')}
          type="number"
          min={1}
          max={80}
          inputMode="numeric"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          hint={saved ? t('roadmaps.goal.current', { count: saved }) : t('roadmaps.goal.none')}
          error={errors.weeklyGoalHours ? t('roadmaps.goal.error') : undefined}
        />
        <Button type="submit" variant="secondary" disabled={goal.isPending}>
          {t('roadmaps.goal.save')}
        </Button>
      </form>
    </Card>
  );
}

export function ExploreCard({ activeSlug }: { activeSlug?: string }) {
  const { t, i18n } = useTranslation();
  const [area, setArea] = useState('');
  const [q, setQ] = useState('');
  const search = useDebouncedValue(q.trim(), 250);
  const list = useRoadmapList(area, search);
  const areas = list.data?.areas ?? [];

  return (
    <Card title={t('roadmaps.explore.title')} aria-label={t('roadmaps.explore.title')}>
      <div className="flex flex-col gap-3">
        <SelectField
          label={t('roadmaps.explore.area')}
          value={area}
          onChange={(e) => setArea(e.target.value)}
        >
          <option value="">{t('roadmaps.explore.allAreas')}</option>
          {areas.map((a) => (
            <option key={a} value={a}>
              {a}
            </option>
          ))}
        </SelectField>
        <TextField
          label={t('roadmaps.explore.search')}
          type="search"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        {list.isPending ? (
          <div className="flex flex-col gap-2" aria-hidden="true">
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-16 w-full" />
          </div>
        ) : list.isError ? (
          <ErrorState message={t('roadmaps.loadError')} onRetry={() => void list.refetch()} />
        ) : list.data.items.length === 0 ? (
          <EmptyState title={t('roadmaps.explore.empty')} />
        ) : (
          <ul className="m-0 flex list-none flex-col gap-2 p-0">
            {list.data.items.map((item) => (
              <ExploreItem
                key={item.id}
                item={item}
                active={item.slug === activeSlug}
                locale={i18n.language}
              />
            ))}
          </ul>
        )}
      </div>
    </Card>
  );
}

function ExploreItem({
  item,
  active,
  locale,
}: {
  item: RoadmapItem;
  active: boolean;
  locale: string;
}) {
  const { t } = useTranslation();
  return (
    <li>
      <Link
        to={`/roadmaps/${item.slug}`}
        aria-current={active ? 'page' : undefined}
        className={cn(
          'card-inner flex min-h-11 flex-col gap-1 px-3 py-2 hover:border-text-secondary',
          active && 'border-action',
        )}
      >
        <span className="flex items-center justify-between gap-2">
          <span className="font-semibold">{item.title}</span>
          {item.following && (
            <Chip tone="action">
              {item.percent !== undefined
                ? `${new Intl.NumberFormat(locale, { maximumFractionDigits: 1 }).format(item.percent)}%`
                : t('roadmaps.following')}
            </Chip>
          )}
        </span>
        <span className="text-xs text-text-secondary">
          {t('roadmaps.explore.meta', {
            area: item.area,
            count: item.topicCount,
            hours: formatHours(item.totalHours, locale),
          })}
        </span>
      </Link>
    </li>
  );
}

export function SuggestCard() {
  const { t } = useTranslation();
  const suggest = useSuggestRoadmap();
  const [title, setTitle] = useState('');
  const [area, setArea] = useState('');
  const [details, setDetails] = useState('');
  const errors = fieldErrors(suggest.error);
  const field = (name: string) => (errors[name] ? t('roadmaps.suggest.fieldError') : undefined);

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    suggest.mutate(
      { title: title.trim(), area: area.trim() || undefined, details: details.trim() || undefined },
      {
        onSuccess: () => {
          setTitle('');
          setArea('');
          setDetails('');
        },
      },
    );
  }

  return (
    <Card title={t('roadmaps.suggest.title')} aria-label={t('roadmaps.suggest.title')}>
      <form onSubmit={onSubmit} className="flex flex-col gap-3" noValidate>
        <p className="text-sm text-text-secondary">{t('roadmaps.suggest.hint')}</p>
        <TextField
          label={t('roadmaps.suggest.name')}
          value={title}
          maxLength={120}
          required
          onChange={(e) => setTitle(e.target.value)}
          error={field('title')}
        />
        <TextField
          label={t('roadmaps.suggest.area')}
          value={area}
          maxLength={60}
          onChange={(e) => setArea(e.target.value)}
          error={field('area')}
        />
        <TextAreaField
          label={t('roadmaps.suggest.details')}
          value={details}
          maxLength={1000}
          rows={3}
          onChange={(e) => setDetails(e.target.value)}
          error={field('details')}
        />
        <Button
          type="submit"
          variant="secondary"
          disabled={suggest.isPending || title.trim().length < 3}
        >
          {t('roadmaps.suggest.send')}
        </Button>
        <p aria-live="polite" className="text-sm">
          {suggest.isSuccess && <span className="text-action">{t('roadmaps.suggest.thanks')}</span>}
          {suggest.error && Object.keys(errors).length === 0 && (
            <span className="text-danger">{errorMessage(t, suggest.error)}</span>
          )}
        </p>
      </form>
    </Card>
  );
}
