import { Fragment, useEffect, useMemo, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui';
import { LEGAL_DOCUMENTS, type LegalKind } from '@/content/legal/documents';
import { parseLegalMarkdown, plain, type Block, type Inline } from '@/content/legal/legalMarkdown';

function InlineText({ items }: { items: Inline[] }) {
  return (
    <>
      {items.map((x, i) =>
        x.type === 'strong' ? (
          <strong key={i} className="font-semibold text-text">
            {x.text}
          </strong>
        ) : (
          <Fragment key={i}>{x.text}</Fragment>
        ),
      )}
    </>
  );
}

function renderBlock(block: Block, key: number): ReactNode {
  switch (block.type) {
    case 'h1':
      return (
        <h1 key={key} className="page-title legal-title mb-4">
          <InlineText items={block.children} />
        </h1>
      );
    case 'h2':
      return (
        <h2 key={key} id={block.id} tabIndex={-1} className="card-title mb-3 mt-10 scroll-mt-6">
          <InlineText items={block.children} />
        </h2>
      );
    case 'ul':
      return (
        <ul key={key} className="mb-4 list-disc space-y-2 pl-6 leading-relaxed text-text-secondary">
          {block.items.map((item, i) => (
            <li key={i}>
              <InlineText items={item} />
            </li>
          ))}
        </ul>
      );
    case 'table':
      return (
        <div key={key} className="legal-table mb-4 overflow-x-auto">
          <table className="w-full border-collapse text-left text-sm">
            <thead>
              <tr>
                {block.head.map((c, i) => (
                  <th key={i} scope="col" className="label border-b border-border p-3 align-bottom">
                    <InlineText items={c} />
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {block.rows.map((row, r) => (
                <tr key={r} className="border-b border-border">
                  {row.map((c, i) => (
                    <td key={i} className="p-3 align-top leading-relaxed text-text-secondary">
                      <InlineText items={c} />
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      );
    default:
      return (
        <p key={key} className="mb-4 leading-relaxed text-text-secondary">
          <InlineText items={block.children} />
        </p>
      );
  }
}

/** Blocos do Markdown legal como elementos React (texto sempre como texto, nunca HTML). */
export function LegalBlocks({ blocks }: { blocks: Block[] }) {
  return <>{blocks.map(renderBlock)}</>;
}

/**
 * Termos de Uso e Política de Privacidade: o texto vem dos .md em `content/legal` (só pt-BR),
 * desenhado sem HTML cru. Índice com âncoras por seção.
 */
export function LegalPage({ kind }: { kind: LegalKind }) {
  const { t, i18n } = useTranslation();
  const { hash } = useLocation();
  const navigate = useNavigate();
  const doc = LEGAL_DOCUMENTS[kind];
  const blocks = useMemo(() => parseLegalMarkdown(doc.source), [doc.source]);
  const sections = blocks.filter((b) => b.type === 'h2');
  const titleBlock = blocks.find((b) => b.type === 'h1');
  const title = titleBlock ? plain(titleBlock.children) : '';
  // Título e linha de versão ficam antes do índice; o resto, depois.
  const firstSection = blocks.findIndex((b) => b.type === 'h2');
  const intro = firstSection < 0 ? blocks : blocks.slice(0, firstSection);
  const body = firstSection < 0 ? [] : blocks.slice(firstSection);

  useEffect(() => {
    document.title = `${title} · stack.study`;
  }, [title]);

  // Âncora no endereço (link direto ou clique no índice): rola até a seção e põe o foco nela.
  useEffect(() => {
    if (!hash) return;
    const el = document.getElementById(decodeURIComponent(hash.slice(1)));
    el?.scrollIntoView?.();
    el?.focus({ preventScroll: true });
  }, [hash]);

  return (
    <article lang="pt-BR" className="legal-doc">
      {!i18n.resolvedLanguage?.startsWith('pt') && (
        <p lang={i18n.resolvedLanguage} className="print-hidden mb-6 text-sm text-text-muted">
          {t('legal.ptOnly')}
        </p>
      )}
      <LegalBlocks blocks={intro} />
      <div className="print-hidden mb-2 flex flex-wrap gap-3">
        <Button variant="secondary" onClick={() => window.print()}>
          {t('legal.print')}
        </Button>
      </div>
      {sections.length > 0 && (
        <nav aria-labelledby="legal-toc" className="legal-toc card-inner mb-6 mt-6 p-5">
          <h2 id="legal-toc" className="label mb-3 text-text-secondary">
            {t('legal.toc')}
          </h2>
          <ol className="flex flex-col gap-2 text-sm">
            {sections.map((s) => (
              <li key={s.id}>
                <a
                  href={`#${s.id}`}
                  onClick={(e) => {
                    e.preventDefault();
                    navigate({ hash: s.id });
                    // Mesmo endereço de antes: o efeito não roda de novo, então foca aqui também.
                    const el = document.getElementById(s.id);
                    el?.scrollIntoView?.();
                    el?.focus({ preventScroll: true });
                  }}
                  className="text-text-secondary underline-offset-4 hover:text-text hover:underline"
                >
                  {s.text}
                </a>
              </li>
            ))}
          </ol>
        </nav>
      )}
      <LegalBlocks blocks={body} />
    </article>
  );
}
