#!/usr/bin/env bash
# E2E Portal DB Isolation (Option A) - Phase 4: reset-at-start.
#
# Resets BOTH E2E-only databases to a known, empty-then-freshly-seeded
# baseline. Run this at the START of every Full E2E run (not relied upon at
# the end), so the E2E DB recovers cleanly even after a prior run was killed
# abnormally (this session's own repeated host-memory-pressure process
# kills is exactly the scenario this guards against).
#
#   gsys_portal_e2e  (PostgreSQL, in the existing gsys-prototype-postgres
#                     container): DROP + CREATE only. Left empty - Flyway
#                     (PrototypeFlywayConfig) rebuilds the schema
#                     automatically the next time the app starts under the
#                     "e2e" profile, exactly like any fresh Prototype DB.
#   legacy_demo_e2e  (MySQL, in the existing gsys-legacy-demo-mysql
#                     container): DROP + CREATE, then reseeded from the
#                     SAME source-of-truth files as legacy_demo
#                     (demo-data/01-schema.sql, 02-seed.sql) via a
#                     sed-transformed temp copy, because both files contain
#                     a hardcoded "USE legacy_demo;" that would otherwise
#                     silently redirect execution back onto the real
#                     legacy_demo database.
#
# Absolute conditions (unchanged from the governing E2E DB Isolation
# directive): this script NEVER touches legacy_demo or gsys_portal. It
# hard-codes the exact "_e2e" database names below and only ever issues
# DROP/CREATE/seed statements against those exact names - it does not
# accept a database name as a parameter, specifically so it cannot be
# pointed at the wrong database by mistake.
#
# Usage:
#   cd backend && ./e2e-db-reset.sh
#
# Requires: gsys-prototype-postgres and gsys-legacy-demo-mysql containers
# already running (docker compose up -d).

set -euo pipefail
cd "$(dirname "$0")"

readonly PG_CONTAINER="gsys-prototype-postgres"
readonly PG_ADMIN_DB="postgres"
readonly PG_USER="gsys_portal"
readonly PG_E2E_DB="gsys_portal_e2e"

readonly MYSQL_CONTAINER="gsys-legacy-demo-mysql"
readonly MYSQL_ROOT_PW="root_demo_pw"
readonly MYSQL_E2E_DB="legacy_demo_e2e"
readonly MYSQL_RO_USER="gsys_portal_ro"

echo "=== E2E DB Reset: ${PG_E2E_DB} (PostgreSQL) ==="
docker exec -i "${PG_CONTAINER}" psql -U "${PG_USER}" -d "${PG_ADMIN_DB}" -v ON_ERROR_STOP=1 <<SQL
DROP DATABASE IF EXISTS ${PG_E2E_DB};
CREATE DATABASE ${PG_E2E_DB} OWNER ${PG_USER};
SQL
echo "${PG_E2E_DB} recreated (empty - Flyway will migrate it on next app startup under the e2e profile)."

echo ""
echo "=== E2E DB Reset: ${MYSQL_E2E_DB} (MySQL) ==="
docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -p"${MYSQL_ROOT_PW}" <<SQL
DROP DATABASE IF EXISTS ${MYSQL_E2E_DB};
CREATE DATABASE ${MYSQL_E2E_DB} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
SQL

TMP_SCHEMA="$(mktemp)"
TMP_SEED="$(mktemp)"
trap 'rm -f "${TMP_SCHEMA}" "${TMP_SEED}"' EXIT

sed "s/legacy_demo/${MYSQL_E2E_DB}/g" demo-data/01-schema.sql > "${TMP_SCHEMA}"
sed "s/legacy_demo/${MYSQL_E2E_DB}/g" demo-data/02-seed.sql > "${TMP_SEED}"

docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -p"${MYSQL_ROOT_PW}" < "${TMP_SCHEMA}"
docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -p"${MYSQL_ROOT_PW}" < "${TMP_SEED}"

# Re-issue the SELECT-only grant for the E2E database (idempotent - grants
# are keyed by db name and are not implicitly revoked by DROP DATABASE, but
# this makes the script correct even on a from-scratch container).
docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -p"${MYSQL_ROOT_PW}" <<SQL
GRANT SELECT ON ${MYSQL_E2E_DB}.* TO '${MYSQL_RO_USER}'@'%';
REVOKE INSERT, UPDATE, DELETE, CREATE, DROP, ALTER, INDEX ON ${MYSQL_E2E_DB}.* FROM '${MYSQL_RO_USER}'@'%';
FLUSH PRIVILEGES;
SQL

echo "${MYSQL_E2E_DB} recreated and reseeded from demo-data/ (legacy_demo untouched)."
echo ""
echo "=== E2E DB Reset complete. ==="
