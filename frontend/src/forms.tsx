import { useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from './api';
import { ErrorNotice, Modal } from './components';
import { categories } from './format';
import type { Category, CreateTrip, Item, ItemFields } from './types';

export function TripForm({ onCreate, onClose }: { onCreate: (input: CreateTrip) => Promise<void>; onClose: () => void }) {
  const [busy, setBusy] = useState(false); const [error, setError] = useState<unknown>();
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const data = new FormData(event.currentTarget);
    const input = Object.fromEntries(data.entries()) as CreateTrip;
    setBusy(true); setError(undefined);
    try { await onCreate(input); } catch (reason) { setError(reason); setBusy(false); }
  }
  return <Modal title="建立新旅程" onClose={onClose} busy={busy}><form onSubmit={event => void submit(event)}>
    {error !== undefined && <ErrorNotice error={error}/>}
    <label>旅程名稱<input name="title" required maxLength={120} autoFocus placeholder="沖繩好友旅行"/></label>
    <label>目的地<input name="destination" required maxLength={120} placeholder="沖繩"/></label>
    <div className="form-row"><label>開始日期<input name="startDate" type="date" required/></label><label>結束日期<input name="endDate" type="date" required/></label></div>
    <p className="muted">每趟旅程為 1 至 14 天，起訖日都包含在內。</p>
    <label>時區<input name="timezone" required maxLength={64} defaultValue="Asia/Taipei"/></label>
    <div className="form-row"><label>幣別<input name="currency" required pattern="[A-Z]{3}" maxLength={3} defaultValue="TWD"/></label><label>預算上限<input name="budgetLimit" inputMode="decimal" required pattern="(?:0|[1-9][0-9]{0,11})(?:\.[0-9]{1,2})?" defaultValue="0.00"/></label></div>
    <div className="form-actions"><button type="button" className="secondary" disabled={busy} onClick={onClose}>取消</button><button disabled={busy} type="submit">{busy ? '建立中…' : '建立旅程'}</button></div>
  </form></Modal>;
}

export function ItemEditor({ item, onSave, onDelete, onClose, onRefresh }: {
  item?: Item; onSave: (fields: ItemFields) => Promise<void>; onDelete?: () => Promise<void>;
  onClose: () => void; onRefresh: () => void;
}) {
  const [busy, setBusy] = useState(false); const [error, setError] = useState<unknown>(); const [confirmDelete, setConfirmDelete] = useState(false);
  async function execute(action: () => Promise<void>) {
    setBusy(true); setError(undefined);
    try { await action(); } catch (reason) { setError(reason); setBusy(false); }
  }
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const data = new FormData(event.currentTarget);
    const text = (field: string) => String(data.get(field) ?? '');
    const nullable = (field: string) => text(field) || null;
    const fields: ItemFields = { title: text('title'), category: text('category') as Category, localStartTime: nullable('localStartTime'), localEndTime: nullable('localEndTime'),
      locationName: nullable('locationName'), mapUrl: nullable('mapUrl'), estimatedAmount: text('estimatedAmount'), note: text('note') };
    void execute(() => onSave(fields));
  }
  const conflict = error instanceof ApiError && error.code === 'VERSION_CONFLICT';
  return <Modal title={item ? '編輯行程' : '新增行程'} onClose={onClose} busy={busy}><form onSubmit={submit}>
    {error !== undefined && <ErrorNotice error={error}/>}
    {conflict && <button type="button" className="secondary" onClick={onRefresh}>重新載入最新行程</button>}
    <label>活動名稱<input name="title" required maxLength={160} defaultValue={item?.title ?? ''} autoFocus/></label>
    <label>分類<select name="category" defaultValue={item?.category ?? 'ACTIVITY'}>{Object.entries(categories).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label>
    <div className="form-row"><label>開始時間<input name="localStartTime" type="time" defaultValue={item?.localStartTime?.slice(0, 5) ?? ''}/></label><label>結束時間<input name="localEndTime" type="time" defaultValue={item?.localEndTime?.slice(0, 5) ?? ''}/></label></div>
    <label>地點<input name="locationName" maxLength={255} defaultValue={item?.locationName ?? ''}/></label>
    <label>地圖網址<input name="mapUrl" type="url" maxLength={2048} defaultValue={item?.mapUrl ?? ''} placeholder="https://…"/></label>
    <label>活動預估金額（整體旅程）<input name="estimatedAmount" inputMode="decimal" pattern="(?:0|[1-9][0-9]{0,11})(?:\.[0-9]{1,2})?" required defaultValue={item?.estimatedAmount ?? '0.00'}/></label>
    <label>備註<textarea name="note" rows={3} maxLength={10000} defaultValue={item?.note ?? ''}/></label>
    {confirmDelete && <div className="error"><p>刪除後不再計入行程與預算，確定刪除？</p><button type="button" className="danger" disabled={busy} onClick={() => void execute(onDelete!)}>確認刪除</button><button type="button" className="secondary" disabled={busy} onClick={() => setConfirmDelete(false)}>保留行程</button></div>}
    <div className="form-actions">{item && <button type="button" className="danger" disabled={busy || conflict} onClick={() => setConfirmDelete(true)}>刪除行程</button>}
      <button type="button" className="secondary" disabled={busy} onClick={onClose}>取消</button><button type="submit" disabled={busy || conflict}>{busy ? '儲存中…' : '儲存行程'}</button></div>
  </form></Modal>;
}
