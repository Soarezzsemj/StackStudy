import { createBrowserRouter, Outlet, type RouteObject } from 'react-router-dom';
import { AppShell } from '@/components/shell/AppShell';
import { RedirectIfAuthenticated, RequireAuth } from '@/features/auth/RequireAuth';
import { LoginPage } from '@/features/auth/LoginPage';
import { RegisterPage } from '@/features/auth/RegisterPage';
import { RouteErrorPage } from '@/pages/RouteErrorPage';
import { PublicPage } from '@/pages/PublicPage';
import {
  AdminStatusPage,
  CockpitPage,
  DeckPage,
  DeepWorkPage,
  DesignPage,
  FlashcardsPage,
  GradesPage,
  LegalPage,
  NotFoundPage,
  ProfilePage,
  ReviewPage,
  StackPage,
} from './lazyPages';

export const routes: RouteObject[] = [
  {
    // Raiz só para a página de erro valer em todas as rotas (erro de render ou de chunk).
    element: <Outlet />,
    errorElement: <RouteErrorPage />,
    children: [
      {
        element: <RedirectIfAuthenticated />,
        children: [
          { path: '/login', element: <LoginPage /> },
          { path: '/register', element: <RegisterPage /> },
        ],
      },
      // Públicas, com ou sem login.
      {
        element: <PublicPage />,
        children: [
          { path: '/terms', element: <LegalPage kind="terms" /> },
          { path: '/privacy', element: <LegalPage kind="privacy" /> },
          // Rota interna para conferir o design system (sem login e sem dados).
          { path: '/_design', element: <DesignPage /> },
        ],
      },
      {
        element: <RequireAuth />,
        children: [
          {
            element: <AppShell />,
            children: [
              { path: '/', element: <CockpitPage /> },
              { path: '/stack', element: <StackPage /> },
              { path: '/deep-work', element: <DeepWorkPage /> },
              { path: '/flashcards', element: <FlashcardsPage /> },
              { path: '/flashcards/decks/:deckId', element: <DeckPage /> },
              { path: '/flashcards/review', element: <ReviewPage /> },
              { path: '/grades', element: <GradesPage /> },
              { path: '/profile', element: <ProfilePage /> },
              // A página confere o papel (403 sem piscar); o servidor confere de novo.
              { path: '/admin/status', element: <AdminStatusPage /> },
              { path: '*', element: <NotFoundPage /> },
            ],
          },
        ],
      },
    ],
  },
];

export const createRouter = () => createBrowserRouter(routes);
