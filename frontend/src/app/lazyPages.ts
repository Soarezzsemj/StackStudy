import { lazy } from 'react';

/*
 * Code splitting por rota: cada tela vira um arquivo .js próprio, baixado ao abrir a tela.
 * Login e cadastro ficam no pacote inicial (são a primeira coisa que aparece). Enquanto a tela
 * carrega, o AppShell mostra um esqueleto (Suspense em volta do Outlet).
 */
export const CockpitPage = lazy(() =>
  import('@/features/cockpit/CockpitPage').then((m) => ({ default: m.CockpitPage })),
);
export const StackPage = lazy(() =>
  import('@/features/stack/StackPage').then((m) => ({ default: m.StackPage })),
);
export const DeepWorkPage = lazy(() =>
  import('@/features/deepwork/DeepWorkPage').then((m) => ({ default: m.DeepWorkPage })),
);
export const FlashcardsPage = lazy(() =>
  import('@/features/flashcards/FlashcardsPage').then((m) => ({ default: m.FlashcardsPage })),
);
export const DeckPage = lazy(() =>
  import('@/features/flashcards/DeckPage').then((m) => ({ default: m.DeckPage })),
);
export const ReviewPage = lazy(() =>
  import('@/features/flashcards/ReviewPage').then((m) => ({ default: m.ReviewPage })),
);
export const GradesPage = lazy(() =>
  import('@/features/grades/GradesPage').then((m) => ({ default: m.GradesPage })),
);
export const ProfilePage = lazy(() =>
  import('@/features/profile/ProfilePage').then((m) => ({ default: m.ProfilePage })),
);
export const NotFoundPage = lazy(() =>
  import('@/pages/NotFoundPage').then((m) => ({ default: m.NotFoundPage })),
);
export const DesignPage = lazy(() =>
  import('@/pages/DesignPage').then((m) => ({ default: m.DesignPage })),
);
export const RoomsPage = lazy(() =>
  import('@/features/rooms/RoomsPage').then((m) => ({ default: m.RoomsPage })),
);
export const RoadmapsPage = lazy(() =>
  import('@/features/roadmaps/RoadmapsPage').then((m) => ({ default: m.RoadmapsPage })),
);
export const AdminStatusPage = lazy(() =>
  import('@/features/admin/AdminStatusPage').then((m) => ({ default: m.AdminStatusPage })),
);
export const LegalPage = lazy(() =>
  import('@/pages/LegalPage').then((m) => ({ default: m.LegalPage })),
);
