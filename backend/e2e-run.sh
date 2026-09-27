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
#   1. Preflight: fail fast (no auto-kill) if port 8080/5173 is already in
#      use - almost always a leftover process from a previous run that a
#      health check could otherwise mistake for this run's own server (see
#      the Process Cleanup Audit below for exactly how that happens).
#   2. Reset the E2E-only databases to a known baseline (./e2e-db-reset.sh),
#      run FIRST so a prior abnormally-terminated run's leftovers never
#      leak into this run.
#   3. Start the backend under the "e2e" Spring profile only.
#   4. FAIL FAST: before running a single Playwright test, grep the
#      backend's own startup log for the Flyway/Legacy connection-target
#      lines (PrototypeFlywayConfig / LegacyDataSourceConfig, both already
#      logged at INFO) and verify they show gsys_portal_e2e / legacy_demo_e2e
#      - not gsys_portal / legacy_demo. If the wrong catalog appears, or the
#      expected line never appears within the timeout, this script kills the
#      backend and aborts with a non-zero exit code before any test runs.
#      (The /actuator/health endpoint deliberately never exposes JDBC
#      URL/catalog - Production Readiness Audit §4 - so it cannot be used
#      for this check; the startup log is the only safe source.)
#   5. Start the frontend dev server and run Playwright.
#   6. Always tear down both background process trees on exit (normal
#      completion, Playwright test failure, or Ctrl+C/SIGTERM), and
#      propagate Playwright's own exit code.
#
# Process Cleanup Audit (Windows + Git Bash specific):
#   `$!` after `cmd &` is an MSYS-internal PID, not the real Windows PID
#   (WINPID) - and on Windows, `npm run dev` is actually a 4-hop chain
#   (bash running npm's own POSIX shebang -> node npm-cli.js -> cmd.exe,
#   npm's own Windows script runner -> node vite.js) that bash's job
#   control never sees past the first hop. A plain `kill $FRONTEND_PID`
#   therefore terminates only the top of that chain - the real Vite dev
#   server (and everything below it) survives as an orphan holding port
#   5173, confirmed empirically (100% reproducible across normal exit,
#   test-failure exit, and forced termination). Two changes address this:
#     - Vite is launched directly (`node node_modules/vite/bin/vite.js`),
#       skipping npm/cmd.exe entirely, so there is at most one hop between
#       this script's own job-control PID and the real process.
#     - cleanup() resolves the real WINPID via `/proc/<msys_pid>/winpid`
#       and, on Windows, uses `taskkill //PID <winpid> //T //F` (kills the
#       PID's entire process tree) instead of bash's own `kill` (which
#       only ever reaches the single tracked PID). Linux/macOS keep the
#       original kill/wait behavior unchanged (no such split there).
#   A forced termination of this script itself (e.g. TerminateProcess from
#   outside bash) still bypasses the EXIT/INT/TERM traps entirely - no
#   userspace script can prevent that. The Preflight guard above is the
#   backstop for that case: it detects a leftover listener on a later run
#   and refuses to start rather than silently reusing (or colliding with)
#   the older process.
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

# Process Cleanup Audit: OS detection - the taskkill/WINPID path below is
# Windows(Git Bash/MSYS)-only. Linux/macOS/CI keep the original plain
# kill/wait behavior, which was never the problem there (no npm/cmd.exe
# split - Linux npm execs node directly).
case "$(uname -s 2>/dev/null || echo unknown)" in
    MINGW*|MSYS*|CYGWIN*) IS_WINDOWS=1 ;;
    *) IS_WINDOWS=0 ;;
esac

# Resolves an MSYS-internal PID (what bash's own $! reports) to the real
# Windows PID, via MSYS's own /proc/<pid>/winpid mapping. On Linux/macOS
# (where no such split exists) this file never exists, so the input PID is
# simply echoed back unchanged - always safe to call unconditionally.
winpid_of() {
    local msys_pid="$1"
    if [[ -r "/proc/${msys_pid}/winpid" ]]; then
        cat "/proc/${msys_pid}/winpid" 2>/dev/null || echo "${msys_pid}"
    else
        echo "${msys_pid}"
    fi
}

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

# Returns success (0) iff something is already listening on 127.0.0.1:<port>.
# Uses bash's own /dev/tcp - no external tool dependency, works identically
# on Windows Git Bash and Linux/macOS. Run inside a subshell so the opened
# file descriptor is closed automatically and never leaks into this script.
port_in_use() {
    local port="$1"
    (exec 3<>"/dev/tcp/127.0.0.1/${port}") 2>/dev/null
}

# Preflight Guard (Process Cleanup Audit §5/§6/§7): refuses to start rather
# than risk either (a) a stale process from a previous abnormal termination
# silently answering this run's own health check, or (b) colliding with
# something genuinely unrelated on the same port. Never kills anything
# automatically - PID/CommandLine are best-effort operator information only,
# and their absence must never suppress the fail-fast itself.
check_port_free() {
    local port="$1" label="$2"
    if ! port_in_use "${port}"; then
        return 0
    fi

    echo ""
    echo "ERROR:"
    echo "Port ${port} is already in use (${label})."
    echo ""
    echo "E2E will not start because an existing ${label} process may be left"
    echo "from a previous run."
    echo ""
    echo "No process was killed automatically."
    echo ""

    local pid="" cmdline=""
    if [[ "${IS_WINDOWS}" == "1" ]]; then
        pid="$(netstat -ano -p tcp 2>/dev/null | grep -E ":${port}[[:space:]]+.*LISTENING" | awk '{print $NF}' | head -1)"
        if [[ -n "${pid}" ]]; then
            cmdline="$(powershell -NoProfile -Command "(Get-CimInstance Win32_Process -Filter \"ProcessId=${pid}\").CommandLine" 2>/dev/null)"
        fi
    else
        pid="$(lsof -ti tcp:"${port}" 2>/dev/null | head -1)"
        if [[ -n "${pid}" ]]; then
            cmdline="$(ps -p "${pid}" -o command= 2>/dev/null)"
        fi
    fi
    if [[ -n "${pid}" ]]; then
        echo "PID: ${pid}"
        [[ -n "${cmdline}" ]] && echo "Command: ${cmdline}"
        echo ""
    fi

    echo "Please verify/terminate the process manually and retry."
    exit 1
}

echo ""
echo "=== Preflight: checking port 8080/5173 are free ==="
check_port_free 8080 "backend"
check_port_free 5173 "frontend"
echo "Ports 8080 and 5173 are free."

BACKEND_PID=""
BACKEND_WINPID=""
FRONTEND_PID=""
FRONTEND_WINPID=""
CLEANUP_DONE=""

# Terminates one tracked process (and, on Windows, its entire process tree -
# see the Process Cleanup Audit banner above for why that matters). A no-op
# if the PID is unset or already gone.
terminate_tree() {
    local label="$1" msys_pid="$2" winpid="$3"
    if [[ -z "${msys_pid}" ]]; then
        return 0
    fi
    if ! kill -0 "${msys_pid}" 2>/dev/null; then
        return 0
    fi
    if [[ "${IS_WINDOWS}" == "1" && -n "${winpid}" ]]; then
        echo "Stopping ${label} (pid ${msys_pid}, winpid ${winpid}, full process tree)..."
        taskkill //PID "${winpid}" //T //F >/dev/null 2>&1 || true
    else
        echo "Stopping ${label} (pid ${msys_pid})..."
        kill "${msys_pid}" 2>/dev/null || true
    fi
    wait "${msys_pid}" 2>/dev/null || true
}

# Idempotent by construction (CLEANUP_DONE guard) - both the EXIT trap and
# the INT/TERM traps below call this, and INT/TERM's own explicit `exit`
# afterward triggers EXIT again, so a second call must be a safe no-op.
cleanup() {
    if [[ -n "${CLEANUP_DONE}" ]]; then
        return 0
    fi
    CLEANUP_DONE=1
    terminate_tree "frontend dev server" "${FRONTEND_PID}" "${FRONTEND_WINPID}"
    terminate_tree "backend" "${BACKEND_PID}" "${BACKEND_WINPID}"
}
trap cleanup EXIT
# Ctrl+C / SIGTERM: bash does not automatically re-raise the script's own
# exit past a caught signal, so cleanup must be called explicitly here too,
# followed by an explicit exit (whose own EXIT trap firing is what the
# CLEANUP_DONE guard above exists to make harmless).
trap 'cleanup; exit 130' INT
trap 'cleanup; exit 143' TERM

echo "=== Phase 1/4: resetting E2E-only databases ==="
./e2e-db-reset.sh

echo ""
echo "=== Phase 2/3: starting backend under the 'e2e' profile only ==="
: > "${BACKEND_LOG}"
./mvnw -q spring-boot:run -Dspring-boot.run.profiles=e2e >"${BACKEND_LOG}" 2>&1 &
BACKEND_PID=$!
BACKEND_WINPID="$(winpid_of "${BACKEND_PID}")"
echo "Backend starting (pid ${BACKEND_PID}, winpid ${BACKEND_WINPID}), log: ${BACKEND_LOG}"

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
# Process Cleanup Audit: launched directly via node, never through `npm run
# dev` - on Windows that would otherwise be a 4-hop chain (bash -> npm's own
# POSIX shebang -> node npm-cli.js -> cmd.exe -> node vite.js) that this
# script's own job control cannot see past the first hop. --strictPort is a
# second, independent line of defense on top of the Preflight Guard above:
# if port 5173 is somehow already taken anyway, Vite fails fast instead of
# silently falling back to 5174+ (which would otherwise let the Preflight's
# own port-8080/5173-free check pass, only for the later "frontend is up"
# health check to succeed against a DIFFERENT, unexpected server).
: > "${FRONTEND_LOG}"
(cd "${FRONTEND_DIR}" && node node_modules/vite/bin/vite.js --strictPort >"${FRONTEND_LOG}" 2>&1) &
FRONTEND_PID=$!
FRONTEND_WINPID="$(winpid_of "${FRONTEND_PID}")"
echo "Frontend starting (pid ${FRONTEND_PID}, winpid ${FRONTEND_WINPID})"
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
