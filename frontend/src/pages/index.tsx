import { useCallback, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api } from '../api';
import { BudgetPanel, ErrorNotice, ItemCard } from '../components';
import { TripForm, ItemEditor } from '../forms';
import { useResource, useWorkspace } from '../hooks';
import type { Item } from '../types';

export function Dashboard() {
  const [page, setPage] = useState(0);
  const load = useCallback(() => api.trips(page), [page]);
  const { data, error, loading, reload } = useResource(['trips', page], load);
  const [creating, setCreating] = useState(false); const navigate = useNavigate();
  return <main><div className="page-heading"><div><p className="eyebrow">下一站，一起出發</p><h1>我的旅程</h1><p className="muted">把每天的期待，安排在同一份行程裡。</p></div><button onClick={() => setCreating(true)}>＋ 建立新旅程</button></div>
    {loading && <p role="status">載入旅程中…</p>}{error !== undefined && <ErrorNotice error={error} retry={reload}/>}
    {!loading && data?.items.length === 0 && <section className="empty"><span aria-hidden="true">✈</span><h2>下一趟旅行，從這裡開始</h2><p>建立旅程，安排每天的活動與預算。</p><button onClick={() => setCreating(true)}>建立第一趟旅程</button></section>}
    <div className="trip-grid">{data?.items.map(trip => <Link className="trip-card" to={`/trips/${trip.id}`} key={trip.id}><div className="trip-cover"><span aria-hidden="true">↗</span><span>{trip.destination}</span></div><div className="trip-card-body"><h2>{trip.title}</h2><p>{trip.startDate} — {trip.endDate}</p><p className="muted">{trip.memberCount} 位成員 · {trip.currency} · {trip.timezone}</p><span className="text-link">打開旅程 →</span></div></Link>)}</div>
    {data && data.total > 20 && <nav className="pagination" aria-label="旅程分頁"><button disabled={page === 0 || loading} onClick={() => setPage(page - 1)}>上一頁</button><span>第 {page + 1} 頁</span><button disabled={(page + 1) * data.size >= data.total || loading} onClick={() => setPage(page + 1)}>下一頁</button></nav>}
    {creating && <TripForm onClose={() => setCreating(false)} onCreate={async input => { const trip = await api.createTrip(input); navigate(`/trips/${trip.id}`); }}/>}</main>;
}

export function Workspace() {
  const { id = '' } = useParams(); const { data, error, loading, reload } = useWorkspace(id);
  const [tab, setTab] = useState('schedule'); const [selectedDay, setSelectedDay] = useState<string>();
  const [editor, setEditor] = useState<{ dayId: string; item?: Item }>(); const [feedback, setFeedback] = useState('');
  if (!data) return <main><Link to="/trips">← 我的旅程</Link>{loading && <p role="status">載入行程中…</p>}{error !== undefined && <ErrorNotice error={error} retry={reload}/>}</main>;
  const { trip, schedule, budget, members } = data;
  const day = schedule.days.find(entry => entry.id === selectedDay) ?? schedule.days[0];
  const done = (message: string) => { setEditor(undefined); setFeedback(message); reload(); };
  const times = day.items.flatMap(item => item.localStartTime ? [item.localStartTime] : []);
  const timeWarning = times.some((time, index) => index > 0 && time < times[index - 1]);
  return <main><Link className="back-link" to="/trips">← 所有旅程</Link><div className="page-heading"><div><p className="eyebrow">{trip.destination}</p><h1>{trip.title}</h1><p className="muted">{trip.startDate} — {trip.endDate} · {trip.timezone} · {trip.currency}</p></div><span className="tag">{trip.memberCount} 位成員一起規劃</span></div>
    <nav className="workspace-tabs" aria-label="旅程內容">{[['schedule', '每日行程'], ['budget', '旅程預算'], ['members', '旅程成員']].map(([key, title]) => <button className={tab === key ? 'active' : ''} aria-pressed={tab === key} key={key} onClick={() => setTab(key)}>{title}</button>)}</nav>
    {feedback && <p role="status" className="success">{feedback}</p>}{loading && <p role="status">更新行程中…</p>}{error !== undefined && <ErrorNotice error={error} retry={reload}/>}
    {tab === 'schedule' && <section className="schedule-layout"><nav className="day-tabs" aria-label="選擇日期">{schedule.days.map((entry, index) => <button key={entry.id} className={day.id === entry.id ? 'active' : ''} aria-pressed={day.id === entry.id} onClick={() => setSelectedDay(entry.id)}><strong>DAY {index + 1}</strong><span>{entry.localDate.slice(5)}</span></button>)}</nav>
      <div className="day-content"><div className="section-heading"><div><h2>{day.localDate}</h2><p className="muted">{day.items.length} 項活動 · 時間以 {trip.timezone} 顯示</p></div><button onClick={() => setEditor({ dayId: day.id })}>＋ 新增行程</button></div>
        {timeWarning && <p className="warning">活動排列與開始時間順序不同；時間未自動調整。</p>}
        {day.items.length === 0 && <div className="empty small"><h3>這天還有空白，留給新的冒險</h3><p>新增第一個活動，也可以先不設定時間。</p></div>}
        {day.items.map(item => <ItemCard key={item.id} item={item} currency={trip.currency} onEdit={() => setEditor({ dayId: day.id, item })}/>)}
      </div></section>}
    {tab === 'budget' && <BudgetPanel budget={budget} currency={trip.currency}/>}
    {tab === 'members' && <section className="panel"><h2>一起旅行的夥伴</h2><ul className="members">{members.map(member => <li key={member.id}><span className="avatar" aria-hidden="true">{member.displayName.slice(0, 1)}</span><span>{member.displayName}</span><span className="tag">{trip.ownerUserId === member.id ? '旅程建立者 · 可編輯' : '可編輯'}</span></li>)}</ul><p className="muted">邀請連結尚未開放。</p></section>}
    {editor && <ItemEditor item={editor.item} onClose={() => setEditor(undefined)} onRefresh={() => { setEditor(undefined); reload(); }}
      onSave={async fields => { if (editor.item) await api.updateItem(id, editor.item, fields); else await api.createItem(id, editor.dayId, fields); done('行程已儲存，預算已更新。'); }}
      onDelete={async () => { await api.deleteItem(id, editor.item!); done('行程已刪除，不再計入預算。'); }}/>}</main>;
}
