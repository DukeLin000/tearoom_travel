import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { BrowserRouter, Link, Navigate, Route, Routes } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from './api';
import { ErrorNotice } from './components';
import { useOnline } from './hooks';
import { Dashboard, Workspace } from './pages';
import type { User } from './types';

export default function App() {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<User>(); const [loading, setLoading] = useState(true); const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false); const online = useOnline();
  useEffect(() => {
    let disposed = false;
    void api.me().then(value => { if (!disposed) setUser(value); }, reason => { if (!disposed && !(reason instanceof ApiError && reason.status === 401)) setError(reason); })
      .finally(() => { if (!disposed) setLoading(false); });
    return () => { disposed = true; };
  }, []);
  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const data = new FormData(event.currentTarget); setBusy(true); setError(undefined);
    try { const signedIn = await api.login(String(data.get('subject')), String(data.get('displayName'))); queryClient.clear(); setUser(signedIn); } catch (reason) { setError(reason); } finally { setBusy(false); }
  }
  async function logout() {
    setBusy(true); setError(undefined);
    try { await api.logout(); queryClient.clear(); setUser(undefined); } catch (reason) { setError(reason); } finally { setBusy(false); }
  }
  return <BrowserRouter><header className="site-header"><Link className="brand" to="/trips"><span aria-hidden="true">↗</span> TRIPMATE</Link><span className="dev-badge">本機開發版</span>{user && <div className="header-user"><span>{user.displayName}</span><button className="secondary" disabled={busy} onClick={() => void logout()}>登出</button></div>}</header>
    {!online && <div className="warning" role="alert">目前離線。請重新連線後再儲存，離線變更不會自動送出。</div>}
    {error !== undefined && <div className="global-error"><ErrorNotice error={error}/></div>}
    {loading ? <main><p role="status">確認登入狀態中…</p></main> : user ? <Routes><Route path="/trips" element={<Dashboard/>}/><Route path="/trips/:id" element={<Workspace/>}/><Route path="*" element={<Navigate to="/trips" replace/>}/></Routes> :
      <main className="login-layout"><section><p className="eyebrow">GOOD PLANS. GREAT COMPANY.</p><h1>一起計畫，<br/>一起留下回憶。</h1><p>從每天的行程，到整趟旅行的預算，<br/>讓下一趟冒險井然有序。</p></section><section className="panel login-panel"><h2>開發模式登入</h2><p className="muted">僅用於本機測試；此版本不可公開部署。</p><form onSubmit={event => void login(event)}><label>測試使用者代號<input name="subject" required pattern="[a-zA-Z0-9_-]{1,40}" defaultValue="alice"/></label><label>顯示名稱<input name="displayName" required maxLength={80} defaultValue="旅人 Alice"/></label><button type="submit" disabled={busy || !online}>{busy ? '登入中…' : '開始規劃 →'}</button></form></section></main>}
    <footer>TRIPMATE · 一起把期待變成行程</footer></BrowserRouter>;
}
