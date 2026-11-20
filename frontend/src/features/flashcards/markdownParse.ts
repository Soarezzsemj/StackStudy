/*
 * Markdown simples para as cartas, sem HTML: vira uma árvore que o <Markdown> desenha com
 * elementos React (nada de innerHTML, então não há como injetar script).
 *
 * Blocos: parágrafo (quebras de linha mantidas), título (#, ##, ###), lista (- ou *) e lista
 * numerada (1.), bloco de código (```). Em linha: **negrito**, *itálico* ou _itálico_, `código`.
 */

export type Inline =
  | { type: 'text'; text: string }
  | { type: 'strong'; text: string }
  | { type: 'em'; text: string }
  | { type: 'code'; text: string }
  | { type: 'br' };

export type Block =
  | { type: 'p'; children: Inline[] }
  | { type: 'h'; level: 1 | 2 | 3; children: Inline[] }
  | { type: 'ul'; items: Inline[][] }
  | { type: 'ol'; items: Inline[][] }
  | { type: 'pre'; text: string };

const INLINE = /(`[^`\n]+`|\*\*[^*\n]+\*\*|\*[^*\s][^*\n]*\*|_[^_\s][^_\n]*_)/g;

export function parseInline(text: string): Inline[] {
  const out: Inline[] = [];
  const lines = text.split('\n');
  lines.forEach((line, i) => {
    if (i > 0) out.push({ type: 'br' });
    let last = 0;
    for (const match of line.matchAll(INLINE)) {
      const token = match[0];
      const at = match.index ?? 0;
      if (at > last) out.push({ type: 'text', text: line.slice(last, at) });
      if (token.startsWith('`')) out.push({ type: 'code', text: token.slice(1, -1) });
      else if (token.startsWith('**')) out.push({ type: 'strong', text: token.slice(2, -2) });
      else out.push({ type: 'em', text: token.slice(1, -1) });
      last = at + token.length;
    }
    if (last < line.length) out.push({ type: 'text', text: line.slice(last) });
  });
  return out;
}

const BULLET = /^\s*[-*]\s+(.*)$/;
const NUMBERED = /^\s*\d+[.)]\s+(.*)$/;
const HEADING = /^(#{1,3})\s+(.*)$/;

export function parseMarkdown(source: string): Block[] {
  const blocks: Block[] = [];
  const lines = source.replace(/\r\n?/g, '\n').split('\n');
  let paragraph: string[] = [];

  const flush = () => {
    if (paragraph.length > 0) {
      blocks.push({ type: 'p', children: parseInline(paragraph.join('\n')) });
      paragraph = [];
    }
  };

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]!;
    if (line.trim().startsWith('```')) {
      flush();
      const code: string[] = [];
      i++;
      while (i < lines.length && !lines[i]!.trim().startsWith('```')) {
        code.push(lines[i]!);
        i++;
      }
      blocks.push({ type: 'pre', text: code.join('\n') });
      continue;
    }
    if (line.trim() === '') {
      flush();
      continue;
    }
    const heading = HEADING.exec(line);
    if (heading) {
      flush();
      blocks.push({
        type: 'h',
        level: heading[1]!.length as 1 | 2 | 3,
        children: parseInline(heading[2]!),
      });
      continue;
    }
    const listType = BULLET.test(line) ? 'ul' : NUMBERED.test(line) ? 'ol' : null;
    if (listType) {
      flush();
      const pattern = listType === 'ul' ? BULLET : NUMBERED;
      const items: Inline[][] = [];
      while (i < lines.length && pattern.test(lines[i]!)) {
        items.push(parseInline(pattern.exec(lines[i]!)![1]!));
        i++;
      }
      i--;
      blocks.push(listType === 'ul' ? { type: 'ul', items } : { type: 'ol', items });
      continue;
    }
    paragraph.push(line);
  }
  flush();
  return blocks;
}

/** Texto puro (sem marcações), para prévias de uma linha. */
export function plainText(source: string): string {
  const text = (items: Inline[]) => items.map((i) => (i.type === 'br' ? ' ' : i.text)).join('');
  return parseMarkdown(source)
    .map((b) => {
      switch (b.type) {
        case 'pre':
          return b.text;
        case 'ul':
        case 'ol':
          return b.items.map(text).join(' ');
        default:
          return text(b.children);
      }
    })
    .join(' ')
    .replace(/\s+/g, ' ')
    .trim();
}
