import type { Budget, CreateTrip, Item, ItemFields, Schedule, Trip, TripPage, User } from '../types';

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public traceId?: string) { super(message); }
}
let csrf: { headerName: string; token: string } | undefined;
async function csrfToken() {
  if (!csrf) {
    const response = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin', signal: AbortSignal.timeout(15000) });
    if (!response.ok) throw new ApiError(response.status, 'CSRF_UNAVAILABLE', '無法取得安全驗證，請稍後重試');
    csrf = await response.json() as { headerName: string; token: string };
  }
  return csrf!;
}
async function request<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (method !== 'GET') { const token = await csrfToken(); headers[token.headerName] = token.token; }
  let response: Response;
  try {
    response = await fetch(`/api/v1${path}`, { method, headers, credentials: 'same-origin', signal: AbortSignal.timeout(15000), body: body === undefined ? undefined : JSON.stringify(body) });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'TimeoutError') throw new ApiError(408, 'REQUEST_TIMEOUT', '連線逾時。請重新載入確認是否已儲存，避免重複新增。');
    throw new ApiError(0, 'OFFLINE', '連線中斷。請重新連線並重新載入，確認是否已儲存後再重試。');
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({ code: 'HTTP_ERROR', message: '請求失敗，請稍後重試' })) as { code: string; message: string; traceId?: string };
    if (response.status === 403) csrf = undefined;
    throw new ApiError(response.status, error.code, error.message, error.traceId);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
const tripPath = (id: string) => `/trips/${encodeURIComponent(id)}`;
export const api = {
  me: () => request<User>('/auth/me'),
  login: async (subject: string, displayName: string) => {
    const user = await request<User>('/auth/dev-login', 'POST', { subject, displayName }); csrf = undefined; return user;
  },
  logout: async () => { await request<void>('/auth/logout', 'POST'); csrf = undefined; },
  trips: (page = 0) => request<TripPage>(`/trips?page=${page}&size=20`),
  createTrip: (trip: CreateTrip) => request<Trip>('/trips', 'POST', trip),
  trip: (id: string) => request<Trip>(tripPath(id)),
  schedule: (id: string) => request<Schedule>(`${tripPath(id)}/schedule`),
  budget: (id: string) => request<Budget>(`${tripPath(id)}/budget`),
  members: (id: string) => request<User[]>(`${tripPath(id)}/members`),
  createItem: (trip: string, dayId: string, fields: ItemFields) => request<Item>(`${tripPath(trip)}/items`, 'POST', { dayId, ...fields }),
  updateItem: (trip: string, item: Item, fields: ItemFields) => request<Item>(`${tripPath(trip)}/items/${item.id}`, 'PATCH', { expectedVersion: item.version, ...fields }),
  deleteItem: (trip: string, item: Item) => request<void>(`${tripPath(trip)}/items/${item.id}?expectedVersion=${item.version}`, 'DELETE'),
};
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError && error.code === 'VERSION_CONFLICT') return '此行程已被其他成員修改。你的變更尚未儲存，請重新載入最新行程後再編輯。';
  if (error instanceof ApiError && error.status === 404) return '找不到旅程或行程，或你尚未加入這個旅程。';
  if (error instanceof ApiError && error.status === 401) return '登入已過期，請重新登入。';
  return error instanceof Error ? error.message : '發生錯誤，請重試';
}
