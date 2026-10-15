> 此文件是完整 MVP API 目標；已實作端點以 PHASE1_API.md 為準。

# REST API contract — `/api/v1`

Authentication required except static frontend/OIDC callback and readiness endpoints. All trip operations require trip membership. Prefer consistent JSON errors `{ "code": "...", "message": "...", "traceId": "..." }`; include field-level details for 400 if useful.

## Trips
- `POST /trips` -> 201 with trip ID, title, date range, currency, budgetLimit, version
- `GET /trips` -> paginated current user's trips
- `GET /trips/{tripId}` -> trip + member count
- `PATCH /trips/{tripId}` -> versioned update; all members may update non-admin trip settings where product allows, owner for destructive operations
- `GET /trips/{tripId}/schedule` -> ordered days and ordered active items, include day/item versions
- `GET /trips/{tripId}/budget` -> budgetLimit, plannedTotal, remaining, estimatedPerPerson, breakdown by category

Example create trip:
```json
{"title":"沖繩好友旅行","destination":"沖繩","startDate":"2027-03-12","endDate":"2027-03-14","timezone":"Asia/Tokyo","currency":"TWD","budgetLimit":"30000.00"}
```

## Itinerary
- `POST /trips/{tripId}/items` -> 201 new item (dayId, title, category, optional times, location, amount, note)
- `PATCH /trips/{tripId}/items/{itemId}` -> 200 updated item with incremented version
- `DELETE /trips/{tripId}/items/{itemId}` -> 204 on successful soft delete, with `If-Match` version or explicit expectedVersion request mechanism
- `PUT /trips/{tripId}/days/{dayId}/order` -> 200 day version, array of exact ordered live item IDs and `expectedVersion`
- `POST /trips/{tripId}/items/{itemId}/move` -> 200 move to different day (Phase 3), requires both source and destination expectedDayVersion

Example PATCH amount:
```json
{"expectedVersion":3,"estimatedAmount":"2500.00"}
```
Response:
```json
{"id":"<uuid>","estimatedAmount":"2500.00","version":4,"updatedAt":"<ISO8601>"}
```
Stale item/day update -> `409 {"code":"VERSION_CONFLICT","message":"Resource was updated by another trip member"}`.

## Invites and activity (Phase 5)
- `POST /trips/{tripId}/invites` -> create secure time-limited invitation, owner only
- `POST /invites/redeem` -> accept invite while authenticated, atomic limit check
- `GET /trips/{tripId}/members` -> members
- `GET /trips/{tripId}/history?cursor=...&limit=...` -> history, sorted by `(created_at,id)` descending

## Synchronization (Phase 6)

REST + polling every 10 seconds while a workspace is visible; refetch on focus/reconnect and after successful mutations. No WebSocket/STOMP. Preserve unsaved form snapshots and surface stale data instead of replacing user input.

## Error and validation rules
- 400 invalid field/date/currency/amount/expectedVersion
- 401 unauthenticated; 403 authenticated but insufficient permission; 404 not found / outside trip where appropriate
- 409 version conflict; invalid domain inputs use 400
- no leaking details of other users' trips
- idempotency keys for create/redeem operations must be completed in their feature phases; document request fingerprint, retention and conflict semantics
