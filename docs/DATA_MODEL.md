# Data model and invariants

Flyway `V1__core.sql` + `V2__phase_one_foundation.sql` 支援全新初始化與既有 V1 升級。不要修改已套用的 migration，不使用 Hibernate DDL 建表。

| Table | Key and fields | Constraints |
| --- | --- | --- |
| app_user | UUID id、issuer、subject、display_name | unique issuer/subject、非空顯示名稱 |
| trip | UUID id、owner、title、destination、date range、IANA timezone、currency、NUMERIC(14,2) budget_limit | owner FK、deferred owner membership FK、有效日期與非負預算 |
| trip_member | composite UUID PK trip_id/user_id、joined_at | trip/user FK、唯一 membership |
| trip_day | UUID id、trip_id、local_date | trip FK、unique trip/date |
| itinerary_item | UUID id、day_id、title、category、local times、location、map_url、NUMERIC(14,2) estimated_amount、note、sort_order、deleted_at、created_by/updated_by | FK、category enum check、非負金額／位置、end > start |
| trip_invite | UUID id、trip_id、created_by、token_hash bytea、expires_at、revoked_at、max_uses、use_count | unique 32-byte hash、trip/creator/member FK、expires > created、0 ≤ use_count ≤ max_uses、max_uses > 0 |
| trip_change_log | UUID id、trip_id、actor_id、action、entity_id、before/after JSONB | trip/actor FK、非空 action、與資料寫入相同交易 |

七張表都有 created_at、updated_at 與非負 bigint version。trip_member 保留既有 UUID 複合主鍵。V2 將舊 audit bigint sequence 改成 UUID：沒有其他 FK 或已開放的 API 引用舊 audit id，事件內容與時間保持不變。整合測試驗證既有資料升級。

Mutable tables 的 trigger 維護 updated_at；version 由應用程式的明確修改指令或 JPA @Version 控制。audit append-only，updated_at 初始等於 created_at；不提供修改歷史的 API。未儲存可由活動推導的預算總額。

索引涵蓋使用者旅程、trip owner、每日 live items 排序、trip 邀請與歷史倒序。trip_day unique index 支援按 trip 讀取。

金額為整趟旅程的預估，不是每人費用。plannedTotal 為未刪除活動 SUM；remaining 可負；estimatedPerPerson 按當前會員數使用 HALF_UP 保留兩位作顯示。JSON 金額使用字串，前端格式化不轉浮點數。

日期與時間是旅程時區的 local date/time；單筆活動不跨午夜。保留既有 1–14 天旅程限制，這是既有切片的產品限制，並非新版規格要求。Phase 2 應重新確認合理上限，不在本輪移除保護。
