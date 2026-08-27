-- Prototype PostgreSQL database bootstrap.
--
-- NOTE: This Step (Implementation Step 0/1, Order Candidate List vertical slice)
-- intentionally does NOT create any Prototype business tables (portal_order,
-- supplier_response, audit_event, etc.) - those are explicitly out of scope for
-- this step (see the "作らない" list in the implementation instructions).
-- This container is started now, ahead of need, so the dual-DataSource wiring
-- (Legacy READ ONLY + Prototype READ/WRITE) can be proven end-to-end without
-- breaking application startup. Flyway currently has zero migrations to apply.
SELECT 1;
