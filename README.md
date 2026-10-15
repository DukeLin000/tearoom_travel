# TRIPMATE — Phase 1

好友共同旅遊行程規劃網站。本輪依使用者提供的 [MVP V1](docs/MVP_V1.md)，完成 **Frontend / Backend Scaffold + PostgreSQL + Flyway**，並保留工作目錄原有的旅程／活動／預算切片。各階段順序見 [執行計畫](docs/EXECUTION_PLAN.md)，實際驗證與已知限制見 [Phase 1 報告](docs/PHASE1_REPORT.md)。

目前只供本機開發。既有開發登入沒有真實身分驗證；正式 OIDC 在 Phase 2 完成，不可將此版本公開部署。

## Dependency versions

| 工具 | 固定版本／要求 |
| --- | --- |
| Java | JDK 21；Maven 驗證使用 Temurin 21.0.9 |
| Spring Boot | 3.5.11；Web、Security、Data JPA、Validation、Actuator |
| Maven | wrapper 3.3.4、Maven 3.9.11，固定 distribution SHA-256 |
| PostgreSQL | 17-alpine，整合測試實際為 17.11 |
| Flyway／Hibernate／PostgreSQL JDBC | 11.7.2／6.6.42.Final／42.7.10，由 Spring Boot BOM 管理 |
| Node.js／npm | Node 24（`.node-version`）；驗證為 24.19.0／11.9.0 |
| React／React Router | 19.3.0／7.18.4 |
| TanStack Query | 5.104.2 |
| dnd-kit core／sortable／utilities | 6.3.1／10.0.0／3.2.2；拖曳功能留待 Phase 3 |
| TypeScript／Vite | 5.9.3／8.3.4 |
| ESLint／Vitest／Playwright | 10.12.0／5.0.4／1.64.0 |
| Docker | Docker Engine + Compose v2 + BuildKit；驗證為 28.4.0／2.40.3 |

前端完整依賴與 integrity 固定在 `frontend/package-lock.json`。後端 transitive versions 由 Boot BOM 統一管理。保留既有相容版本，沒有為本輪基礎補齊進行整套框架升級。

## Project layout

```text
frontend/src/
  api/          typed REST client、QueryClient
  components/   顯示元件、modal、budget
  pages/        Dashboard、Workspace
  hooks/        TanStack Query 讀取與網路狀態
  types/        API 型別
  App.tsx       路由與登入狀態
backend/
  src/main/java/com/tripmate/
    identity/ trip/ membership/ itinerary/ budget/ audit/ shared/
  src/main/resources/db/migration/
  src/test/
docker-compose.yml
.env.example
scripts/
docs/
```

## 環境需求與初始化

需要 JDK 21、Node.js 24、Docker Engine／Compose v2；Maven wrapper ZIP 需要 `unzip`。先確認 `JAVA_HOME` 指向 JDK 21（`java -version` 正確不代表 `JAVA_HOME/bin/javac` 也正確）。

```bash
# 根目錄；僅在 .env 不存在時複製
cp -n .env.example .env
# 編輯 .env，設定唯一的本機 DB_PASSWORD
# 不提交 .env，不使用正式環境憑證
docker compose up -d --wait db
```

Compose 預設僅啟動 PostgreSQL，埠為 `127.0.0.1:5432`；資料持久化於 named volume。可用 `DB_PORT` 調整。`docker compose stop` / `start` 保留資料；不要用 `down -v` 當作一般重新啟動指令。

## 前後端獨立開發

終端一，在根目錄載入本機設定，再啟動後端：

```bash
set -a
source .env
set +a
export DB_URL="jdbc:postgresql://127.0.0.1:${DB_PORT:-5432}/${DB_NAME:-tripmate}"
export SPRING_PROFILES_ACTIVE=dev
cd backend
./mvnw -B -ntp spring-boot:run
```

後端預設 `127.0.0.1:8080`；readiness 為 `http://127.0.0.1:8080/actuator/health/readiness`。登入與業務 API 是 `/api/v1`。

終端二：

```bash
cd frontend
npm ci --no-audit --no-fund
npm run dev
```

前端在 `http://127.0.0.1:5173`，Vite 代理 `/api` 到後端 8080，strictPort 防止靜默換埠。登入頁可輸入本機測試代號；相同代號對應相同開發身分，換代號可測試旅程隔離。這不是正式認證。

## 獨立建置與本機容器

```bash
(cd backend && ./mvnw -B -ntp package)
(cd frontend && npm ci --no-audit --no-fund && npm run build)

# 選用：同時啟動本機 backend + nginx frontend
docker compose --profile app up --build -d --wait
```

容器前端 `http://127.0.0.1:8081`（`FRONTEND_PORT` 可調整）；後端 8080、DB 5432。前端 Nginx 同源代理 `/api` 並支援 SPA fallback。所有主機埠綁定 loopback。Compose 等待 DB／backend healthy。

正式前端可獨立由靜態服務提供，後端 JAR 可獨立運行；但本輪沒有正式登入、HTTPS 與公開部署驗收。不要在執行 `target/*.jar` 的同時重建覆寫同一個 JAR；應先停止程序。

## Database migration

後端啟動自動執行 Flyway：

- `V1__core.sql` 保持既有內容與 checksum。
- `V2__phase_one_foundation.sql` 新增 `trip_invite`、補齊 metadata、將舊 audit key 升級 UUID，保留既有資料。
- 七張核心表都有 created/updated timestamp、version、必要 PK/FK/unique/check/index；membership 保留 UUID 複合主鍵。
- Hibernate 只 `validate`，不自動改表；Flyway clean 禁用。不保存可由活動推導的預算總額。

邀請表只儲存 32-byte hash 並限制有效期／次數；產生與兌換 API 留待 Phase 5。完整模型見 [DATA_MODEL](docs/DATA_MODEL.md)。

## 測試指令

```bash
# 需要可用的本機 Docker；啟動獨立 PostgreSQL Testcontainer
(cd backend && ./mvnw -B -ntp verify)

(cd frontend && npm ci --no-audit --no-fund)
(cd frontend && npm run typecheck && npm run lint && npm test && npm run build)
(cd frontend && npm audit --audit-level=high)

docker compose --profile app config --quiet

# 根目錄主要 gates，需 .env 或非空 DB_PASSWORD
bash scripts/check.sh
```

整合測試執行真實 Flyway 新建與 V1 升級，沒有 H2 或跳過資料庫。測試 TRUNCATE 只指向 Testcontainer；升級案例使用該容器內獨立 schema。稽核回滾案例刻意觸發 DB 約束 ERROR 日誌，應以最終測試結果判定。

既有瀏覽器回歸測試，服務啟動後執行：

```bash
cd frontend
npx playwright install chromium
E2E_BASE_URL=http://127.0.0.1:8081 npm run test:e2e

# 已安裝 Chromium 的環境
E2E_BASE_URL=http://127.0.0.1:8081 \
PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/usr/bin/chromium npm run test:e2e
```

桌面與 Pixel 7 尺寸覆蓋登入、旅程、活動編輯、預算與軟刪除；使用唯一測試代號，不清除開發資料。GitHub CI 定義於 `.github/workflows/ci.yml`，本機成功不等於遠端 Actions 已成功。

## API 與保留功能

已連接的功能：本機 session 登入、CSRF、建立／列出／讀取自己的旅程、每日活動新增／部分修改／軟刪除、版本衝突、精確預算與成員唯讀。實際端點、DTO 與錯誤碼見 [PHASE1_API](docs/PHASE1_API.md)。

所有旅程端點驗證 membership；金額以 NUMERIC／BigDecimal 與 JSON 字串傳遞。活動寫入與稽核同 transaction；stale expectedVersion 回 `409 VERSION_CONFLICT`。JPA 身分 repository 與現有 JDBC 服務共用 datasource。既有 JDBC 的鎖定／聚合邏輯保留，沒有僅為引入 JPA 而全部改寫。

TanStack Query 管理工作區與列表的伺服器資料，focus／reconnect 與成功修改後 refetch；登出／登入清除快取。表單開啟後保留 snapshot。fetch 15 秒 timeout，不自動重試寫入，避免未知提交結果導致重複新增。

## 常見問題

- `release version 21 not supported`：檢查 `JAVA_HOME/bin/javac -version`，將 JAVA_HOME 改為 JDK 21。
- DB 連不上：確認 `docker compose ps`、DB_PASSWORD、DB_URL 與 DB_PORT；readiness 含 DB 檢查，連線取得逾時為 5 秒。
- 後端啟動 profile guard 失敗：目前只能 `SPRING_PROFILES_ACTIVE=dev`；無 profile、prod、dev/prod 混用均拒絕啟動。Phase 2 才實作正式 OIDC。
- API 401：先登入；403：CSRF token 或 session 已過期，重新登入／重新載入。非會員旅程會得到 404。
- API 409：保留輸入，明確重新載入後再編輯，不自動覆寫。
- Flyway checksum 不符：不要修改已套用檔案；檢查既有 migration 與新增 migration，先備份再處理，不以清庫或 clean 當解法。
- Maven／Docker HTTPS 代理：使用平台代理與正確 CA，不關閉 TLS、checksum 或 strict-ssl。

此雲端 checkout 的可重用流程為 `bash scripts/cloud-setup.sh` 與 `bash scripts/cloud-start.sh`。前者使用 checkout 外的暫存工具／cache，Docker build 的 Maven proxy 與 CA 以 secret mount 傳入；保留既有 Docker 身分設定，只在 `.env` 不存在時產生本機隨機密碼。活程序需在環境還原後重新啟動。

## 已知限制與下一階段

此輪不宣稱完整 MVP 已完成。保留的切片仍有 1–14 天旅程與單日活動限制；開發登入不適合正式環境。尚缺 OIDC、trip PATCH/delete、伺服器建立冪等性、拖曳排序／跨日移動、邀請與管理、歷史頁、10 秒 polling 與完整同步提示。dnd-kit 目前只有依賴基礎。

下一步依 Phase 2 完成標準 OIDC、Trip CRUD、Dashboard 與建立冪等性，再依序驗收 Phase 3–6。V1 不使用 WebSocket、Redis、Kafka、Kubernetes 或微服務；不實作付款與分帳。
