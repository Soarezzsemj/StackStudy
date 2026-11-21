import { Navigate, useLocation } from 'react-router-dom';
import { LEGAL_DOCUMENTS, type LegalKind } from '@/content/legal/documents';

/** /terms e /privacy (links antigos) levam a /termos e /privacidade, mantendo a âncora. */
export function LegacyRedirect({ to }: { to: LegalKind }) {
  const { hash } = useLocation();
  return <Navigate to={{ pathname: LEGAL_DOCUMENTS[to].path, hash }} replace />;
}
