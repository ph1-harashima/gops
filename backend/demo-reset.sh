#!/usr/bin/env bash
# Demo Reset (implementation instructions Step 5 5章).
#
# Truncates ONLY the Prototype PostgreSQL business-workflow tables
# (portal_order, portal_order_detail, supplier_response,
# supplier_response_detail, order_attention, audit_event). portal_user is
# preserved. The Legacy Demo MySQL Seed Schema is never touched by this
# script - it only ever connects to the Prototype DataSource.
#
# NEVER exposed as a Backend API - CLI/script only, per implementation
# instructions Step 5 5章. Run this before each demo rehearsal.
#
# Safe by construction: the application's Safety Guard
# (SafetyGuardEnvironmentPostProcessor) refuses to even start unless the
# active profile is local/demo/test AND both the Legacy and Prototype JDBC
# URLs resolve to allowlisted localhost/Docker-service hosts and database
# names - so this script cannot reach a non-local database. DemoResetRunner
# additionally re-validates the Prototype host/database name at runtime
# before issuing any TRUNCATE.
#
# Usage:
#   cd backend && ./demo-reset.sh
#
# Requires: local Legacy Demo MySQL + Prototype PostgreSQL containers
# already running (docker compose up -d).

set -euo pipefail
cd "$(dirname "$0")"

echo "=== G-SYS Prototype Demo Reset ==="
echo "Target: Prototype PostgreSQL (localhost:54321/gsys_portal) business-data tables only."
echo "portal_user accounts are preserved. Legacy Demo MySQL is never touched."
echo ""

mvn -q spring-boot:run \
    -Dspring-boot.run.profiles=local \
    -Dspring-boot.run.arguments=--app.demo-reset.enabled=true

echo ""
echo "=== Demo Reset finished. ==="
