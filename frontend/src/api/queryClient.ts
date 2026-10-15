import { QueryClient } from '@tanstack/react-query';

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        retry: false,
        refetchOnWindowFocus: 'always',
        refetchOnReconnect: 'always',
      },
      mutations: { retry: false },
    },
  });
}
