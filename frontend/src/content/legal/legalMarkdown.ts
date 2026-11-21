/*
 * Markdown dos documentos legais, sem HTML: vira uma árvore que o <LegalPage> desenha com
 * elementos React (nada de innerHTML; tag no texto aparece como texto, não executa).
 *
 * Blocos: título (#), seção (##, com âncora), parágrafo, lista (-) e tabela (| a | b |).
 * Em linha: só **negrito**.
 */

export type Inline = { type: 'text'; text: string } | { type: 'strong'; text: string };

export type Block =
  | { type: 'h1'; children: Inline[] }
  | { type: 'h2'; id: string; text: string; children: Inline[] }
  | { type: 'p'; children: Inline[] }
  | { type: 'ul'; items: Inline[][] }
  | { type: 'table'; head: Inline[][]; rows: Inline[][][] };

export function parseInline(text: string): Inline[] {
  const out: Inline[] = [];
  let last = 0;
  for (const match of text.matchAll(/\*\*([^*\n]+)\*\*/g)) {
    const at = match.index ?? 0;
    if (at > last) out.push({ type: 'text', text: text.slice(last, at) });
    out.push({ type: 'strong', text: match[1] ?? '' });
    last = at + match[0].length;
  }
  if (last < text.length) out.push({ type: 'text', text: text.slice(last) });
  return out;
}

/** Âncora estável a partir do título da seção ("6. Uso aceitável" → "6-uso-aceitavel"). */
export function slugify(text: string): string {
  return text
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

const cells = (line: string) =>
  line
    .trim()
    .replace(/^\||\|$/g, '')
    .split('|')
    .map((c) => parseInline(c.trim()));

export function parseLegalMarkdown(source: string): Block[] {
  const blocks: Block[] = [];
  const lines = source.replace(/\r\n?/g, '\n').split('\n');
  const used = new Set<string>();
  let i = 0;
  while (i < lines.length) {
    const line = lines[i] ?? '';
    if (line.trim() === '') {
      i++;
    } else if (line.startsWith('# ')) {
      blocks.push({ type: 'h1', children: parseInline(line.slice(2).trim()) });
      i++;
    } else if (line.startsWith('## ')) {
      const text = line.slice(3).trim();
      let id = slugify(text) || 'secao';
      for (let n = 2; used.has(id); n++) id = `${slugify(text)}-${n}`;
      used.add(id);
      blocks.push({ type: 'h2', id, text, children: parseInline(text) });
      i++;
    } else if (line.trim().startsWith('|')) {
      const rows: string[] = [];
      while (i < lines.length && (lines[i] ?? '').trim().startsWith('|')) rows.push(lines[i++]!);
      const [head = '', , ...body] = rows;
      blocks.push({
        type: 'table',
        head: cells(head),
        rows: body.filter((r) => !/^\|?[\s:|-]+\|?$/.test(r.trim())).map(cells),
      });
    } else if (/^\s*-\s+/.test(line)) {
      const items: Inline[][] = [];
      while (i < lines.length && /^\s*-\s+/.test(lines[i] ?? '')) {
        items.push(parseInline((lines[i++] ?? '').replace(/^\s*-\s+/, '')));
      }
      blocks.push({ type: 'ul', items });
    } else {
      const para: string[] = [];
      while (
        i < lines.length &&
        (lines[i] ?? '').trim() !== '' &&
        !/^(#|\||\s*-\s)/.test(lines[i]!)
      ) {
        para.push(lines[i++]!.trim());
      }
      if (para.length === 0) para.push(lines[i++]!.trim());
      blocks.push({ type: 'p', children: parseInline(para.join(' ')) });
    }
  }
  return blocks;
}

/** Texto puro de um trecho (título da aba, testes). */
export const plain = (inlines: Inline[]) => inlines.map((x) => x.text).join('');

/** Campos ainda entre colchetes (ex.: "[NOME COMPLETO]"), sem repetição. */
export function findPlaceholders(source: string): string[] {
  return [...new Set(source.match(/\[[^\]\n]+\]/g) ?? [])];
}
