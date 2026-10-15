import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { createQueryClient } from './api/queryClient';
import { describe, expect, it, vi } from 'vitest';
import { api, ApiError } from './api';
import { Dashboard } from './pages';
import { BudgetPanel } from './components';
import { ItemEditor, TripForm } from './forms';
import { money } from './format';
import type { Item } from './types';
import App from './App';

const item: Item = { id: 'item-1', dayId: 'day-1', title: '午餐', category: 'FOOD', estimatedAmount: '0.10',
  localStartTime: null, localEndTime: null, locationName: null, mapUrl: null, note: '', sortOrder: 0, version: 3, updatedAt: '', deletedAt: null };
describe('Phase 1 connected UI', () => {
  it('clears the previous user query cache when switching accounts', async () => {
    const client = createQueryClient();
    vi.spyOn(api, 'me').mockResolvedValue({ id: 'alice', displayName: 'Alice' });
    vi.spyOn(api, 'logout').mockResolvedValue(undefined);
    vi.spyOn(api, 'login').mockResolvedValue({ id: 'bob', displayName: 'Bob' });
    vi.spyOn(api, 'trips').mockResolvedValue({ items: [], page: 0, size: 20, total: 0 });
    render(<QueryClientProvider client={client}><App/></QueryClientProvider>);
    expect(await screen.findByText('Alice')).toBeVisible();
    client.setQueryData(['workspace', 'private-alice-trip'], { title: 'Alice private data' });
    await userEvent.click(screen.getByRole('button', { name: '登出' }));
    expect(await screen.findByRole('button', { name: '開始規劃 →' })).toBeVisible();
    expect(client.getQueryData(['workspace', 'private-alice-trip'])).toBeUndefined();
    await userEvent.click(screen.getByRole('button', { name: '開始規劃 →' }));
    expect(await screen.findByText('Bob')).toBeVisible();
    expect(client.getQueryData(['workspace', 'private-alice-trip'])).toBeUndefined();
  });
  it('shows dashboard loading and empty state from API', async () => {
    let resolve!: (value: Awaited<ReturnType<typeof api.trips>>) => void;
    vi.spyOn(api, 'trips').mockReturnValue(new Promise(done => { resolve = done; }));
    render(<QueryClientProvider client={createQueryClient()}><MemoryRouter><Dashboard/></MemoryRouter></QueryClientProvider>);
    expect(screen.getByRole('status')).toHaveTextContent('載入旅程中');
    resolve({ items: [], page: 0, size: 20, total: 0 });
    expect(await screen.findByRole('button', { name: '建立第一趟旅程' })).toBeVisible();
  });
  it('shows an API error and retries failed loading', async () => {
    vi.spyOn(api, 'trips').mockRejectedValueOnce(new ApiError(0, 'OFFLINE', '連線中斷')).mockResolvedValueOnce({ items: [], page: 0, size: 20, total: 0 });
    render(<QueryClientProvider client={createQueryClient()}><MemoryRouter><Dashboard/></MemoryRouter></QueryClientProvider>);
    expect(await screen.findByRole('alert')).toHaveTextContent('連線中斷');
    await userEvent.click(screen.getByRole('button', { name: '重新載入' }));
    expect(await screen.findByRole('button', { name: '建立第一趟旅程' })).toBeVisible();
    expect(api.trips).toHaveBeenCalledTimes(2);
  });
  it('submits trip fields as strings and prevents duplicate saves', async () => {
    const create = vi.fn(() => new Promise<void>(() => {}));
    render(<TripForm onCreate={create} onClose={vi.fn()}/>);
    await userEvent.type(screen.getByLabelText('旅程名稱'), '沖繩好友旅行');
    await userEvent.type(screen.getByLabelText('目的地'), '沖繩');
    await userEvent.type(screen.getByLabelText('開始日期'), '2027-03-12');
    await userEvent.type(screen.getByLabelText('結束日期'), '2027-03-14');
    await userEvent.click(screen.getByRole('button', { name: '建立旅程' }));
    expect(create).toHaveBeenCalledWith(expect.objectContaining({ budgetLimit: '0.00', startDate: '2027-03-12', currency: 'TWD' }));
    expect(screen.getByRole('button', { name: '建立中…' })).toBeDisabled();
  });
  it('preserves edits on a version conflict and offers an explicit refresh', async () => {
    const save = vi.fn().mockRejectedValue(new ApiError(409, 'VERSION_CONFLICT', 'Conflict'));
    const refresh = vi.fn();
    render(<ItemEditor item={item} onSave={save} onClose={vi.fn()} onRefresh={refresh}/>);
    const amount = screen.getByLabelText('活動預估金額（整體旅程）');
    await userEvent.clear(amount); await userEvent.type(amount, '2500.00');
    await userEvent.click(screen.getByRole('button', { name: '儲存行程' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('你的變更尚未儲存');
    expect(amount).toHaveValue('2500.00');
    expect(save).toHaveBeenCalledWith(expect.objectContaining({ estimatedAmount: '2500.00' }));
    expect(screen.getByRole('button', { name: '儲存行程' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: '重新載入最新行程' }));
    expect(refresh).toHaveBeenCalledTimes(1);
  });
  it('requires confirmation before deleting an item', async () => {
    const remove = vi.fn().mockResolvedValue(undefined);
    render(<ItemEditor item={item} onSave={vi.fn()} onDelete={remove} onClose={vi.fn()} onRefresh={vi.fn()}/>);
    await userEvent.click(screen.getByRole('button', { name: '刪除行程' }));
    expect(remove).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: '確認刪除' }));
    await waitFor(() => expect(remove).toHaveBeenCalledTimes(1));
  });
  it('labels the estimate and displays exact budget decimal strings', () => {
    render(<BudgetPanel currency="TWD" budget={{ budgetLimit: '100.00', plannedTotal: '0.30', remaining: '99.70', estimatedPerPerson: '0.15', memberCount: 2, breakdown: { FOOD: '0.30' } }}/>);
    expect(screen.getByText(/每人預估 TWD 0.15/)).toHaveTextContent('僅供參考');
    expect(screen.getByText('TWD 99.70')).toBeVisible();
    expect(money('999999999999.99', 'TWD')).toBe('TWD 999,999,999,999.99');
    expect(money('-2400.20', 'TWD')).toBe('TWD -2,400.20');
  });
});
