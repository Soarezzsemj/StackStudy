import { useId, type ReactNode } from 'react';
import { cn } from '@/lib/cn';

/*
 * Controles do cadastro, todos com rótulo, alvo de toque de 44 px e erro ligado ao campo por
 * aria-describedby.
 */

export function ChoiceGroup<T extends string>({
  label,
  options,
  value,
  onChange,
  error,
  hint,
  id,
}: {
  label: string;
  options: { value: T; label: string; description?: string }[];
  value: T | '';
  onChange: (value: T) => void;
  error?: string;
  hint?: string;
  id?: string;
}) {
  const auto = useId();
  const groupId = id ?? auto;
  const msgId = `${groupId}-msg`;
  return (
    <div className="flex flex-col gap-2">
      <span id={`${groupId}-label`} className="label text-text-secondary">
        {label}
      </span>
      <div
        id={groupId}
        role="radiogroup"
        aria-labelledby={`${groupId}-label`}
        aria-describedby={error || hint ? msgId : undefined}
        aria-invalid={error ? true : undefined}
        tabIndex={-1}
        className="flex flex-wrap gap-2"
      >
        {options.map((o) => (
          <button
            key={o.value}
            type="button"
            role="radio"
            aria-checked={value === o.value}
            onClick={() => onChange(o.value)}
            className={cn(
              'min-h-11 rounded-pill border px-4 py-2 text-left text-sm font-semibold transition-colors',
              value === o.value
                ? 'border-action bg-action text-action-ink'
                : 'border-border-control text-text-secondary hover:border-text-secondary hover:text-text',
            )}
          >
            {o.label}
            {o.description && (
              <span
                className={cn(
                  'block text-xs font-normal',
                  value === o.value ? '' : 'text-text-muted',
                )}
              >
                {o.description}
              </span>
            )}
          </button>
        ))}
      </div>
      {(error || hint) && (
        <p id={msgId} className={error ? 'text-sm text-danger' : 'text-sm text-text-muted'}>
          {error ?? hint}
        </p>
      )}
    </div>
  );
}

export function Switch({
  label,
  description,
  checked,
  onChange,
}: {
  label: string;
  description?: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}) {
  const id = useId();
  return (
    <div className="flex min-h-11 items-center justify-between gap-4">
      <div>
        <span id={`${id}-label`} className="font-semibold">
          {label}
        </span>
        {description && (
          <span id={`${id}-desc`} className="block text-xs text-text-muted">
            {description}
          </span>
        )}
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-labelledby={`${id}-label`}
        aria-describedby={description ? `${id}-desc` : undefined}
        onClick={() => onChange(!checked)}
        className={cn(
          'relative inline-flex h-11 w-[4.5rem] shrink-0 items-center rounded-pill border-2 transition-colors',
          checked ? 'border-action bg-action/20' : 'border-border-control bg-surface',
        )}
      >
        <span
          aria-hidden="true"
          className={cn(
            'absolute h-6 w-6 rounded-full transition-transform motion-reduce:transition-none',
            checked ? 'translate-x-9 bg-action' : 'translate-x-2 bg-text-muted',
          )}
        />
      </button>
    </div>
  );
}

export function Consent({
  id,
  checked,
  onChange,
  error,
  children,
}: {
  id: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  error?: string;
  children: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="flex min-h-11 cursor-pointer items-start gap-3 text-sm">
        <input
          id={id}
          type="checkbox"
          className="mt-0.5 h-5 w-5 shrink-0 accent-[var(--action)]"
          checked={checked}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${id}-error` : undefined}
          onChange={(e) => onChange(e.target.checked)}
        />
        <span>{children}</span>
      </label>
      {error && (
        <p id={`${id}-error`} className="pl-8 text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
