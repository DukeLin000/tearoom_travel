import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Item } from './types';

beforeEach(() => vi.resetModules());
describe('typed REST contract', () => {
  it('sends CSRF protection, decimal strings and the explicit expected version', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ version: 4, estimatedAmount: '2500.00' })));
    const { api } = await import('./api');
    const fields = { title: '午餐', category: 'FOOD' as const, estimatedAmount: '2500.00', localStartTime: null, localEndTime: null, locationName: null, mapUrl: null, note: '' };
    await api.updateItem('trip-id', { ...fields, id: 'item-id', version: 3 } as Item, fields);
    const [url, options] = fetchMock.mock.calls[1];
    expect(url).toBe('/api/v1/trips/trip-id/items/item-id');
    expect(options?.headers).toMatchObject({ 'X-CSRF-TOKEN': 'test-csrf', 'Content-Type': 'application/json' });
    expect(JSON.parse(options?.body as string)).toMatchObject({ expectedVersion: 3, estimatedAmount: '2500.00' });
    expect(options?.signal).toBeInstanceOf(AbortSignal);
  });
  it('preserves stable conflict errors without retrying the mutation', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' })))
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 'VERSION_CONFLICT', message: 'Conflict', traceId: 'trace-test' }), { status: 409 }));
    const { api } = await import('./api');
    await expect(api.deleteItem('trip-id', { id: 'item-id', version: 3 } as Item)).rejects.toMatchObject({ status: 409, code: 'VERSION_CONFLICT', traceId: 'trace-test' });
    expect(fetchMock.mock.calls[1][0]).toBe('/api/v1/trips/trip-id/items/item-id?expectedVersion=3');
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
  it('explains uncertain outcomes on timeout instead of claiming a failed write', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new DOMException('Timeout', 'TimeoutError'));
    const { api } = await import('./api');
    await expect(api.trips()).rejects.toMatchObject({ code: 'REQUEST_TIMEOUT', message: expect.stringContaining('確認是否已儲存') });
  });
});
