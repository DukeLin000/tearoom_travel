# TRIPMATE 執行順序

依 `MVP_V1.md`（2026-10-15）執行；舊四階段安排已被取代。每階段須實際可執行、完成適當測試，再進入下一階段。

## Phase 1 — Frontend / Backend Scaffold + PostgreSQL + Flyway

- React + TypeScript + Vite、React Router、TanStack Query provider／資料存取、dnd-kit 固定依賴。
- Java 21 + Spring Boot + Maven wrapper、Spring Web、Security、Data JPA、Validation。
- PostgreSQL Compose；前後端可獨立啟動與建置，Vite 5173 代理 `/api` 到 8080。
- 七張核心資料表、UUID、NUMERIC、外鍵／唯一／檢查約束、索引、時間戳與版本；Flyway 為唯一 schema 管理者。
- 新資料庫與 V1 升級驗證；README、CI、範例環境變數與誠實驗收報告。
- 保留既有旅程、行程與預算功能並執行回歸測試；不將其視為後續階段完整完成。

本輪停在這裡，結果見 `PHASE1_REPORT.md`。

## Phase 2 — Authentication + Trip CRUD + Dashboard

- 標準 OIDC、正式 session/cookie/CSRF 設定與部署環境隔離；不接受正式環境假登入。
- 補齊旅程部分更新、建立者刪除、預算上限修改版本檢查與稽核。
- 審查既有旅程建立／列表／詳情、Dashboard；補齊建立冪等性與驗證。

## Phase 3 — Itinerary CRUD + Daily Schedule + Drag & Drop

- 審查既有活動新增／編輯／軟刪除，加入新增冪等性。
- dnd-kit 拖曳與鍵盤操作、精確完整集合排序、day version 與原子交易。
- 跨日移動依固定順序鎖定兩天，驗證版本並維持排序。
- 時間衝突只提示，不因拖曳改變時間。

## Phase 4 — Budget Calculation + Budget UI

- 審查既有 BigDecimal 預算加總、分類統計、剩餘／超支與每人估計。
- 預算只由未刪除活動推導；補齊預算修改與顯示互動。

## Phase 5 — Members + Invite + Change History

- 產生安全亂數 token、只存 SHA-256 hash、到期／撤銷／限次與原子兌換。
- 重複兌換與併發安全、owner 管理權限、成員頁與分頁稽核歷史。
- Phase 1 僅建立邀請 schema，沒有邀請 API。

## Phase 6 — Polling Synchronization + Concurrency Handling + Testing

- 工作區每 10 秒 polling，focus／reconnect 與寫入後同步；控制頻率。
- 表單保留本機草稿，背景資料變更提示，409 不自動覆寫或重試。
- 完整十項 MVP 驗收、雙使用者競爭與部署檢查。

V1 不使用 WebSocket。分帳、實際付款、AI、訂房與地圖路線最佳化不在此 MVP。
