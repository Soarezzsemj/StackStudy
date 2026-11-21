import { useState } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router-dom';
import { createQueryClient } from '@/lib/queryClient';
import { useSessionBootstrap } from '@/features/auth/useSessionBootstrap';
import { ServerWakeNotice } from '@/components/shell/ServerWakeNotice';
import { OfflineNotice } from '@/components/shell/OfflineNotice';
import { createRouter } from './router';

export function App() {
  const [queryClient] = useState(createQueryClient);
  const [router] = useState(createRouter);
  useSessionBootstrap();

  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      {/* Fora do router: aparece também no login, onde o cold start costuma acontecer. */}
      <ServerWakeNotice />
      <OfflineNotice />
    </QueryClientProvider>
  );
}
