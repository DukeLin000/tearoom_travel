# Phase 1 實作與驗證報告

日期：2026-10-15（Asia/Taipei）。依本次上傳的 [MVP V1](MVP_V1.md) 執行 Phase 1，尚未驗收完整 MVP 或公開部署。

## 起始狀態與本輪變更

GitHub 遠端是空儲存庫；雲端本機 `/workspace/tearoom_travel` 已有未提交的早期四階段實作。本輪先執行既有基準：後端 22 測試、前端 9 測試與 build 都成功，保留這些功能。依最新版六階段指令補齊基礎，沒有將舊文件的 WebSocket 計畫帶入 V1。

| 主要修改檔案 | 完成內容 |
| --- | --- |
| `backend/pom.xml`、`application.yml` | Spring Data JPA、Hibernate validate、關閉 open-in-view、Flyway clean-disabled |
| `identity/AppUser.java`、`AppUserRepository.java`、`IdentityService.java` | 實際 JPA entity/repository、開發身分原子 upsert、版本遞增；保留既有 session/CSRF 防護 |
| `db/migration/V2__phase_one_foundation.sql` | 第七張 trip_invite、hash/期限/限次約束、所有表時間戳與版本、更新時間 trigger、audit UUID 升級、索引；V1 原封保留 |
| `PhaseOneIntegrationTest.java` | 七表基礎約束、新資料庫 migration、既有 V1 user/trip/audit 升級保留、JPA 身分版本與防重複 |
| `frontend/package.json`、lockfile | 固定 TanStack Query 與 dnd-kit 依賴 |
| `frontend/src/api/`、`hooks/`、`main.tsx`、`App.tsx` | QueryClient/provider、讀取快取與 refetch、登入／登出隔離使用者快取 |
| `frontend/src/components/`、`pages/`、`types/` | 依指定目錄組織既有可用實作；保留活動編輯 snapshot 與衝突 UI |
| `frontend/vite.config.ts`、`PhaseOne.test.tsx` | 明確 5173/strictPort/API proxy、帳號切換快取驗證與原 UI 回歸 |
| `docker-compose.yml`、`scripts/`、`.gitignore` | 預設 DB、可選 app profile、保留平台 Docker 設定、忽略 work 暫存；可重用 setup/start/check |
| `README.md`、`CODEX_START.md`、`AGENTS.md`、`docs/` | 完整原始新版規格、六階段安排、REST+Polling、版本／啟動／限制與本報告 |

資料表：app_user、trip、trip_member、trip_day、itinerary_item、trip_invite、trip_change_log。各表皆有 PK/FK 與適當約束、created_at/updated_at/version；主要 id 為 UUID，membership 保留 UUID 複合主鍵。NUMERIC(14,2)/BigDecimal 與 JSON 字串維持精確金額。

## 當次實際驗證

| 檢查／指令 | 結果 |
| --- | --- |
| `backend/./mvnw -B -ntp verify`（明確設定 JDK 21） | 25 通過：11 unit、14 PostgreSQL integration；0 failure/error/skipped；JAR 成功 |
| PostgreSQL Testcontainers + Flyway | 新建 V1/V2、V1 既有資料升級、七表 metadata、邀請 schema 約束成功 |
| JPA + JDBC transaction regression | audit insert 失敗時活動與 day version 全部回滾；原併發同版本恰一成功；通過 |
| `npm ci --no-audit --no-fund` | cloud-setup 與 Docker build 均成功 frozen install |
| `npm run typecheck`、`npm run lint` | 通過；lint max-warnings 0 |
| `npm test` | 10 通過，2 個測試檔；含切換帳號清除資料快取 |
| `npm run build` | 前端 bundle 成功，原生與 Docker 都成功 |
| `npm audit --audit-level=high` | 0 vulnerabilities |
| `bash scripts/cloud-setup.sh` | 後端／前端映像建置成功，CA/proxy 保持 TLS 驗證 |
| `bash scripts/cloud-start.sh` | DB/backend healthy，frontend HTML 成功，readiness UP、匿名 API 401 |
| Compose 原開發 volume | 啟動時確認 schema 由 V1 升級 V2，既有 volume 保留，沒有清庫 |
| Playwright Chromium `desktop` + `Pixel 7` | 2 通過；登入→建立旅程→活動新增/改額→預算→刪除→預算→登出，無水平溢出 |
| 原生 `npm run dev` + curl/HTTP smoke | 5173 HTML 成功，Vite `/api` proxy 回預期匿名 401 |
| `bash -n scripts/*.sh`、Git whitespace/ignore 檢查 | 通過；`.env`、work、node_modules、target 不提交 |

實際解析版本：Spring Boot 3.5.11、Flyway 11.7.2、Hibernate 6.6.42.Final、PostgreSQL JDBC 42.7.10、測試 DB 17.11。Maven 3.9.11、Temurin 21.0.9、Node 24.19.0、npm 11.9.0、Docker 28.4.0、Compose 2.40.3。前端固定版本見 README 與 lockfile。

稽核回滾測試故意產生約束 ERROR 日誌，最終為通過。曾遇環境 JAVA_HOME 指向舊編譯器，明確指定 JDK 21 後重新完整 verify 成功；README 記錄排除方式。沒有跳過資料庫測試或關閉 TLS。

## 啟動方式

一般機器：依 README 由 `.env.example` 建立未提交的 `.env`、填本機 DB_PASSWORD，`docker compose up -d --wait db`，然後在兩個終端獨立執行後端 `./mvnw spring-boot:run`（dev profile 與 DB env）及前端 `npm ci && npm run dev`。前端 5173、API 8080。

選用完整本機容器：`docker compose --profile app up --build -d --wait`，前端 8081。此雲端機器可用 `scripts/cloud-setup.sh` / `cloud-start.sh`，保留資料庫，不使用 down -v。

## 已知限制與下一階段

- 既有 local-only dev auth 保留且 fail closed；它不是真實登入，沒有正式 OIDC 或公開部署。本輪只驗收 Phase 1。
- JPA 實作 identity；其餘既有服務保留 JdbcTemplate 的明確鎖定、聚合與交易，已執行共用 datasource 回歸驗證。
- dnd-kit 只安裝依賴；排序、跨日移動、owner 旅程管理、邀請 API／兌換、歷史頁尚未實作。
- 已有 focus/reconnect 與寫入後 refetch；10 秒 polling 與背景變更提示仍在 Phase 6。
- 尚無伺服器 POST idempotency；UI 禁止重複進行中的提交、不自動 retry，未知提交結果要求先重讀。相關完整驗收在後續功能階段補齊。
- 保留既有 1–14 天旅程、單日活動限制；Google Maps 專屬 host 限制尚未實作，現有 map URL 僅驗證安全 HTTP(S) 格式。
- GitHub Actions 與 Windows wrapper 不因本機驗證而宣稱成功；遠端狀態另查。

下一階段為 Phase 2：標準 OIDC、正式 cookie/CSRF 設定、Trip PATCH/delete／版本／稽核、Dashboard 與建立冪等性，再依序完成 Phase 3–6。V1 不新增 WebSocket。
