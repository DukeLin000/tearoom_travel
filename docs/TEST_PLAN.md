# Test and acceptance plan

## Phase 1 gates

- JDK 21 Maven wrapper 編譯、打包，前端 frozen install、typecheck、lint、test、build。
- 真實 PostgreSQL Testcontainers 初始化 Flyway；V1 → V2 保留 user/trip/audit 資料。
- 七張表的 PK、timestamp/version 與邀請 hash／有效期／使用次數／會員 FK 約束。
- Spring Data JPA 身分 upsert；JPA 與 JDBC 共用交易，audit 失敗 rollback。
- 既有功能回歸：旅程建立與 caller-only 列表、非成員 404、活動新增／修改／非負金額、409 競爭、軟刪除、精確預算。
- TanStack Query 登出清除跨使用者快取；UI 載入／空白／錯誤／衝突狀態。
- Compose config、映像建置、readiness 與匿名 API 401 smoke checks。

## 後續 MVP 驗收

1. 建立旅程與自動 membership。
2. 僅列出目前使用者旅程。
3. 非成員不能讀寫。
4. 所有成員可新增修改活動。
5. 金額不得負。
6. stale version → 409。
7. soft delete 排除預算。
8. 完整集合拖曳排序、day version、跨日鎖定與並行測試（Phase 3）。
9. 建立／邀請兌換冪等性與重複請求（Phase 2/3/5）。
10. 預算統計與 live 活動一致（Phase 4）。

Phase 6 再驗證 10 秒 polling、focus／reconnect、雙瀏覽器衝突、未儲存草稿不被背景資料覆蓋。沒有 WebSocket 測試或實作。

精確指令見 README；只將當次真正執行成功的檢查列入 `PHASE1_REPORT.md`。Windows wrapper 與 GitHub Actions 需在對應環境執行，不可由本機結果推論通過。
