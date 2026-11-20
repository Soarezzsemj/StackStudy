import { useMemo, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  Button,
  Card,
  Chip,
  Heatmap,
  Icon,
  PageTitle,
  ProgressBar,
  StatBlock,
  Wordmark,
} from '@/components/ui';

const SWATCHES = [
  ['--bg', 'bg'],
  ['--surface', 'surface'],
  ['--surface-raised', 'surface-raised'],
  ['--border', 'border'],
  ['--border-control', 'border-control'],
  ['--text', 'text'],
  ['--text-secondary', 'text-secondary'],
  ['--text-muted', 'text-muted'],
  ['--text-on-space', 'text-on-space'],
  ['--action', 'action'],
  ['--action-hover', 'action-hover'],
  ['--space-deep', 'space-deep'],
  ['--space-glow', 'space-glow'],
  ['--danger', 'danger'],
  ['--warning', 'warning'],
] as const;

const SUBJECT_COLORS = ['blue', 'amber', 'purple', 'cyan', 'pink', 'lime'] as const;

/** Amostra fixa, para a página ficar igual em todo carregamento. */
// Matérias de exemplo de áreas diferentes, uma por cor (ordem de atribuição).
const SAMPLE_SUBJECTS = [
  ['calculus', 'var(--subject-blue)'],
  ['constitutionalLaw', 'var(--subject-amber)'],
  ['anatomy', 'var(--subject-purple)'],
  ['accounting', 'var(--subject-cyan)'],
  ['brazilianHistory', 'var(--subject-pink)'],
  ['programming', 'var(--subject-lime)'],
] as const;

function sampleMatrix(rows: number, cols: number): number[][] {
  let seed = 7;
  const next = () => {
    seed = (seed * 9301 + 49297) % 233280;
    return seed / 233280;
  };
  return Array.from({ length: rows }, () =>
    Array.from({ length: cols }, () => {
      const r = next();
      return r < 0.3 ? 0 : Math.round(r * 240);
    }),
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-4">
      <h2 className="label text-text-muted">{title}</h2>
      {children}
    </section>
  );
}

/** Rota interna (/_design) para conferir o design system. */
export function DesignPage() {
  const { t } = useTranslation();
  const heat = useMemo(() => sampleMatrix(7, 20), []);

  return (
    <main className="mx-auto flex max-w-6xl flex-col gap-12 px-4 py-10 lg:px-10">
      <div className="flex items-center justify-between">
        <Wordmark />
        <Link to="/" className="label text-text-secondary hover:text-action">
          {t('shell.root')}
        </Link>
      </div>
      <PageTitle
        eyebrow="/_design"
        title={t('design.title')}
        description={t('design.description')}
      />

      <Section title={t('design.colors')}>
        <div className="grid grid-cols-[repeat(auto-fill,minmax(150px,1fr))] gap-3">
          {SWATCHES.map(([token, name]) => (
            <div key={token} className="card-inner overflow-hidden">
              <div
                className="h-14 border-b border-border"
                style={{ background: `var(${token})` }}
              />
              <div className="p-3">
                <p className="text-sm font-semibold">{name}</p>
                <p className="font-mono text-xs text-text-muted">{token}</p>
              </div>
            </div>
          ))}
        </div>
        <div className="flex flex-wrap gap-2">
          {SUBJECT_COLORS.map((c) => (
            <Chip key={c} dotColor={`var(--subject-${c})`}>
              {c}
            </Chip>
          ))}
        </div>
      </Section>

      <Section title={t('design.typography')}>
        <div className="flex flex-col gap-4">
          <p className="page-title">Deep Work</p>
          <p className="card-title">{t('design.sampleCardTitle')}</p>
          <p className="label text-text-secondary">{t('design.statHours')}</p>
          <p className="tabular text-4xl font-[650]">25:00 · 8,75 · 3.42</p>
        </div>
      </Section>

      <Section title={t('design.buttons')}>
        <div className="flex flex-wrap items-center gap-3">
          <Button>
            <Icon name="deepWork" size={18} />
            {t('design.primary')}
          </Button>
          <Button variant="secondary">{t('design.secondary')}</Button>
          <Button variant="ghost">{t('design.secondary')}</Button>
          <Button disabled>{t('design.disabled')}</Button>
          <Button variant="secondary" iconOnly aria-label={t('shell.search')}>
            <Icon name="search" />
          </Button>
        </div>
      </Section>

      <Section title={t('design.cards')}>
        <div className="grid gap-4 md:grid-cols-2">
          <Card title={t('design.sampleCardTitle')} eyebrow={t('design.sampleCardCode')}>
            <p className="text-text-secondary">{t('design.sampleCardBody')}</p>
            <div className="card-inner mt-4 p-4">
              <ProgressBar value={62} label={t('design.progressLabel')} showValue />
            </div>
          </Card>
          <Card
            variant="hero"
            title={t('design.sampleHeroTitle')}
            eyebrow={t('nav.deepWork')}
            actions={<Chip tone="action">POMODORO</Chip>}
          >
            <p className="tabular mb-2 text-6xl font-[650] tracking-title">18:42</p>
            <p className="text-text-on-space">{t('design.sampleHeroBody')}</p>
          </Card>
        </div>
      </Section>

      <Section title={t('design.chips')}>
        <div className="flex flex-wrap gap-2">
          <Chip>TODO</Chip>
          <Chip tone="action">DONE</Chip>
          <Chip tone="warning">DOING</Chip>
          <Chip tone="danger">EXAM</Chip>
          <Chip tone="space">LAB</Chip>
          {SAMPLE_SUBJECTS.map(([key, color]) => (
            <Chip key={key} dotColor={color}>
              {t(`design.sampleSubjects.${key}`)}
            </Chip>
          ))}
        </div>
      </Section>

      <Section title={t('design.progress')}>
        <div className="flex max-w-md flex-col gap-4">
          <ProgressBar value={20} label={t('design.progressLabel')} showValue />
          <ProgressBar
            value={55}
            label={t('design.progressLabel')}
            color="var(--subject-cyan)"
            showValue
          />
          <ProgressBar
            value={90}
            label={t('design.progressLabel')}
            color="var(--subject-pink)"
            showValue
          />
        </div>
      </Section>

      <Section title={t('design.stats')}>
        <Card>
          <dl className="m-0 flex flex-wrap gap-10">
            <StatBlock label={t('design.statHours')} value="12,5" unit="h" />
            <StatBlock
              label={t('design.statStreak')}
              value="9"
              hint={t('design.days', { count: 9 })}
            />
            <StatBlock label={t('design.statGpa')} value="3.42" unit="/ 4.0" />
          </dl>
        </Card>
      </Section>

      <Section title={t('design.heatmap')}>
        <Card>
          <Heatmap
            values={heat}
            label={t('design.heatmapLabel')}
            cellTitle={(r, c, v) => `${v} min · ${r + 1}/${c + 1}`}
          />
        </Card>
      </Section>
    </main>
  );
}
