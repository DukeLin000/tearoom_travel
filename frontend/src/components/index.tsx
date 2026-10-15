import { useEffect, useRef } from 'react';
import type { ReactNode } from 'react';
import { errorMessage } from '../api';
import { categories, money } from '../format';
import type { Budget, Item } from '../types';

export function ErrorNotice({ error, retry }: { error: unknown; retry?: () => void }) {
  return <div className="error" role="alert"><p>{errorMessage(error)}</p>{retry && <button className="secondary" onClick={retry}>重新載入</button>}</div>;
}
export function Modal({ title, children, onClose, busy = false }: { title: string; children: ReactNode; onClose: () => void; busy?: boolean }) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => { ref.current?.showModal(); }, []);
  return <dialog ref={ref} aria-labelledby="dialog-title" onCancel={event => { event.preventDefault(); if (!busy) onClose(); }}>
    <div className="dialog-heading"><h2 id="dialog-title">{title}</h2><button aria-label="關閉" className="secondary" onClick={onClose} disabled={busy}>✕</button></div>{children}
  </dialog>;
}
export function ItemCard({ item, currency, onEdit }: { item: Item; currency: string; onEdit: () => void }) {
  return <article className="item-card"><div className="item-time">{item.localStartTime?.slice(0, 5) ?? '未定時間'}{item.localEndTime && `–${item.localEndTime.slice(0, 5)}`}</div>
    <div className="item-body"><span className="tag">{categories[item.category]}</span><h3>{item.title}</h3>{item.locationName && <p>{item.locationName}</p>}{item.note && <p className="note">{item.note}</p>}
      {item.mapUrl && <a href={item.mapUrl} target="_blank" rel="noopener noreferrer">查看地圖 ↗</a>}</div>
    <div className="item-actions"><strong>{money(item.estimatedAmount, currency)}</strong><button className="secondary" onClick={onEdit} aria-label={`編輯 ${item.title}`}>編輯</button></div>
  </article>;
}
export function BudgetPanel({ budget, currency }: { budget: Budget; currency: string }) {
  return <section aria-label="預算摘要"><div className="stat-grid">
    <div className="stat"><span>旅程預算上限</span><strong>{money(budget.budgetLimit, currency)}</strong></div>
    <div className="stat"><span>行程預估總額</span><strong>{money(budget.plannedTotal, currency)}</strong></div>
    <div className={`stat ${budget.remaining.startsWith('-') ? 'over' : ''}`}><span>{budget.remaining.startsWith('-') ? '超出預算（負值）' : '剩餘預算'}</span><strong>{money(budget.remaining, currency)}</strong></div>
  </div><div className="panel"><h2>分類預算</h2>{Object.entries(budget.breakdown).length === 0 ? <p>新增活動後，這裡會顯示預估花費。</p> : <dl className="breakdown">{Object.entries(budget.breakdown).map(([category, amount]) => <div key={category}><dt>{categories[category as keyof typeof categories]}</dt><dd>{money(amount!, currency)}</dd></div>)}</dl>}
    <p>每人預估 {money(budget.estimatedPerPerson, currency)}（{budget.memberCount} 位成員平均分攤，僅供參考）</p><p className="muted">金額為整趟旅程估計，依現有行程自動計算，不代表實際結算。</p></div></section>;
}
