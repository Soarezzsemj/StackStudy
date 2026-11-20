import { createBrowserRouter, type RouteObject } from 'react-router-dom';
import { AppShell } from '@/components/shell/AppShell';
import { RedirectIfAuthenticated, RequireAuth } from '@/features/auth/RequireAuth';
import { LoginPage } from '@/features/auth/LoginPage';
import { RegisterPage } from '@/features/auth/RegisterPage';
import { PlaceholderPage } from '@/pages/PlaceholderPage';
import { DesignPage } from '@/pages/DesignPage';
import { StackPage } from '@/features/stack/StackPage';
import { DeepWorkPage } from '@/features/deepwork/DeepWorkPage';
import { GradesPage } from '@/features/grades/GradesPage';
import { NotFoundPage } from '@/pages/NotFoundPage';

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
          { path: '/', element: <PlaceholderPage pageKey="cockpit" phase={5} /> },
          { path: '/stack', element: <StackPage /> },
          { path: '/deep-work', element: <DeepWorkPage /> },
          { path: '/flashcards', element: <PlaceholderPage pageKey="flashcards" phase={4} /> },
          { path: '/grades', element: <GradesPage /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
];

export const createRouter = () => createBrowserRouter(routes);
