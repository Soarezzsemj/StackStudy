import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { buttonClass, Icon, Wordmark } from '@/components/ui';
import { LegalLinks } from '@/components/shell/LegalLinks';

/**
 * Painel de marca das telas de entrada (só em tela larga): slogan, 3 benefícios e um cartão no
 * rodapé (no login, "sua semana te espera"; no cadastro, a prévia do perfil).
 */
export function AuthBrand({ label, children }: { label: string; children?: ReactNode }) {
  const { t } = useTranslation();
  return (
    <aside
      aria-label={label}
      className="auth-brand relative hidden flex-col justify-between gap-10 overflow-hidden border-r border-border p-10 lg:flex xl:px-14"
    >
      <div className="flex max-w-lg flex-col gap-10">
        <Wordmark className="text-2xl" />
        <div className="flex flex-col gap-5">
          <p className="eyebrow text-text-secondary">{t('auth.tagline')}</p>
          <p className="page-title text-[clamp(2.5rem,4vw,var(--text-page-title))]">
            {t('signup.brand.slogan')}
          </p>
          <p className="text-lg text-text-secondary">{t('auth.brand.description')}</p>
        </div>
        <ul className="m-0 flex list-none flex-col gap-4 p-0">
          {(['one', 'two', 'three'] as const).map((k) => (
            <li key={k} className="flex items-center gap-3">
              <span
                aria-hidden="true"
                className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full border border-space-glow/30 bg-space-deep/60"
              >
                <Icon name="check" size={16} className="text-action" />
              </span>
              {t(`signup.brand.benefits.${k}`)}
            </li>
          ))}
        </ul>
      </div>
      {children}
    </aside>
  );
}

/** Link do canto superior direito ("Novo por aqui? CRIAR CONTA" / "Já tem conta? ENTRAR"). */
export function AuthCorner({ prompt, to, label }: { prompt: string; to: string; label: string }) {
  return (
    <p className="m-0 flex items-center gap-3 text-sm text-text-secondary">
      <span className="hidden sm:inline">{prompt}</span>
      <Link to={to} className={buttonClass('secondary')}>
        {label}
      </Link>
    </p>
  );
}

/**
 * Duas áreas: marca à esquerda (tela larga) e formulário à direita (a tela toda no celular, com
 * o wordmark no topo).
 */
export function AuthFrame({
  brand,
  corner,
  children,
}: {
  brand: ReactNode;
  corner?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="grid min-h-dvh lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
      {brand}
      <main id="main" className="flex min-w-0 flex-col px-4 py-4 sm:px-8 sm:py-6">
        <div className="flex min-h-11 items-center justify-between gap-3">
          <Wordmark className="text-2xl lg:invisible" />
          {corner}
        </div>
        <div className="flex flex-1 justify-center py-8 lg:items-center lg:py-12">
          <div className="flex w-full max-w-[480px] flex-col gap-6">
            {children}
            <LegalLinks className="justify-center text-xs" />
          </div>
        </div>
      </main>
    </div>
  );
}

/** Cabeçalho do formulário: rótulo pequeno, título grande e uma linha de apoio. */
export function AuthHeading({
  eyebrow,
  title,
  subtitle,
}: {
  eyebrow?: ReactNode;
  title: ReactNode;
  subtitle?: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-2">
      {eyebrow && <p className="eyebrow">{eyebrow}</p>}
      <h1 className="auth-title">{title}</h1>
      {subtitle && <p className="text-text-secondary">{subtitle}</p>}
    </div>
  );
}

// Classes completas (o Tailwind não enxerga nome montado em tempo de execução).
const WEEK_LEVELS = [
  'bg-heat-2',
  'bg-heat-3',
  'bg-heat-4',
  'bg-heat-3',
  'bg-heat-2',
  'bg-heat-1',
  'bg-heat-0',
];

/** Cartão do rodapé do painel de marca no login (decorativo: não mostra dado nenhum). */
function WeekAwaits() {
  const { t } = useTranslation();
  return (
    <section className="card flex max-w-md flex-col gap-3 p-5">
      <p className="label text-text-muted">{t('auth.brand.weekTitle')}</p>
      <div aria-hidden="true" className="flex gap-1.5">
        {WEEK_LEVELS.map((cls, i) => (
          <span key={i} className={`h-4 w-4 rounded-[4px] ${cls}`} />
        ))}
      </div>
      <p className="text-sm text-text-secondary">{t('auth.brand.weekBody')}</p>
    </section>
  );
}

/** Telas simples de entrada (login, confirmação de e-mail). */
export function AuthLayout({
  eyebrow,
  title,
  subtitle,
  children,
  footer,
  notice,
  corner,
}: {
  eyebrow?: string;
  title: string;
  subtitle: string;
  children: ReactNode;
  /** Linha abaixo do formulário (ex.: aviso de servidor acordando). */
  footer?: ReactNode;
  /** Aviso acima do formulário (ex.: sessão expirou). */
  notice?: ReactNode;
  corner?: ReactNode;
}) {
  const { t } = useTranslation();
  return (
    <AuthFrame
      brand={
        <AuthBrand label={t('signup.brand.label')}>
          <WeekAwaits />
        </AuthBrand>
      }
      corner={corner}
    >
      <AuthHeading eyebrow={eyebrow} title={title} subtitle={subtitle} />
      {notice}
      {children}
      {footer && <p className="text-center text-sm text-text-muted">{footer}</p>}
    </AuthFrame>
  );
}
