import { useEffect } from 'react';
import type { Task } from '@/lib/api/stack';
import { useStackStore } from '@/stores/stackStore';
import { useUiStore } from '@/stores/uiStore';
import { useSetTaskStatus } from './queries';
import { resolveShortcut } from './shortcuts';

/** Foca o botão do título da tarefa (a seleção acompanha o foco). */
export function focusTask(id: string) {
  document.querySelector<HTMLElement>(`[data-task-id="${CSS.escape(id)}"]`)?.focus();
}

/**
 * Liga os atalhos da tela The Stack enquanto ela está montada: N, E, Espaço e setas.
 * `tasks` é a lista visível, na ordem em que aparece.
 */
export function useStackShortcuts(tasks: Task[]) {
  const dialogOpen = useStackStore((s) => s.dialog !== null);
  const paletteOpen = useUiStore((s) => s.paletteOpen);
  const { mutate: setStatus } = useSetTaskStatus();

  useEffect(() => {
    if (dialogOpen || paletteOpen) return;

    function onKeyDown(e: KeyboardEvent) {
      if (e.defaultPrevented || e.repeat) return;
      const action = resolveShortcut(e);
      if (!action) return;
      const { selectedTaskId, select, openDialog } = useStackStore.getState();
      const index = tasks.findIndex((t) => t.id === selectedTaskId);
      const selected = index >= 0 ? tasks[index] : undefined;

      if (action === 'newTask') {
        e.preventDefault();
        openDialog({ kind: 'task' });
      } else if (action === 'editTask' && selected) {
        e.preventDefault();
        openDialog({ kind: 'task', taskId: selected.id });
      } else if (action === 'toggleDone' && selected) {
        e.preventDefault();
        setStatus({ id: selected.id, status: selected.status === 'DONE' ? 'TODO' : 'DONE' });
      } else if ((action === 'next' || action === 'prev') && tasks.length > 0) {
        e.preventDefault();
        const step = action === 'next' ? 1 : -1;
        const nextIndex = index < 0 ? 0 : Math.min(tasks.length - 1, Math.max(0, index + step));
        const next = tasks[nextIndex];
        if (next) {
          select(next.id);
          focusTask(next.id);
        }
      }
    }

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [dialogOpen, paletteOpen, tasks, setStatus]);
}
