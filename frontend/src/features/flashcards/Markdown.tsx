import { useMemo } from 'react';
import { cn } from '@/lib/cn';
import { parseMarkdown, type Inline } from './markdownParse';

function Inlines({ items }: { items: Inline[] }) {
  return (
    <>
      {items.map((item, i) => {
        switch (item.type) {
          case 'br':
            return <br key={i} />;
          case 'strong':
            return <strong key={i}>{item.text}</strong>;
          case 'em':
            return <em key={i}>{item.text}</em>;
          case 'code':
            return (
              <code key={i} className="rounded bg-surface-raised px-1.5 py-0.5 text-[0.9em]">
                {item.text}
              </code>
            );
          default:
            return <span key={i}>{item.text}</span>;
        }
      })}
    </>
  );
}

/** Desenha o Markdown simples de uma carta (só elementos React, sem HTML cru). */
export function Markdown({ source, className }: { source: string; className?: string }) {
  const blocks = useMemo(() => parseMarkdown(source), [source]);
  return (
    <div className={cn('flex flex-col gap-3 break-words', className)}>
      {blocks.map((b, i) => {
        if (b.type === 'pre') {
          return (
            <pre
              key={i}
              className="overflow-x-auto rounded-inner bg-surface-raised p-3 text-left text-sm"
            >
              <code>{b.text}</code>
            </pre>
          );
        }
        if (b.type === 'h') {
          return (
            <p key={i} className={cn('font-[650]', b.level === 1 ? 'text-2xl' : 'text-xl')}>
              <Inlines items={b.children} />
            </p>
          );
        }
        if (b.type === 'ul' || b.type === 'ol') {
          const List = b.type;
          return (
            <List
              key={i}
              className={cn('m-0 pl-6 text-left', b.type === 'ul' ? 'list-disc' : 'list-decimal')}
            >
              {b.items.map((item, j) => (
                <li key={j}>
                  <Inlines items={item} />
                </li>
              ))}
            </List>
          );
        }
        return (
          <p key={i}>
            <Inlines items={b.children} />
          </p>
        );
      })}
    </div>
  );
}
