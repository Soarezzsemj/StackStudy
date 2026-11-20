// Ajudantes dos atalhos de teclado globais.

/** O usuário está digitando (campo, área de texto, select ou contenteditable)? */
export function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof Element)) return false;
  const tag = target.tagName;
  return (
    tag === 'INPUT' ||
    tag === 'TEXTAREA' ||
    tag === 'SELECT' ||
    (target as HTMLElement).isContentEditable ||
    target.closest('[contenteditable="true"]') !== null
  );
}

/** Foco num controle que já reage a Espaço/Enter sozinho (botão, link, opção)? */
export function isControlTarget(target: EventTarget | null): boolean {
  return target instanceof Element && target.closest('button, a, [role="option"]') !== null;
}

export function hasModifier(e: { ctrlKey: boolean; metaKey: boolean; altKey: boolean }): boolean {
  return e.ctrlKey || e.metaKey || e.altKey;
}
