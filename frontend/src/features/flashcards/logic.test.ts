import { describe, expect, it } from 'vitest';
import type { Card } from '@/lib/api/flashcards';
import { parseTags } from './csv';
import { addDays, lastDays } from './days';
import { parseInline, parseMarkdown, plainText } from './markdownParse';
import {
  currentCard,
  initialSession,
  isFinished,
  reviewKey,
  reviewReducer,
  summarize,
} from './reviewSession';

const card = (id: string): Card => ({
  id,
  deckId: 'd',
  front: `frente ${id}`,
  back: `verso ${id}`,
  tags: [],
  stage: 'NEW',
  intervalDays: 0,
  easeFactor: 2.5,
  repetitions: 0,
  lapses: 0,
  createdAt: '2026-11-20T00:00:00Z',
});

describe('markdown', () => {
  it('inline: negrito, itálico, código e quebra de linha', () => {
    expect(parseInline('a **b** *c* _d_ `e`\nf')).toEqual([
      { type: 'text', text: 'a ' },
      { type: 'strong', text: 'b' },
      { type: 'text', text: ' ' },
      { type: 'em', text: 'c' },
      { type: 'text', text: ' ' },
      { type: 'em', text: 'd' },
      { type: 'text', text: ' ' },
      { type: 'code', text: 'e' },
      { type: 'br' },
      { type: 'text', text: 'f' },
    ]);
  });

  it('não transforma asterisco solto (ex.: 2 * 3)', () => {
    expect(parseInline('2 * 3 = 6')).toEqual([{ type: 'text', text: '2 * 3 = 6' }]);
  });

  it('blocos: título, listas, código e parágrafos', () => {
    const blocks = parseMarkdown(
      '# Ossos\n\n- fêmur\n- tíbia\n\n1. um\n2. dois\n\n```\nx = 1\n```\nfim',
    );
    expect(blocks.map((b) => b.type)).toEqual(['h', 'ul', 'ol', 'pre', 'p']);
    expect(blocks[1]).toEqual({
      type: 'ul',
      items: [[{ type: 'text', text: 'fêmur' }], [{ type: 'text', text: 'tíbia' }]],
    });
    expect(blocks[3]).toEqual({ type: 'pre', text: 'x = 1' });
  });

  it('HTML vira texto (nada é interpretado)', () => {
    expect(parseMarkdown('<script>alert(1)</script>')).toEqual([
      { type: 'p', children: [{ type: 'text', text: '<script>alert(1)</script>' }] },
    ]);
  });

  it('plainText para prévias', () => {
    expect(plainText('**Fêmur**\n\n- osso\n- da coxa')).toBe('Fêmur osso da coxa');
  });
});

describe('sessão de revisão', () => {
  it('revela, responde, desfaz e termina', () => {
    let s = reviewReducer(initialSession(), {
      type: 'start',
      cards: [card('1'), card('2')],
      now: 0,
    });
    expect(currentCard(s)?.id).toBe('1');
    // Responder antes de revelar não depende do reducer (a tela bloqueia), mas revelar sim.
    s = reviewReducer(s, { type: 'reveal' });
    expect(s.revealed).toBe(true);
    s = reviewReducer(s, { type: 'answered', rating: 'GOOD', reviewId: 'r1' });
    expect(currentCard(s)?.id).toBe('2');
    expect(s.revealed).toBe(false);
    s = reviewReducer(s, { type: 'undone' });
    expect(currentCard(s)?.id).toBe('1');
    expect(s.revealed).toBe(true);
    expect(s.history).toHaveLength(0);
    s = reviewReducer(s, { type: 'answered', rating: 'AGAIN', reviewId: 'r2' });
    s = reviewReducer(s, { type: 'reveal' });
    s = reviewReducer(s, { type: 'answered', rating: 'EASY', reviewId: 'r3' });
    expect(isFinished(s)).toBe(true);
    // Revelar depois do fim não faz nada; desfazer volta para a última.
    expect(reviewReducer(s, { type: 'reveal' })).toBe(s);
    expect(currentCard(reviewReducer(s, { type: 'undone' }))?.id).toBe('2');

    const summary = summarize(s, 95_000);
    expect(summary.total).toBe(2);
    expect(summary.byRating).toEqual({ AGAIN: 1, HARD: 0, GOOD: 0, EASY: 1 });
    expect(summary.accuracyPct).toBe(50);
    expect(summary.elapsedSeconds).toBe(95);
  });

  it('desfazer sem histórico não muda nada', () => {
    const s = initialSession([card('1')]);
    expect(reviewReducer(s, { type: 'undone' })).toBe(s);
    expect(summarize(s, 0).accuracyPct).toBeNull();
  });

  it('teclas', () => {
    expect(reviewKey(' ', false)).toEqual({ type: 'reveal' });
    expect(reviewKey('Enter', false)).toEqual({ type: 'reveal' });
    expect(reviewKey(' ', true)).toBeNull();
    expect(reviewKey('1', false)).toBeNull();
    expect(reviewKey('1', true)).toEqual({ type: 'rate', rating: 'AGAIN' });
    expect(reviewKey('4', true)).toEqual({ type: 'rate', rating: 'EASY' });
    expect(reviewKey('u', true)).toEqual({ type: 'undo' });
    expect(reviewKey('x', true)).toBeNull();
  });
});

describe('tags e datas', () => {
  it('normaliza tags como o backend', () => {
    expect(parseTags(' Ossos, membro-inferior;ossos  ')).toEqual(['ossos', 'membro-inferior']);
    expect(parseTags('')).toEqual([]);
  });

  it('preenche dias sem dado', () => {
    expect(addDays('2026-02-28', 1)).toBe('2026-03-01');
    expect(lastDays('2026-11-20', 3, [{ date: '2026-11-19', count: 4 }])).toEqual([
      { date: '2026-11-18', count: 0 },
      { date: '2026-11-19', count: 4 },
      { date: '2026-11-20', count: 0 },
    ]);
  });
});
