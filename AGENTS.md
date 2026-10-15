# TRIPMATE coding instructions

## Mission
Build the MVP described in `docs/` as deployable, testable production-oriented software. Prefer small vertical slices over broad untested scaffolding. No fake "complete" claims: execute available checks, report which checks passed, and list blocked checks with reasons.

## Repository conventions
- Backend: Java 21, Spring Boot, Maven wrapper, package-based modular monolith: `identity`, `trip`, `membership`, `itinerary`, `budget`, `collaboration`, `audit`.
- Frontend: React + TypeScript, Vite, typed API layer, responsive mobile-first layout. Keep state normalization and API calls out of presentational components.
- DB: PostgreSQL + Flyway versioned migrations. Never use `ddl-auto=update` for production.
- Docker Compose for app + db (local dev). Bind application only as necessary, use environment variables and a `.env.example` with no secrets.
- Follow existing code patterns if repository later acquires them; avoid unnecessary frameworks.

## Required correctness rules
1. REST + PostgreSQL are the source of truth; MVP V1 uses polling, never WebSocket.
2. Every trip-scoped HTTP request checks membership. Owner privileges are reserved for administrative/destructive trip operations.
3. `itinerary_item.estimated_amount` is the single source for V1 planned activity expenses; exclude soft-deleted items. Budget pages must never maintain a second independent cost total.
4. Monetary values use PostgreSQL NUMERIC and Java BigDecimal. API amounts are decimal strings. No float/double accounting.
5. Item editing uses an explicit expected version; stale writes return HTTP 409 with stable error code `VERSION_CONFLICT`.
6. Reordering one day is atomic, validates the exact full set of live item IDs, locks/verifies a day version; cross-day movement locks day rows in deterministic order.
7. Validate all inputs at API boundaries; no silent exception swallowing, unbounded retries, hard-coded credentials or no-timeout external calls.
8. Audit writes and data mutation belong to the same transaction. Clients refetch after successful mutations and on focus/reconnect. Phase 6 adds 10-second polling without replacing unsaved forms.
9. Soft deletion must not leave broken references or incorrect budget totals. Return 404 for item not belonging to requested trip.
10. OIDC for real users. A local-only development profile may provide a test user, but it must fail fast in non-development environments.

## Change protocol
- Read docs and inspect repository before coding.
- Begin with Phase 1 in `docs/EXECUTION_PLAN.md`; complete a working slice before starting later phases.
- Implement tests alongside features: JUnit + integration tests on backend; frontend component/tests as appropriate.
- Keep API types and docs consistent; record deviations in README with rationale.
- Run all runnable lint/build/test/migration checks. Include commands and summary in final task report.
- Never commit secrets. Do not run destructive commands without checking their scope.
- Do not add Redis, Kafka, Kubernetes, CQRS, Event Sourcing, or microservices without measured need.
