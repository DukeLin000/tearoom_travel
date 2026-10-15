# TRIPMATE product specification

權威產品規格為使用者上傳的 `MVP_V1.md`。本檔摘要主要互動；階段順序見 `EXECUTION_PLAN.md`，實際已完成範圍見 `PHASE1_REPORT.md`。

好友在私人旅程內共同規劃每日活動、日期／時間／地點與預算；所有成員可直接編輯，建立者保留刪除旅程及成員管理。標準 OIDC 登入後方可加入工作區。邀請需登入兌換、有期限與限次，資料庫只存 hash。

頁面：Login、Dashboard、Create Trip、Trip Overview、Itinerary、Activity Editor、Budget、Members、History。提供手機與桌面 RWD、空白／載入／錯誤／衝突狀態、表單驗證、觸控與鍵盤操作。

拖曳只更動排序；跨日移動可改日期，不自動調整時間。金額是活動對整趟旅程的估計；預算來自 live 活動加總，超支提示但不阻擋儲存。每人金額是平均估計，沒有實際付款、代墊或分帳。

V1 以 REST + 每 10 秒 polling，同時在 focus 與寫入後同步；不能覆蓋未儲存表單。沒有 WebSocket、離線佇列、AI、訂房、投票、地圖路線最佳化或多幣別換匯。

目前既有切片保留 1–14 天旅程及單日活動限制，這些限制與所有未完成功能必須在 README 清楚列出。Phase 1 開發登入沒有真實認證，不得對外發布。
