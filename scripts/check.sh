#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
(cd backend && ./mvnw -B -ntp verify)
(cd frontend && npm ci --no-audit --no-fund && npm run typecheck && npm run lint && npm test && npm run build)
docker compose --profile app config --quiet
