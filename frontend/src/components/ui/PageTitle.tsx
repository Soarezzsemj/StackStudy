import type { ReactNode } from 'react';

export interface PageTitleProps {
  title: ReactNode;
  eyebrow?: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
}

export function PageTitle({ title, eyebrow, description, actions }: PageTitleProps) {
  return (
    <header className="mb-8 flex flex-wrap items-end justify-between gap-6">
      <div className="min-w-0">
        {eyebrow && <p className="label mb-3 text-text-muted">{eyebrow}</p>}
        <h1 className="page-title break-words">{title}</h1>
        {description && <p className="mt-4 max-w-2xl text-text-secondary">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-3">{actions}</div>}
    </header>
  );
}
