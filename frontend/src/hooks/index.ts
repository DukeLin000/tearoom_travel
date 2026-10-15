import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { QueryKey } from '@tanstack/react-query';
import { api } from '../api';

export function useResource<T>(key: QueryKey, load: () => Promise<T>) {
  const query = useQuery({ queryKey: key, queryFn: load });
  return {
    data: query.data,
    error: query.error ?? undefined,
    loading: query.isFetching,
    reload: () => { void query.refetch(); },
  };
}
export function useWorkspace(id: string) {
  return useResource(['workspace', id], async () => {
    const [trip, schedule, budget, members] = await Promise.all([api.trip(id), api.schedule(id), api.budget(id), api.members(id)]);
    return { trip, schedule, budget, members };
  });
}
export function useOnline() {
  const [online, setOnline] = useState(navigator.onLine);
  useEffect(() => {
    const update = () => setOnline(navigator.onLine);
    window.addEventListener('online', update); window.addEventListener('offline', update);
    return () => { window.removeEventListener('online', update); window.removeEventListener('offline', update); };
  }, []);
  return online;
}
