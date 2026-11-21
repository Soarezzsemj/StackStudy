import { useTranslation } from 'react-i18next';
import { isRouteErrorResponse, Link, useRouteError } from 'react-router-dom';
import { Button, buttonClass, Card, Icon, Wordmark } from '@/components/ui';
import { isChunkLoadError } from '@/lib/errors';

/**
 * Erro inesperado numa tela (render quebrou ou o arquivo da tela não baixou). Nunca tela em
 * branco: explica e oferece recarregar ou voltar ao início. Depois de um deploy, os arquivos
 * antigos somem da Vercel e o navegador com a versão velha cai aqui: recarregar resolve.
 */
export function RouteErrorPage() {
  const { t } = useTranslation();
  const error = useRouteError();
  const notFound = isRouteErrorResponse(error) && error.status === 404;
  const chunk = isChunkLoadError(error);
  const kind = notFound ? 'notFound' : chunk ? 'newVersion' : 'generic';

  return (
    <main id="main" className="flex min-h-dvh items-center justify-center px-4 py-10">
      <div className="flex w-full max-w-lg flex-col gap-6">
        <Wordmark className="text-2xl" />
        <Card variant="hero" className="p-8" role="alert">
          <p className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-danger/10 text-danger">
            <Icon name="alert" size={24} />
          </p>
          <h1 className="page-title mb-3 text-[40px]">{t(`errorPage.${kind}.title`)}</h1>
          <p className="mb-8 text-text-on-space">{t(`errorPage.${kind}.body`)}</p>
          <div className="flex flex-wrap gap-3">
            {!notFound && (
              <Button onClick={() => window.location.reload()}>{t('errorPage.reload')}</Button>
            )}
            <Link to="/" className={buttonClass(notFound ? 'primary' : 'secondary')}>
              {t('errorPage.home')}
            </Link>
          </div>
        </Card>
      </div>
    </main>
  );
}
