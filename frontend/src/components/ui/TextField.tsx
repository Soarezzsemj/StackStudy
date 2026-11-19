import { forwardRef, useId, type InputHTMLAttributes } from 'react';

export interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  hint?: string;
  error?: string;
}

export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, hint, error, id, ...rest },
  ref,
) {
  const autoId = useId();
  const inputId = id ?? autoId;
  const hintId = `${inputId}-hint`;
  const describedBy = error || hint ? hintId : undefined;
  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={inputId} className="label text-text-secondary">
        {label}
      </label>
      <input
        ref={ref}
        id={inputId}
        className="input"
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        {...rest}
      />
      {(error || hint) && (
        <p id={hintId} className={error ? 'text-sm text-danger' : 'text-sm text-text-muted'}>
          {error ?? hint}
        </p>
      )}
    </div>
  );
});
