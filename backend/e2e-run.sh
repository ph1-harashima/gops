#!/usr/bin/env bash
# E2E Portal DB Isolation (Option A) - Phase 3/4 orchestration.
#
# The ONE supported way to run Full E2E. It exists specifically so a human
# cannot accidentally run Playwright against a backend started under the
# "demo" profile (which would re-pollute the shared gsys_portal/legacy_demo
# databases, as previously happened). All of the following decision logic
# lives HERE, in test infrastructure - never in production Java - per the
# governing E2E DB Isolation directive:
#
#   1. Reset the E2E-only databases to a known baseline (./e2e-db-reset.sh),
#      run FIRST so a prior abnormally-terminated run's leftovers never
#      leak into this run.
#   2. Start the backend under the "e2e" Spring profile only.
#   3. FAIL FAST: before running a single Playwright test, grep the
#      backend's own startup log for the Flyway/Legacy connection-target
#      lines (PrototypeFlywayConfig / LegacyDataSourceConfig, both already
#      logged at INFO) and verify they show gsys_portal_e2e / legacy_demo_e2e
#      - not gsys_portal / legacy_demo. If the wrong catalog appears, or the
#      expected line never appears within the timeout, this script kills the
#      backend and aborts with a non-zero exit code before any test runs.
#      (The /actuator/health endpoint deliberately never exposes JDBC
#      URL/catalog - Production Readiness Audit §4 - so it cannot be used
#      for this check; the startup log is the only safe source.)
#   4. Start the frontend dev server and run Playwright.
#   5. Always tear down both background processes on exit, regardless of
#      the test outcome, and propagate Playwright's own exit code.
#
# Usage:
#   cd backend && ./e2e-run.sh [-- <extra playwright args>]
#
# Requires: gsys-prototype-postgres / gsys-legacy-demo-mysql containers
# already running (docker compose up -d). Nothing else needs to be running -
# this script starts and stops the backend and frontend dev server itself.

set -euo pipefail
cd "$(dirname "$0")"
BACKEND_DIR="$(pwd)"
FRONTEND_DIR="$(cd ../frontend && pwd)"

# pom.xml requires Java 21+ (maven.compiler.release=21). This host's default
# JAVA_HOME points at a JDK 11 install, which cannot compile this project -
# override it here rather than requiring every operator to fix their shell
# profile just to run Full E2E.
if [[ -d "/c/Program Files/Java/jdk-23" ]]; then
    export JAVA_HOME="C:\\Program Files\\Java\\jdk-23"
fi

readonly LOG_DIR="${BACKEND_DIR}/target/e2e-run"
mkdir -p "${LOG_DIR}"
readonly BACKEND_LOG="${LOG_DIR}/backend.log"
readonly FRONTEND_LOG="${LOG_DIR}/frontend.log"

readonly EXPECTED_PROTOTYPE_CATALOG="gsys_portal_e2e"
readonly EXPECTED_LEGACY_CATALOG="legacy_demo_e2e"

echo "=== Phase 0: Protected DB Static Guard ==="
# Single source of truth (frontend/e2e/guard/protected-db-guard.mjs) - the
# exact same module Playwright's own globalSetup (e2e/guard/global-setup.ts)
# calls, so a spec file that hardcodes gsys_portal/legacy_demo is caught
# here BEFORE wasting time on a DB reset/backend boot, and is caught again
# by Playwright itself if someone runs `npx playwright test` directly
# without going through this script at all.
node "${FRONTEND_DIR}/e2e/guard/protected-db-guard.mjs" "${FRONTEND_DIR}/e2e"
readonly BACKEND_URL="http://localhost:8080"
readonly FRONTEND_URL="http://localhost:5173"
readonly STARTUP_TIMEOUT_SECS=90

BACKEND_PID=""
FRONTEND_PID=""

cleanup() {
    if [[ -n "${FRONTEND_PID}" ]] && kill -0 "${FRONTEND_PID}" 2>/dev/null; then
        echo "Stopping frontend dev server (pid ${FRONTEND_PID})..."
        kill "${FRONTEND_PID}" 2>/dev/null || true
        wait "${FRONTEND_PID}" 2>/dev/null || true
    fi
    if [[ -n "${BACKEND_PID}" ]] && kill -0 "${BACKEND_PID}" 2>/dev/null; then
        echo "Stopping backend (pid ${BACKEND_PID})..."
        kill "${BACKEND_PID}" 2>/dev/null || true
        wait "${BACKEND_PID}" 2>/dev/null || true
    fi
}
trap cleanup EXIT

echo "=== Phase 1/4: resetting E2E-only databases ==="
./e2e-db-reset.sh

echo ""
echo "=== Phase 2/3: starting backend under the 'e2e' profile only ==="
: > "${BACKEND_LOG}"
./mvnw -q spring-boot:run -Dspring-boot.run.profiles=e2e >"${BACKEND_LOG}" 2>&1 &
BACKEND_PID=$!
echo "Backend starting (pid ${BACKEND_PID}), log: ${BACKEND_LOG}"

echo ""
echo "=== Fail-fast guard: verifying the backend actually connected to the E2E-only databases ==="
deadline=$((SECONDS + STARTUP_TIMEOUT_SECS))
prototype_ok=""
legacy_ok=""
wrong_db_detected=""
while (( SECONDS < deadline )); do
    if ! kill -0 "${BACKEND_PID}" 2>/dev/null; then
        echo "FAIL-FAST: backend process exited before startup completed. Log tail:"
        tail -n 60 "${BACKEND_LOG}" || true
        exit 1
    fi

    if grep -q "Flyway migration target .* Catalog: ${EXPECTED_PROTOTYPE_CATALOG}" "${BACKEND_LOG}" 2>/dev/null; then
        prototype_ok=1
    fi
    if grep -q "Legacy datasource connection target .* Catalog: ${EXPECTED_LEGACY_CATALOG}" "${BACKEND_LOG}" 2>/dev/null; then
        legacy_ok=1
    fi
    # Defense in depth: abort immediately (don't even wait out the timeout)
    # if the WRONG catalog shows up - e.g. someone's shell already exported
    # SPRING_PROFILES_ACTIVE=demo and it took precedence over this script's
    # -Dspring-boot.run.profiles=e2e.
    if grep -qE "Flyway migration target .* Catalog: gsys_portal$|Legacy datasource connection target .* Catalog: legacy_demo$" "${BACKEND_LOG}" 2>/dev/null; then
        wrong_db_detected=1
        break
    fi

    if [[ -n "${prototype_ok}" && -n "${legacy_ok}" ]]; then
        break
    fi
    sleep 1
done

if [[ -n "${wrong_db_detected}" ]]; then
    echo "FAIL-FAST: backend connected to the DEMO database, not the E2E-only database. Aborting before any test runs."
    echo "This almost always means an environment variable (e.g. SPRING_PROFILES_ACTIVE) overrode the 'e2e' profile."
    grep -E "Flyway migration target|Legacy datasource connection target" "${BACKEND_LOG}" || true
    exit 1
fi
if [[ -z "${prototype_ok}" || -z "${legacy_ok}" ]]; then
    echo "FAIL-FAST: timed out after ${STARTUP_TIMEOUT_SECS}s waiting for confirmation that the backend connected to"
    echo "${EXPECTED_PROTOTYPE_CATALOG} / ${EXPECTED_LEGACY_CATALOG}. Log tail:"
    tail -n 80 "${BACKEND_LOG}" || true
    exit 1
fi

echo "Confirmed: backend is connected to ${EXPECTED_PROTOTYPE_CATALOG} (PostgreSQL) and ${EXPECTED_LEGACY_CATALOG} (MySQL)."
grep -E "Flyway migration target|Legacy datasource connection target" "${BACKEND_LOG}"

echo ""
echo "=== Waiting for backend health (${BACKEND_URL}/actuator/health) ==="
deadline=$((SECONDS + STARTUP_TIMEOUT_SECS))
until curl -fsS "${BACKEND_URL}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; do
    if (( SECONDS >= deadline )); then
        echo "FAIL-FAST: backend did not report healthy within ${STARTUP_TIMEOUT_SECS}s."
        tail -n 80 "${BACKEND_LOG}" || true
        exit 1
    fi
    sleep 1
done
echo "Backend is healthy."

echo ""
echo "=== Starting frontend dev server ==="
: > "${FRONTEND_LOG}"
(cd "${FRONTEND_DIR}" && npm run dev >"${FRONTEND_LOG}" 2>&1) &
FRONTEND_PID=$!
deadline=$((SECONDS + STARTUP_TIMEOUT_SECS))
until curl -fsS "${FRONTEND_URL}" >/dev/null 2>&1; do
    if (( SECONDS >= deadline )); then
        echo "FAIL-FAST: frontend dev server did not respond within ${STARTUP_TIMEOUT_SECS}s."
        tail -n 80 "${FRONTEND_LOG}" || true
        exit 1
    fi
    sleep 1
done
echo "Frontend dev server is up."

echo ""
echo "=== Running Playwright (Full E2E) ==="
# Several specs (fulfillment-follow-up-foundation, arrival-warehouse-stock-
# visibility-foundation, gulliver-phase1-integration, global-navigation-
# back-operation, legacy-po-concurrency-control) set up test-only fixtures
# via a direct `docker exec ... psql/mysql` call that bypasses the running
# app's own datasource/SafetyGuard isolation entirely. E2E_PROTOTYPE_DB /
# E2E_LEGACY_DB (read by those specs, defaulting to gsys_portal/legacy_demo
# for an ad-hoc manual run) redirect that raw SQL to the SAME E2E-only
# databases the app itself is connected to - without this, those specs
# would silently mutate the protected Demo databases even though the app's
# own traffic is correctly isolated (found and fixed during this audit).
export E2E_PROTOTYPE_DB="${EXPECTED_PROTOTYPE_CATALOG}"
export E2E_LEGACY_DB="${EXPECTED_LEGACY_CATALOG}"
set +e
(cd "${FRONTEND_DIR}" && npm run test:e2e -- "$@")
PLAYWRIGHT_EXIT=$?
set -e

echo ""
echo "=== Full E2E finished with exit code ${PLAYWRIGHT_EXIT} ==="
exit "${PLAYWRIGHT_EXIT}"
