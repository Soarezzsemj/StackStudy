import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { PageTitle } from '@/components/ui';

interface Section {
  title: string;
  body: string[];
}

/**
 * Termos de uso e Política de Privacidade (LGPD), em pt-BR e en. O texto fica no i18n
 * (`legal.terms` / `legal.privacy`). Revise contato e responsável antes de divulgar.
 */
export function LegalPage({ kind }: { kind: 'terms' | 'privacy' }) {
  const { t } = useTranslation();
  const sections = t(`legal.${kind}.sections`, { returnObjects: true }) as Section[];
  const title = t(`legal.${kind}.title`);

  useEffect(() => {
    document.title = `${title} · stack.study`;
  }, [title]);

  return (
    <article>
      <PageTitle title={title} description={t('legal.updated', { date: t('legal.date') })} />
      <p className="mb-8 text-text-secondary">{t(`legal.${kind}.intro`)}</p>
      <div className="flex flex-col gap-8">
        {(Array.isArray(sections) ? sections : []).map((s, i) => (
          <section key={i} aria-labelledby={`legal-${kind}-${i}`}>
            <h2 id={`legal-${kind}-${i}`} className="card-title mb-3">
              {s.title}
            </h2>
            {s.body.map((p, j) => (
              <p key={j} className="mb-3 leading-relaxed text-text-secondary">
                {p}
              </p>
            ))}
          </section>
        ))}
      </div>
    </article>
  );
}
