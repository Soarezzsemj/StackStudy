import { useEffect } from 'react';
import { Command } from 'cmdk';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { NAV_ITEMS } from '@/app/routes';
import { Icon } from '@/components/ui';
import { useUiStore } from '@/stores/uiStore';
import { useStackStore, type StackDialog } from '@/stores/stackStore';
import type { IconName } from '@/components/ui';

const ITEM_CLASS =
  'flex min-h-11 cursor-pointer items-center gap-3 rounded-inner px-3 text-sm text-text-secondary data-[selected=true]:bg-surface-raised data-[selected=true]:text-text';
const GROUP_CLASS =
  '[&_[cmdk-group-heading]]:label [&_[cmdk-group-heading]]:px-3 [&_[cmdk-group-heading]]:py-2 [&_[cmdk-group-heading]]:text-text-muted';

/** Ações que abrem um diálogo do The Stack (a tela é aberta antes, se preciso). */
const ACTIONS: { key: string; icon: IconName; dialog: StackDialog }[] = [
  { key: 'palette.newTask', icon: 'plus', dialog: { kind: 'task' } },
  { key: 'palette.newSubject', icon: 'stack', dialog: { kind: 'subject' } },
  { key: 'palette.newSemester', icon: 'calendar', dialog: { kind: 'semesterForm' } },
];

/** Paleta global (Ctrl/Cmd+K): ações rápidas e navegação. */
export function CommandPalette() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const open = useUiStore((s) => s.paletteOpen);
  const setOpen = useUiStore((s) => s.setPaletteOpen);
  const toggle = useUiStore((s) => s.togglePalette);
  const openDialog = useStackStore((s) => s.openDialog);

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if (e.key.toLowerCase() === 'k' && (e.ctrlKey || e.metaKey)) {
        e.preventDefault();
        toggle();
      }
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [toggle]);

  function run(action: () => void) {
    setOpen(false);
    action();
  }

  return (
    <Command.Dialog
      open={open}
      onOpenChange={setOpen}
      label={t('palette.label')}
      overlayClassName="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm"
      contentClassName="fixed left-1/2 top-[14vh] z-50 w-[min(640px,calc(100vw-2rem))] -translate-x-1/2"
      className="card card-hero overflow-hidden shadow-2xl"
    >
      <div className="flex items-center gap-3 border-b border-border px-5">
        <Icon name="search" size={18} className="text-text-muted" />
        <Command.Input
          placeholder={t('palette.placeholder')}
          className="h-14 flex-1 bg-transparent text-base text-text outline-none placeholder:text-text-muted"
        />
      </div>
      <Command.List className="max-h-[50vh] overflow-y-auto p-2">
        <Command.Empty className="px-4 py-6 text-sm text-text-muted">
          {t('palette.empty')}
        </Command.Empty>
        <Command.Group heading={t('palette.actions')} className={GROUP_CLASS}>
          {ACTIONS.map((action) => (
            <Command.Item
              key={action.key}
              value={t(action.key)}
              onSelect={() =>
                run(() => {
                  navigate('/stack');
                  openDialog(action.dialog);
                })
              }
              className={ITEM_CLASS}
            >
              <Icon name={action.icon} size={18} />
              <span className="flex-1">{t(action.key)}</span>
            </Command.Item>
          ))}
        </Command.Group>
        <Command.Group heading={t('palette.navigate')} className={GROUP_CLASS}>
          {NAV_ITEMS.map((item) => {
            const label = t('palette.goTo', { page: t(item.labelKey) });
            return (
              <Command.Item
                key={item.to}
                value={label}
                onSelect={() => run(() => navigate(item.to))}
                className={ITEM_CLASS}
              >
                <Icon name={item.icon} size={18} />
                <span className="flex-1">{label}</span>
                <Icon name="arrowRight" size={16} className="text-text-muted" />
              </Command.Item>
            );
          })}
        </Command.Group>
      </Command.List>
    </Command.Dialog>
  );
}
