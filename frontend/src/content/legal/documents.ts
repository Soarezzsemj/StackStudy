import privacidade from './privacidade.pt-BR.md?raw';
import termos from './termos.pt-BR.md?raw';

/**
 * Documentos legais publicados. Nova versão: edite o .md (versão e data no topo), troque
 * `version` aqui e `TERMS_VERSION`/`PRIVACY_VERSION` no backend (ver CLAUDE.md).
 */
export type LegalKind = 'terms' | 'privacy';

export interface LegalDocument {
  kind: LegalKind;
  path: string;
  /** Endereço antigo (Fase 7), redirecionado para o novo. */
  legacyPath: string;
  version: string;
  source: string;
}

export const LEGAL_DOCUMENTS: Record<LegalKind, LegalDocument> = {
  terms: { kind: 'terms', path: '/termos', legacyPath: '/terms', version: '1.0', source: termos },
  privacy: {
    kind: 'privacy',
    path: '/privacidade',
    legacyPath: '/privacy',
    version: '1.0',
    source: privacidade,
  },
};
