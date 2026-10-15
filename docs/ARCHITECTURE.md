# Architecture

## Topology

React + TypeScript + Vite → REST `/api/v1` → Java 21 Spring Boot → PostgreSQL。
前後端可獨立建置與部署；開發時 Vite 5173 同源代理至後端 8080。Compose 預設僅啟動資料庫，`app` profile 可另外啟動本機完整應用。

## Frameworks and modules

- React Router 管路由，TanStack Query 管伺服器資料快取／focus／reconnect／重新取得。登入與登出清除跨使用者快取。
- dnd-kit 已固定依賴；排序與移動功能在 Phase 3 實作。
- Spring Web、Security、Bean Validation、Data JPA、Flyway。
- `identity` 使用 JPA entity/repository 與 PostgreSQL upsert 避免同身分重複；其餘既有服務保留 JdbcTemplate，避免重寫已驗證的鎖定與聚合 SQL。
- Spring JpaTransactionManager 與 JdbcTemplate 共用 datasource；稽核與寫入在同一 transaction，透過整合測試驗證回滾。
- `trip`、`membership`、`itinerary`、`budget`、`audit` 按業務劃分，`shared` 提供錯誤與金額工具。不建立空的 collaboration 或 WebSocket 模組。
- Flyway 是 schema 唯一來源；Hibernate `ddl-auto=validate`、`open-in-view=false`。

## Consistency

修改需 expectedVersion；活動 stale version 回 `409 VERSION_CONFLICT`。day row lock 保護既有新增／刪除排序，稽核失敗整筆回滾。排序／跨日移動的完整版本機制在 Phase 3 補齊。所有金額 PostgreSQL NUMERIC、Java BigDecimal，JSON 使用十進位字串。

V1 使用 REST + Polling，不使用 WebSocket。現在保留 focus／reconnect 與寫入後 refetch；Phase 6 加入 10 秒 polling 與草稿變更提示。表單以開啟時 snapshot 初始化，不自動用背景資料替換未儲存輸入。

## Security and deployment boundary

每個旅程端點驗證 membership；不存在與不可見皆 404。CSRF 開啟、HttpOnly session、無寬鬆 CORS。現有開發登入僅允許唯一 active profile `dev`；其餘情況啟動失敗。它不提供真實身分驗證，不能公開部署。Phase 2 實作 OIDC 後才驗收正式登入。

邀請表只接受 32-byte hash，schema 限制有效期間與使用計數；安全產生、兌換與 owner 驗證仍待 Phase 5。Secret 透過環境變數提供，禁止提交 `.env`。Compose 埠僅綁定 loopback。
