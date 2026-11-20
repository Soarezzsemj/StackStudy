export type StackShortcut = 'newTask' | 'editTask' | 'toggleDone' | 'next' | 'prev';

/** Marca o botão do título da tarefa: o único botão onde Espaço vira "marcar como feita". */
export const TASK_ROW_ATTR = 'data-task-row';

interface KeyInfo {
  key: string;
  ctrlKey: boolean;
  metaKey: boolean;
  altKey: boolean;
  target: EventTarget | null;
}

function isEditable(el: Element): boolean {
  const tag = el.tagName;
  return (
    tag === 'INPUT' ||
    tag === 'TEXTAREA' ||
    tag === 'SELECT' ||
    (el as HTMLElement).isContentEditable ||
    el.closest('[contenteditable="true"]') !== null
  );
}

/**
 * Atalhos da tela The Stack: N (nova), E (editar), Espaço (feita), setas (seleção).
 * Nunca disparam digitando em campos nem com Ctrl/Cmd/Alt. Espaço e setas só valem fora de
 * botões e links (para não brigar com o clique nativo), exceto na linha da tarefa.
 */
export function resolveShortcut(e: KeyInfo): StackShortcut | null {
  if (e.ctrlKey || e.metaKey || e.altKey) return null;
  const el = e.target instanceof Element ? e.target : null;
  if (el && isEditable(el)) return null;
  const onControl = el !== null && el.closest('button, a, [role="option"]') !== null;
  const onTaskRow = el !== null && el.closest(`[${TASK_ROW_ATTR}]`) !== null;

  switch (e.key) {
    case 'n':
    case 'N':
      return 'newTask';
    case 'e':
    case 'E':
      return 'editTask';
    case ' ':
      return !onControl || onTaskRow ? 'toggleDone' : null;
    case 'ArrowDown':
    case 'j':
      return !onControl || onTaskRow ? 'next' : null;
    case 'ArrowUp':
    case 'k':
      return !onControl || onTaskRow ? 'prev' : null;
    default:
      return null;
  }
}
