import { useEffect } from 'react';
import { Command } from 'cmdk';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ADMIN_ITEM, NAV_ITEMS, PROFILE_ITEM } from '@/app/routes';
import { useAuthStore } from '@/stores/authStore';
import { authApi } from '@/lib/api/auth';
import { Icon } from '@/components/ui';
import { useUiStore } from '@/stores/uiStore';
import { useStackStore, type StackDialog } from '@/stores/stackStore';
import type { IconName } from '@/components/ui';
import { useCurrentSubjects } from '@/features/stack/queries';
import { DEFAULT_SETTINGS } from '@/features/deepwork/pomodoro';
import {
  useActiveSession,
  useDeepWorkSettings,
  useSessionActions,
} from '@/features/deepwork/queries';
import { useDeepWorkStore } from '@/stores/deepWorkStore';
import { useFlashcardStore } from '@/stores/flashcardStore';

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

/** Atalhos para as seções do Perfil. */
const ACCOUNT_LINKS: { key: string; icon: IconName; to: string }[] = [
  { key: 'palette.preferences', icon: 'user', to: '/profile#preferencias' },
  { key: 'palette.sessions', icon: 'globe', to: '/profile#conta' },
  { key: 'palette.exportData', icon: 'archive', to: '/profile#privacidade' },
];

/** Paleta global (Ctrl/Cmd+K): ações rápidas, foco, conta e navegação entre todas as telas. */
export function CommandPalette() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const open = useUiStore((s) => s.paletteOpen);
  const setOpen = useUiStore((s) => s.setPaletteOpen);
  const toggle = useUiStore((s) => s.togglePalette);
  const openDialog = useStackStore((s) => s.openDialog);
  const openFlashcardDialog = useFlashcardStore((s) => s.openDialog);
  const { list: subjects } = useCurrentSubjects();
  const { data: active } = useActiveSession();
  const { data: settings = DEFAULT_SETTINGS } = useDeepWorkSettings();
  const { start } = useSessionActions();
  const isAdmin = useAuthStore((s) => s.user?.role === 'ADMIN');

  /** Inicia foco na matéria no modo escolhido na tela Deep Work e abre a tela. */
  function startFocus(subjectId: string) {
    const store = useDeepWorkStore.getState();
    store.setSubject(subjectId);
    store.setBreak(null);
    navigate('/deep-work');
    start.mutate({
      subjectId,
      mode: store.mode,
      plannedMinutes: store.mode === 'POMODORO' ? settings.focusMinutes : undefined,
    });
  }

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
          <Command.Item
            value={t('palette.newCard')}
            onSelect={() =>
              run(() => {
                // O diálogo vive na tela de Flashcards e na de um deck.
                const p = location.pathname;
                if (p !== '/flashcards' && !p.startsWith('/flashcards/decks/'))
                  navigate('/flashcards');
                openFlashcardDialog({ kind: 'card' });
              })
            }
            className={ITEM_CLASS}
          >
            <Icon name="flashcards" size={18} />
            <span className="flex-1">{t('palette.newCard')}</span>
          </Command.Item>
          <Command.Item
            value={t('palette.reviewCards')}
            onSelect={() => run(() => navigate('/flashcards/review'))}
            className={ITEM_CLASS}
          >
            <Icon name="flashcards" size={18} />
            <span className="flex-1">{t('palette.reviewCards')}</span>
          </Command.Item>
        </Command.Group>
        <Command.Group heading={t('palette.focus')} className={GROUP_CLASS}>
          {active ? (
            <Command.Item
              value={t('palette.goToSession')}
              onSelect={() => run(() => navigate('/deep-work'))}
              className={ITEM_CLASS}
            >
              <Icon name="deepWork" size={18} />
              <span className="flex-1">{t('palette.goToSession')}</span>
            </Command.Item>
          ) : (
            subjects.map((subject) => {
              const label = t('palette.startFocus', { subject: subject.name });
              return (
                <Command.Item
                  key={subject.id}
                  value={label}
                  onSelect={() => run(() => startFocus(subject.id))}
                  className={ITEM_CLASS}
                >
                  <span
                    aria-hidden="true"
                    className="ml-1 mr-1 h-2.5 w-2.5 rounded-full"
                    style={{ backgroundColor: subject.color }}
                  />
                  <span className="flex-1">{label}</span>
                </Command.Item>
              );
            })
          )}
        </Command.Group>
        <Command.Group heading={t('palette.account')} className={GROUP_CLASS}>
          {ACCOUNT_LINKS.map((link) => (
            <Command.Item
              key={link.key}
              value={t(link.key)}
              onSelect={() => run(() => navigate(link.to))}
              className={ITEM_CLASS}
            >
              <Icon name={link.icon} size={18} />
              <span className="flex-1">{t(link.key)}</span>
            </Command.Item>
          ))}
          <Command.Item
            value={t('palette.logout')}
            onSelect={() =>
              run(() => {
                void authApi
                  .logout()
                  .catch(() => undefined)
                  .then(() => navigate('/login', { replace: true }));
              })
            }
            className={ITEM_CLASS}
          >
            <Icon name="logout" size={18} />
            <span className="flex-1">{t('palette.logout')}</span>
          </Command.Item>
        </Command.Group>
        <Command.Group heading={t('palette.navigate')} className={GROUP_CLASS}>
          {[...NAV_ITEMS, PROFILE_ITEM, ...(isAdmin ? [ADMIN_ITEM] : [])].map((item) => {
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
