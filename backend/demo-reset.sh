#!/usr/bin/env bash
# Demo Reset (implementation instructions Step 5 5章).
#
# Truncates ONLY the Prototype PostgreSQL business-workflow tables (see
# DemoResetRunner.java for the authoritative, always-current list - kept
# here as a mirror, not duplicated logic): audit_event, order_attention,
# supplier_response_detail, supplier_response, official_po_integration_request,
# order_email, portal_order_revision_detail, portal_order_revision, follow_up_case,
# legacy_po_baseline, portal_order_detail, portal_order, price_change_set_detail,
# price_change_set. portal_user,
# supplier_contact, mail_template, and manufacturer_channel (Portal-owned
# Master data) are all preserved. The Legacy Demo MySQL Seed Schema is
# never touched by this script - it only ever connects to the Prototype
# DataSource.
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
#   cd backend && ./demo-reset.sh --include-test-master-data
#
# --include-test-master-data (Phase 1 Final Cleanup, Test Data Lifecycle):
# additionally physically DELETEs the E2E-generated supplier_contact/
# mail_template rows DemoResetRunner.cleanupTestMasterData() can identify
# with 100% confidence (see its Javadoc) - Inactive rows only, restricted to
# content patterns confirmed to never appear in real Demo Master data.
# manufacturer_channel, supplier_region_classification, official_po_short_code,
# and every other mail_template row are NEVER touched by this option - no
# reliable Test marker exists for them yet. Omit this flag for the ordinary
# Demo Reset behavior (Master data of every kind untouched, as before).
#
# Requires: local Legacy Demo MySQL + Prototype PostgreSQL containers
# already running (docker compose up -d).

set -euo pipefail
cd "$(dirname "$0")"

RUN_ARGS="--app.demo-reset.enabled=true"
if [[ "${1:-}" == "--include-test-master-data" ]]; then
    RUN_ARGS="${RUN_ARGS} --app.demo-reset.include-test-master-data=true"
    echo "=== G-SYS Prototype Demo Reset (+ Test Master Data Cleanup) ==="
else
    echo "=== G-SYS Prototype Demo Reset ==="
fi
echo "Target: Prototype PostgreSQL (localhost:54321/gsys_portal) business-data tables only."
echo "portal_user accounts are preserved. Legacy Demo MySQL is never touched."
echo ""

mvn -q spring-boot:run \
    -Dspring-boot.run.profiles=local \
    -Dspring-boot.run.arguments="${RUN_ARGS}"

echo ""
echo "=== Demo Reset finished. ==="
