#!/usr/bin/env bash
# Runs every test of the middleware and the UI. Run it before every commit; CI runs the same.
#   ./scripts/test-all.sh            unit, API, parity and UI tests (no backend needed)
#   ./scripts/test-all.sh --live     also the read-only tests against the real local backend
#                                    (needs MARIS_LIVE_GROUP and MARIS_LIVE_PASSWORD, Tomcat on :8080)
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"

echo "== middleware: unit, API, parity, engine tests"
(cd maris-middleware && mvn -q test)

if [[ "${1:-}" == "--live" ]]; then
  echo "== middleware: live read-only tests against the real backend"
  (cd maris-middleware && mvn -q test -Plive)
fi

echo "== UI: type check, lint, tests, build"
(cd maris-ui && npx tsc --noEmit -p . && npx eslint src && npx vitest run && npm run build >/dev/null)

echo "All tests passed."
