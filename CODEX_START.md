# TRIPMATE 開發入口

先讀 `AGENTS.md`、`docs/MVP_V1.md`、`docs/EXECUTION_PLAN.md`、`docs/ARCHITECTURE.md` 與 README。

以 2026-10-15 使用者提供的 `docs/MVP_V1.md` 為產品規格；它取代早期四階段與 WebSocket 規劃。一次完成一個階段並驗收。本輪只執行 Phase 1：前後端基礎、PostgreSQL、Flyway、可重現建置與測試。

已有的開發登入、旅程／活動／預算垂直切片應保留並做回歸驗證，不能因階段重編而移除。它們不是 Phase 2–6 已驗收的證明。開發登入只允許 exclusive `dev` profile，正式部署前必須完成 OIDC。

執行可用的測試、記錄實際結果、修正可重現失敗。只提交原始碼與範例設定，不提交 `.env`、快取、建置結果、資料庫或測試資料。不使用 WebSocket、Redis、Kafka、Kubernetes、微服務、CQRS 或 Event Sourcing。
