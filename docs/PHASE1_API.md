# Phase 1 implemented API

This supplements the original API_CONTRACT.md without changing its future-phase requirements.

## Authentication

Only the `dev` profile is supported. No-profile/production/mixed-profile startup fails closed.

1. `GET /api/v1/auth/csrf` (public) returns `{ "token": "...", "headerName": "X-CSRF-TOKEN" }` and establishes the CSRF session.
2. `POST /api/v1/auth/dev-login` with the CSRF header and `{ "subject": "alice", "displayName": "Alice" }` establishes the authenticated session. Subject: `[a-zA-Z0-9_-]{1,40}`; displayName: nonblank, max 80. This is a local test identity, not real authentication.
3. `GET /api/v1/auth/me` returns `{ "id": "uuid", "displayName": "Alice" }` or 401.
4. `POST /api/v1/auth/logout` with CSRF returns 204 and invalidates the session.

Browser requests are same-origin; cookies are HttpOnly and SameSite=Lax. Vite/Nginx proxy `/api`. No arbitrary user headers or user IDs select an authenticated user. Every write requires the CSRF header. Identity is stored as issuer `urn:tripmate:local-dev` plus subject; production OIDC is deferred.

## Implemented routes

| Method | Route after `/api/v1` | Response |
| --- | --- | --- |
| POST | `/trips` | 201, complete trip, Location header |
| GET | `/trips?page=0&size=20` | `{items, page, size, total}`; size 1–100 |
| GET | `/trips/{tripId}` | trip including memberCount and ownerUserId |
| GET | `/trips/{tripId}/schedule` | `{tripId, days:[{id, localDate, version, items:[...]}]}` |
| GET | `/trips/{tripId}/budget` | budgetLimit, plannedTotal, remaining, estimatedPerPerson, memberCount, breakdown |
| GET | `/trips/{tripId}/members` | `[{id, displayName}]` |
| POST | `/trips/{tripId}/items` | 201, new item, version 0 |
| PATCH | `/trips/{tripId}/items/{itemId}` | 200, full updated item, incremented version |
| DELETE | `/trips/{tripId}/items/{itemId}?expectedVersion=0` | 204, soft delete |

Create-trip fields are exactly the original contract. Date range is inclusive, 1–14 days; timezone must be an IANA zone; currency must be an uppercase ISO 4217 code. Owner is the authenticated caller, never a request field.

Create-item example:

```json
{
  "dayId": "<uuid from schedule>",
  "title": "海邊午餐",
  "category": "FOOD",
  "localStartTime": "12:00",
  "localEndTime": "13:00",
  "locationName": "海邊餐廳",
  "mapUrl": "https://example.com/map",
  "estimatedAmount": "2500.00",
  "note": "記得提前訂位"
}
```

Categories: `FOOD`, `TRANSPORT`, `STAY`, `ACTIVITY`, `SHOPPING`, `OTHER`. Title max 160; location max 255; map URL max 2048, absolute HTTP/HTTPS without credentials; note max 10000, defaults to empty on create. Both times may be null; if both exist, end must be later on the same day. Amount is required and must be a nonnegative decimal **string** with up to 12 integer digits and 2 decimals, without exponent or leading plus sign. JSON numbers are rejected.

PATCH is partial and must include nonnegative integer `expectedVersion` and at least one editable field. Editable fields: title, category, localStartTime, localEndTime, locationName, mapUrl, estimatedAmount, note. `dayId` and ordering are immutable in Phase 1. Optional fields may be cleared with null; title/category/amount/note cannot be null. DELETE uses the explicit query parameter mechanism permitted by the original contract; `If-Match` is not implemented.

Budget category breakdown is an object keyed by category with fixed-two-decimal strings. Missing categories have no entries. Per-person preview uses HALF_UP at 2 decimals and must be labeled an estimate. Reads exclude deleted items; writes and audit rows are committed atomically. Day version increments on create/delete; item version increments on edit/delete. Creating/deleting items keeps active positions contiguous; editing preserves order.

Errors use `{code,message,traceId}` and `X-Trace-Id`. Validation returns 400 `INVALID_INPUT`, missing authentication 401 `UNAUTHENTICATED`, CSRF failure 403 `FORBIDDEN`, nonexistent/inaccessible/cross-trip resources 404 `NOT_FOUND`, stale writes 409 `VERSION_CONFLICT`. Connection acquisition failures return 503 `DATABASE_UNAVAILABLE`.

POST idempotency keys, trip metadata PATCH, trip deletion, reorder, cross-day move, invitation redemption, history pagination, 10-second polling and OIDC are unimplemented. The UI disables duplicate in-flight submissions; it never retries a mutation automatically. On uncertain network failures, refetch before retrying.

V1 explicitly excludes WebSocket. Later endpoints and six-phase sequencing follow MVP_V1.md / EXECUTION_PLAN.md. The Phase 1 invite schema does not expose invite endpoints.
