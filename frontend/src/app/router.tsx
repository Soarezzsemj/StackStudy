import { createBrowserRouter, type RouteObject } from 'react-router-dom';
import { AppShell } from '@/components/shell/AppShell';
import { RedirectIfAuthenticated, RequireAuth } from '@/features/auth/RequireAuth';
import { LoginPage } from '@/features/auth/LoginPage';
import { RegisterPage } from '@/features/auth/RegisterPage';
import { DesignPage } from '@/pages/DesignPage';
import { StackPage } from '@/features/stack/StackPage';
import { DeepWorkPage } from '@/features/deepwork/DeepWorkPage';
import { GradesPage } from '@/features/grades/GradesPage';
import { FlashcardsPage } from '@/features/flashcards/FlashcardsPage';
import { DeckPage } from '@/features/flashcards/DeckPage';
import { ReviewPage } from '@/features/flashcards/ReviewPage';
import { NotFoundPage } from '@/pages/NotFoundPage';
import { ProfilePage } from '@/features/profile/ProfilePage';
import { CockpitPage } from '@/features/cockpit/CockpitPage';

export const routes: RouteObject[] = [
  {
    element: <RedirectIfAuthenticated />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/register', element: <RegisterPage /> },
    ],
  },
  // Rota interna para conferir o design system (sem login e sem dados).
  { path: '/_design', element: <DesignPage /> },
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
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
];

export const createRouter = () => createBrowserRouter(routes);
