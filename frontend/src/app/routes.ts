import type { IconName } from '@/components/ui';

export interface NavItem {
  /** Caminho da rota. */
  to: string;
  /** Chave de tradução do nome. */
  labelKey: string;
  /** Último trecho do breadcrumb (`~/stackstudy / <slug>`). */
  slug: string;
  icon: IconName;
}

export const NAV_ITEMS: NavItem[] = [
  { to: '/', labelKey: 'nav.cockpit', slug: 'cockpit', icon: 'cockpit' },
  { to: '/stack', labelKey: 'nav.stack', slug: 'stack', icon: 'stack' },
  { to: '/deep-work', labelKey: 'nav.deepWork', slug: 'deep-work', icon: 'deepWork' },
  { to: '/flashcards', labelKey: 'nav.flashcards', slug: 'flashcards', icon: 'flashcards' },
  { to: '/grades', labelKey: 'nav.grades', slug: 'grades', icon: 'grades' },
];

/** Perfil: fora do menu principal (fica no bloco do usuário, na barra lateral). */
export const PROFILE_ITEM: NavItem = {
  to: '/profile',
  labelKey: 'nav.profile',
  slug: 'profile',
  icon: 'user',
};

export function findNavItem(pathname: string): NavItem | undefined {
  return [...NAV_ITEMS, PROFILE_ITEM].find((item) =>
    item.to === '/' ? pathname === '/' : pathname === item.to || pathname.startsWith(`${item.to}/`),
  );
}
