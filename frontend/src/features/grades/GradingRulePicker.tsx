import { useId } from 'react';
import { useTranslation } from 'react-i18next';
import { GRADE_METHODS, type GradeMethod } from '@/lib/api/grades';
import { cn } from '@/lib/cn';

interface GradingRulePickerProps {
  value: GradeMethod | '';
  onChange: (value: GradeMethod | '') => void;
  /** Com texto, mostra a opção "usar a preferência" (valor ''). */
  inheritLabel?: string;
  focusFirst?: boolean;
}

/**
 * "Como a sua faculdade calcula a nota?": três regras, cada uma com uma frase curta. Grupo de
 * rádios nativos (setas do teclado e leitor de tela funcionam sem nada extra).
 */
export function GradingRulePicker({
  value,
  onChange,
  inheritLabel,
  focusFirst,
}: GradingRulePickerProps) {
  const { t } = useTranslation();
  const name = useId();
  const options: { value: GradeMethod | ''; label: string; description?: string }[] = [
    ...GRADE_METHODS.map((m) => ({
      value: m,
      label: t(`grades.method.${m}`),
      description: t(`grades.methodHint.${m}`),
    })),
    ...(inheritLabel !== undefined ? [{ value: '' as const, label: inheritLabel }] : []),
  ];

  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="field-label mb-2 block">{t('grades.methodQuestion')}</legend>
      {options.map((o, i) => {
        const checked = value === o.value;
        const descId = o.description ? `${name}-${i}-desc` : undefined;
        // Nome acessível = só o título; a frase explicativa vai como descrição.
        const labelId = `${name}-${i}-label`;
        return (
          <label
            key={o.value || 'inherit'}
            className={cn(
              'card-inner flex min-h-11 cursor-pointer items-start gap-3 px-4 py-3 text-sm',
              checked && 'border-action/60',
            )}
          >
            <input
              type="radio"
              name={name}
              value={o.value}
              checked={checked}
              aria-labelledby={labelId}
              aria-describedby={descId}
              data-autofocus={focusFirst && checked ? true : undefined}
              className="mt-0.5 h-5 w-5 shrink-0 accent-[var(--action)]"
              onChange={() => onChange(o.value)}
            />
            <span className="flex flex-col gap-0.5">
              <span id={labelId} className="font-semibold text-text">
                {o.label}
              </span>
              {o.description && (
                <span id={descId} className="text-text-secondary">
                  {o.description}
                </span>
              )}
            </span>
          </label>
        );
      })}
    </fieldset>
  );
}
