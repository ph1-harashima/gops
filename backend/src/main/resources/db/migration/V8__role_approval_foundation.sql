-- Prototype PostgreSQL only. Phase 7-C1: Role / Approval Foundation.
-- Forward-only. Legacy MySQL is never touched by any migration.
--
-- 1) Role model collapses to the two production roles designed in
--    docs/target-production-procurement-workflow.md 4章:
--      OPERATOR (起票・承認依頼) / ADMIN (承認・差し戻し・修正承認)
--    Existing demo accounts are mapped, not dropped, so existing
--    created_by/performed_by strings in demo data stay resolvable:
--      purchase01 (PURCHASE)     -> OPERATOR
--      sales_admin (SALES_ADMIN) -> ADMIN
--      sys_admin (SYS_ADMIN)     -> ADMIN
--
-- 2) email column added now to prepare the Target "email as login id"
--    (Target Design 17章) WITHOUT switching the login identifier yet -
--    login stays username-based this Phase (see Phase 7-C1 report: the
--    identifier switch touches auth API params/E2E/seeds and is split
--    into its own later step). Demo emails use the reserved .invalid TLD -
--    never a real mailbox.
--
-- 3) Workflow Status migration (Target Design 5.1 mapping):
--    READY_TO_ORDER -> APPROVED. PENDING_APPROVAL is new and starts empty.
--    Historical audit_event rows keep their original READY_TO_ORDER strings
--    untouched (append-only Audit is never rewritten); the Frontend keeps
--    an i18n label for READY_TO_ORDER so old Timeline entries still render.

ALTER TABLE portal_user ADD COLUMN email VARCHAR(200);

UPDATE portal_user SET role = 'OPERATOR', email = 'purchase01@portal-demo.invalid'  WHERE username = 'purchase01';
UPDATE portal_user SET role = 'ADMIN',    email = 'sales_admin@portal-demo.invalid' WHERE username = 'sales_admin';
UPDATE portal_user SET role = 'ADMIN',    email = 'sys_admin@portal-demo.invalid'   WHERE username = 'sys_admin';

-- Dedicated ADMIN demo account (Phase 7-C1 18章). Password: DemoPass123!
-- (same local-only demo password policy as V5; hash reused from purchase01's
-- V5 row - BCrypt of the same demo password, safe to commit).
INSERT INTO portal_user (username, display_name, password_hash, role, email) VALUES
    ('admin01', '購買管理者（デモ）', '$2a$10$UsD.12ENDE2F5G2PYSkB.OPT/KfZXt1jVaqi0Js/ayiINNSA5L1ZO', 'ADMIN', 'admin01@portal-demo.invalid');

CREATE UNIQUE INDEX uq_portal_user_email ON portal_user (email) WHERE email IS NOT NULL;

-- The V1 check constraint enumerates allowed statuses - swap it BEFORE the
-- data migration so the UPDATE below can write the new values. READY_TO_ORDER
-- is intentionally dropped from the allowed set (fully migrated right after,
-- and no code writes it anymore); it survives only as a historical string
-- inside append-only audit_event rows.
ALTER TABLE portal_order DROP CONSTRAINT ck_portal_order_status;

UPDATE portal_order SET status = 'APPROVED' WHERE status = 'READY_TO_ORDER';

-- Re-added only after the data migration - ADD CONSTRAINT validates existing
-- rows, so it must come after every READY_TO_ORDER row has been mapped.
ALTER TABLE portal_order ADD CONSTRAINT ck_portal_order_status CHECK (status IN
    ('DRAFT','PENDING_APPROVAL','APPROVED','SENT','AWAITING_SUPPLIER','SUPPLIER_CONFIRMED','COMPLETED'));

-- 4) New Approval Workflow Audit event types (AuditEvent.java Phase 7-C1
--    constants). The full allow-list is repeated here (V6's pattern), not
--    just the additions, since Postgres CHECK constraints are replaced
--    wholesale, not appended to.
ALTER TABLE audit_event DROP CONSTRAINT ck_audit_event_type;
ALTER TABLE audit_event ADD CONSTRAINT ck_audit_event_type CHECK (event_type IN (
    'ORDER_DRAFT_CREATED','ORDER_QTY_CHANGED','ORDER_READY','DEMO_SENT','STATUS_CHANGED',
    'SUPPLIER_RESPONSE_RECEIVED','QUANTITY_CHANGED','DELIVERY_CHANGED',
    'ATTENTION_ADDED','ATTENTION_RESOLVED','REQUESTED_DELIVERY_CHANGED','REMARK_CHANGED',
    'ORDER_DATE_CHANGED','ORDER_RETURNED_TO_DRAFT',
    'SUBMITTED_FOR_APPROVAL','ORDER_APPROVED','APPROVED_WITH_CHANGES','RETURNED_FOR_CORRECTION'
));
