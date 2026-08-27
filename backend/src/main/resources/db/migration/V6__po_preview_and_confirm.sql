-- Prototype PostgreSQL only. Technical Design 5.1/5.6, Implementation Step 3.
-- V1..V5 were already applied in Step 2 (committed b2eda6d) - this is a proper
-- additive migration rather than editing/reset, since real local Draft data
-- from Step 2 manual verification already exists in the dev Postgres volume.

-- Prototype PO No. sequence (implementation instructions 6章): a Postgres
-- SEQUENCE guarantees atomic, gap-tolerant, collision-free allocation even
-- under concurrent Confirm Order calls, without needing a retry loop like
-- DraftNoGenerator's random-based approach. Format assembled in
-- PrototypePoNoGenerator.java as "PO-DEMO-<yyyyMMdd>-<seq, zero-padded>" -
-- the "-DEMO-" segment keeps this unmistakably distinct from any Legacy PO
-- No. format (never mimicked - implementation instructions 6章).
CREATE SEQUENCE prototype_po_no_seq START WITH 1 INCREMENT BY 1;

-- ORDER_RETURNED_TO_DRAFT: a specific, queryable event type for
-- READY_TO_ORDER -> DRAFT (implementation instructions 15章), alongside the
-- generic STATUS_CHANGED event already in V3's CHECK list - mirrors the
-- existing ORDER_READY + STATUS_CHANGED pairing used for Confirm Order.
ALTER TABLE audit_event DROP CONSTRAINT ck_audit_event_type;
ALTER TABLE audit_event ADD CONSTRAINT ck_audit_event_type CHECK (event_type IN (
    'ORDER_DRAFT_CREATED','ORDER_QTY_CHANGED','ORDER_READY','DEMO_SENT','STATUS_CHANGED',
    'SUPPLIER_RESPONSE_RECEIVED','QUANTITY_CHANGED','DELIVERY_CHANGED',
    'ATTENTION_ADDED','ATTENTION_RESOLVED','REQUESTED_DELIVERY_CHANGED','REMARK_CHANGED',
    'ORDER_DATE_CHANGED','ORDER_RETURNED_TO_DRAFT'
));
