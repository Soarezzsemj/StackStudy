import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { routes } from '@/app/router';
import { LEGAL_DOCUMENTS, type LegalKind } from '@/content/legal/documents';
import {
  findPlaceholders,
  parseLegalMarkdown,
  plain,
  slugify,
} from '@/content/legal/legalMarkdown';
import { CONTACT_EMAIL } from '@/lib/site';
import { LegalBlocks } from '@/pages/LegalPage';
import { useAuthStore } from '@/stores/authStore';
import { mockApi, renderWithProviders } from '@/test/utils';

const KINDS: LegalKind[] = ['terms', 'privacy'];
const normalize = (s: string) => s.replace(/\s+/g, ' ').trim();

function openPublic(path: string) {
  useAuthStore.getState().clear();
  mockApi({});
  return renderWithProviders(routes, { initialEntries: [path] });
}

describe('documentos legais', () => {
  it.each(KINDS)('%s: abre sem login com o texto integral, versão e data', async (kind) => {
    const doc = LEGAL_DOCUMENTS[kind];
    openPublic(doc.path);
    const blocks = parseLegalMarkdown(doc.source);
    const h1 = blocks.find((b) => b.type === 'h1')!;
    await screen.findByRole('heading', { level: 1, name: plain(h1.children) });
    expect(
      screen.getByText(`Versão ${doc.version} · Última atualização: 21 de novembro de 2026`),
    ).toBeInTheDocument();

    // Todo trecho do arquivo aparece na página (nada cortado nem resumido).
    const article = normalize(document.querySelector('article')!.textContent ?? '');
    for (const b of blocks) {
      const parts =
        b.type === 'ul'
          ? b.items.map(plain)
          : b.type === 'table'
            ? [...b.head, ...b.rows.flat()].map(plain)
            : [plain(b.children)];
      for (const part of parts) expect(article).toContain(normalize(part));
    }
  });

  it.each(KINDS)('%s: o índice tem uma âncora por seção e leva até ela', async (kind) => {
    const doc = LEGAL_DOCUMENTS[kind];
    const { router } = openPublic(doc.path);
    const toc = await screen.findByRole('navigation', { name: 'Nesta página' });
    const links = within(toc).getAllByRole('link');
    const sections = parseLegalMarkdown(doc.source).filter((b) => b.type === 'h2');
    expect(links).toHaveLength(sections.length);
    for (const link of links) {
      const id = link.getAttribute('href')!.slice(1);
      expect(document.getElementById(id)?.tagName).toBe('H2');
    }
    const last = links.at(-1)!;
    await userEvent.click(last);
    const target = document.getElementById(last.getAttribute('href')!.slice(1))!;
    expect(target).toHaveFocus();
    expect(router.state.location.hash).toBe(`#${target.id}`);
  });

  it('endereços antigos redirecionam, mantendo a âncora', async () => {
    const { router } = openPublic('/terms#17-contato');
    await screen.findByRole('heading', { level: 1, name: 'Termos de Uso do StackStudy' });
    expect(router.state.location.pathname).toBe('/termos');
    expect(router.state.location.hash).toBe('#17-contato');
    expect(document.getElementById('17-contato')).toHaveFocus();
  });

  it('rodapé do login: Termos, Privacidade e Contato', async () => {
    openPublic('/login');
    const footer = await screen.findByRole('navigation', { name: 'Documentos legais' });
    expect(within(footer).getByRole('link', { name: 'Termos de Uso' })).toHaveAttribute(
      'href',
      '/termos',
    );
    expect(within(footer).getByRole('link', { name: 'Política de Privacidade' })).toHaveAttribute(
      'href',
      '/privacidade',
    );
    expect(within(footer).getByRole('link', { name: 'Contato' })).toHaveAttribute(
      'href',
      `mailto:${CONTACT_EMAIL}`,
    );
  });

  it('rodapé do cadastro também tem os links', async () => {
    openPublic('/signup');
    const footer = await screen.findByRole('navigation', { name: 'Documentos legais' });
    expect(within(footer).getByRole('link', { name: 'Termos de Uso' })).toHaveAttribute(
      'href',
      '/termos',
    );
    expect(within(footer).getByRole('link', { name: 'Contato' })).toHaveAttribute(
      'href',
      `mailto:${CONTACT_EMAIL}`,
    );
  });

  it('o renderizador não executa nem cria HTML vindo do Markdown', () => {
    const evil = [
      '# Título <script>window.__pwned = 1</script>',
      '',
      '<img src="x" onerror="window.__pwned = 1">',
      '',
      '- item <iframe src="javascript:alert(1)"></iframe>',
      '',
      '| <b>col</b> | b |',
      '| --- | --- |',
      '| <script>1</script> | **forte** |',
    ].join('\n');
    const { container } = render(<LegalBlocks blocks={parseLegalMarkdown(evil)} />);
    expect(container.querySelector('script, img, iframe, b')).toBeNull();
    expect(container.textContent).toContain('<img src="x" onerror="window.__pwned = 1">');
    expect(container.querySelector('strong')).toHaveTextContent('forte');
    expect((window as { __pwned?: number }).__pwned).toBeUndefined();
  });

  it('âncoras sem acento e estáveis', () => {
    expect(slugify('6. Uso aceitável')).toBe('6-uso-aceitavel');
    expect(slugify('8. Cookies e armazenamento no navegador')).toBe(
      '8-cookies-e-armazenamento-no-navegador',
    );
  });

  it('o texto usa o e-mail de contato da configuração e a versão declarada', () => {
    for (const kind of KINDS) {
      const doc = LEGAL_DOCUMENTS[kind];
      expect(doc.source).toContain(CONTACT_EMAIL);
      expect(doc.source).toContain(`**Versão ${doc.version} ·`);
    }
  });

  it('a versão gravada no aceite (backend) é a dos arquivos', () => {
    const yml = readFileSync(
      resolve(process.cwd(), '../backend/src/main/resources/application.yml'),
      'utf-8',
    );
    expect(yml).toContain(`\${TERMS_VERSION:${LEGAL_DOCUMENTS.terms.version}}`);
    expect(yml).toContain(`\${PRIVACY_VERSION:${LEGAL_DOCUMENTS.privacy.version}}`);
  });

  // Os campos entre colchetes (responsável, CPF/CNPJ, cidade) são do dono do projeto. Fora de
  // produção o build passa; com APP_ENV=production o teste falha enquanto houver colchetes.
  it('sem campos pendentes entre colchetes em produção', () => {
    const pending = KINDS.flatMap((k) =>
      findPlaceholders(LEGAL_DOCUMENTS[k].source).map((p) => `${LEGAL_DOCUMENTS[k].path}: ${p}`),
    );
    if (process.env.APP_ENV === 'production') {
      expect(pending, 'Preencha os campos pendentes dos documentos legais').toEqual([]);
    } else {
      // Só os três campos conhecidos podem estar pendentes.
      const known = ['[NOME COMPLETO]', '[CPF/CNPJ]', '[CIDADE/UF]'];
      expect(pending.every((p) => known.some((k) => p.endsWith(k)))).toBe(true);
    }
  });
});
