# TRIPMATE — Codex 開發指令（MVP V1）

你是一位資深 Full-Stack Software Engineer / System Architect。

請依照以下規格，實際開發一套「好友共同旅遊行程規劃網站 — TRIPMATE」。

**目標是建立可執行、可維護、可部署的網站，而不只是 Demo 或架構文件。**

## 一、系統架構

採用簡單的前後端分離架構。

### Frontend
- React + TypeScript + Vite
- React Router：頁面路由
- TanStack Query：API 資料管理
- dnd-kit：行程拖曳排序
- RWD：同時支援桌面與手機

### Backend
- Java 21
- Spring Boot + Maven
- Spring Web（REST API）
- Spring Security（登入與存取控制）
- Spring Data JPA
- Bean Validation
- Flyway（資料庫 Migration）

### Database
- PostgreSQL
- 使用 UUID 作為主要識別碼
- 金額使用 NUMERIC / BigDecimal
- 使用 Transaction 確保資料一致性

請選擇相容且仍受支援的穩定套件版本，確認依賴相容性，並記錄實際使用版本。

暫時不使用 WebSocket、Redis、Kafka、Kubernetes、Microservices、CQRS 或 Event Sourcing。

前後端必須能夠獨立開發、建置與部署。

## 二、專案結構

```text
TRIPMATE/
├── frontend/
│   ├── src/
│   │   ├── api/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── hooks/
│   │   ├── types/
│   │   └── App.tsx
│   ├── package.json
│   └── vite.config.ts
├── backend/
│   ├── src/main/java/
│   ├── src/main/resources/
│   ├── src/test/
│   └── pom.xml
├── docker-compose.yml
├── .env.example
└── README.md
```

若 Repository 已有程式碼，先分析現有實作，保留可用功能，不要無必要地重新建立專案。

## 三、網站功能

### 1. 使用者登入
- 提供安全的登入機制，優先使用標準 OIDC。
- 不允許在正式環境使用假登入或固定帳號。
- 未登入者不能進入旅程工作區。

### 2. 我的旅程
- 顯示目前參加的旅程。
- 可以建立新旅程。
- 每個旅程顯示名稱、目的地、日期、參加人數與預算。
- 點擊旅程進入詳細頁面。

### 3. 建立旅程
使用者可以輸入：
- 旅程名稱
- 目的地
- 開始日期
- 結束日期
- 預算上限
- 幣別（V1 預設 TWD）
- 時區

建立者自動加入旅程。

### 4. 多人共同編輯
- 所有已加入的成員都可以修改行程。
- 可以新增、編輯及軟刪除活動。
- 可以調整活動日期、時間、金額和順序。
- 成員不需要主辦人核准。
- 旅程刪除及成員管理保留給建立者。
- 後端必須驗證成員身分，不得僅依賴前端控制。

### 5. 行程時間軸
每筆活動包含：
- 活動名稱
- 所屬日期
- 開始與結束時間
- 活動類型
- 地點
- Google Maps 連結
- 預估費用
- 備註
- 排序位置

支援拖曳調整同一天的活動順序。

跨日移動可以透過變更日期操作完成。

拖曳排序不應自動改變活動時間；時間衝突以提示方式處理。

### 6. 預算管理
- 設定整趟旅程預算上限。
- 自動加總未刪除活動的預估費用。
- 顯示費用分類統計。
- 顯示目前預估總花費。
- 顯示預算剩餘金額。
- 顯示平均每人預估費用。
- 超出預算時提示，但不阻擋儲存。

所有金額計算以後端 BigDecimal 為準。

V1 不實作實際代墊、付款或完整分帳結算。

### 7. 成員與邀請
- 旅程建立者可以產生邀請連結。
- 使用者登入並接受邀請後成為旅程成員。
- 邀請 Token 必須安全產生、限制有效期限與使用次數。
- 資料庫只儲存 Token Hash。
- 可查看旅程成員列表。

### 8. 資料同步
第一版使用 REST API + Polling。

- 每 10 秒重新讀取旅程行程資料。
- 頁面重新取得焦點時同步最新資料。
- 成功修改後立即更新或重新取得對應資料。
- 編輯表單不能被背景 Polling 無提示覆蓋。
- 請合理控制 API 請求頻率。

暫不實作 WebSocket。

## 四、資料庫設計

至少建立以下資料表：

- app_user
- trip
- trip_member
- trip_day
- itinerary_item
- trip_invite
- trip_change_log

請建立 Flyway Migration，包含：
- Primary Key
- Foreign Key
- Unique Constraint
- Check Constraint
- 必要 Index
- Created / Updated Timestamp
- Version 欄位

行程採用 Soft Delete。

新增或編輯活動、調整排序、修改預算等操作必須具備明確 Transaction Boundary。

避免重複儲存可由活動金額推導的預算總額。

## 五、REST API

所有業務 API 使用 `/api/v1` 前綴。

至少實作：

```text
POST   /api/v1/trips
GET    /api/v1/trips
GET    /api/v1/trips/{tripId}
PATCH  /api/v1/trips/{tripId}

GET    /api/v1/trips/{tripId}/schedule
POST   /api/v1/trips/{tripId}/items
PATCH  /api/v1/trips/{tripId}/items/{itemId}
DELETE /api/v1/trips/{tripId}/items/{itemId}

PUT    /api/v1/trips/{tripId}/days/{dayId}/order
POST   /api/v1/trips/{tripId}/items/{itemId}/move

GET    /api/v1/trips/{tripId}/budget
GET    /api/v1/trips/{tripId}/members
GET    /api/v1/trips/{tripId}/history

POST   /api/v1/trips/{tripId}/invites
POST   /api/v1/invites/redeem
```

請定義一致的 Request DTO、Response DTO、Validation 與 Error Model。

HTTP Status：
- 400：輸入格式或欄位不正確
- 401：尚未登入
- 403：沒有操作權限
- 404：資源不存在或不可見
- 409：資料版本衝突
- 500：非預期伺服器錯誤

所有修改操作應支援版本檢查，避免多人編輯覆蓋其他人的資料。

對於新增操作與邀請兌換，需考慮重複請求及 Idempotency。

## 六、UI/UX 設計

採用現代化、簡潔的旅遊工具風格，優先考量手機操作便利性。

主要頁面：

1. Login：登入。
2. Dashboard：我的旅程。
3. Create Trip：建立旅程。
4. Trip Overview：旅程總覽。
5. Itinerary：每日行程時間軸。
6. Activity Editor：活動編輯表單。
7. Budget：旅程預算。
8. Members：成員與邀請。
9. History：修改紀錄。

UI 設計要求：
- Responsive Layout
- Loading / Empty / Error States
- 表單輸入驗證
- 成功與失敗訊息
- 行程拖曳與移動
- 預算摘要
- 手機觸控友善
- 日期、幣別及金額正確格式化
- 避免不必要的動畫與複雜視覺效果

請以真正可串接 API 的頁面為目標，不要只建立靜態 Mockup。

## 七、安全性與錯誤處理

必須處理：

- Authentication / Authorization
- Trip Membership Validation
- Input Validation
- 參數及 URL 安全性
- CORS / CSRF（依登入方式設定）
- Secret Management
- Concurrent Modification
- Transaction Rollback
- Duplicate Requests
- Database Connection Failure
- API Timeout
- Soft Delete
- Logging

正式環境不得包含硬編碼帳密或開發用認證捷徑。

## 八、開發環境

使用 Docker Compose 建立 PostgreSQL 開發環境。

前端與後端必須分別啟動。

前端開發服務預設使用 Port 5173。

後端 API 預設使用 Port 8080。

本機前端透過 Vite Proxy 將 `/api` 轉發到後端，以降低開發時的跨來源設定複雜度。

所有敏感設定使用環境變數，不提交 Secret 到 Git。

README 必須包含：
- Dependency Versions
- 環境需求
- 初始化方式
- 前端啟動指令
- 後端啟動指令
- Database Migration
- 測試指令
- 常見問題

## 九、測試與驗收

請至少建立並執行以下測試：

1. 建立旅程。
2. 查詢目前使用者的旅程。
3. 非成員無法讀取及修改私人旅程。
4. 成員可以新增及修改活動。
5. 活動金額不得為負。
6. 編輯時版本不一致返回 409。
7. 軟刪除後活動不再計入預算。
8. 拖曳排序後資料順序正確。
9. 重複提交不應意外建立重複資料。
10. 預算統計結果與活動資料一致。

在環境允許時執行：
- Frontend Build
- Backend Build
- Unit Tests
- Integration Tests
- API Smoke Tests

不得宣稱未執行的測試通過。

## 十、實作順序

依照以下順序開發：

Phase 1：Frontend / Backend Scaffold + PostgreSQL + Flyway

Phase 2：Authentication + Trip CRUD + Dashboard

Phase 3：Itinerary CRUD + Daily Schedule + Drag & Drop

Phase 4：Budget Calculation + Budget UI

Phase 5：Members + Invite + Change History

Phase 6：Polling Synchronization + Concurrency Handling + Testing

一次只完成一個可驗收階段，確保功能實際可執行後再進入下一階段。不要只停留在分析或產生規格。

## 十一、Codex 執行要求

1. 先檢查目前 Repository 的目錄與程式碼。
2. 對照本規格找出已完成及未完成項目。
3. 不破壞已有且正確的功能。
4. 建立必要的程式碼、設定、Migration 與測試。
5. 執行可用的 Build / Test。
6. 修正可重現的錯誤。
7. 每個階段完成後提供：
   - 修改檔案
   - 完成功能
   - 測試結果
   - 啟動方式
   - 已知限制
   - 下一階段工作

現在開始執行 Phase 1。

**請實際撰寫程式碼，不要只產出架構建議或待辦事項。**