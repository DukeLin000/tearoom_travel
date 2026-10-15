#!/usr/bin/env bash
set -euo pipefail
cd /workspace/tearoom_travel
# Processes must be started again after snapshot restoration. Existing volumes are preserved.
docker compose --profile app up -d --no-build --wait --wait-timeout 90
curl --fail --silent --show-error --max-time 5 http://127.0.0.1:8080/actuator/health/readiness
curl --fail --silent --show-error --max-time 5 --output /dev/null http://127.0.0.1:8081/
# The API must deny anonymous access through the same-origin frontend proxy.
status=$(curl --silent --show-error --max-time 5 --output /dev/null --write-out '%{http_code}' http://127.0.0.1:8081/api/v1/trips)
if [[ "$status" != 401 ]]; then
  printf 'Unexpected unauthenticated API status: %s\n' "$status" >&2
  exit 1
fi
