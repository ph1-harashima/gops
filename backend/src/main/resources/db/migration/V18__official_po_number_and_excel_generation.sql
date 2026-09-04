-- Prototype PostgreSQL only. Phase 9-A: Official PO Number confirmation +
-- Excel generation wiring (Production-Oriented PO Workflow §13 Phase 1).
--
-- 1) official_po_integration_request gains the 5 Excel-contract fields that
--    have no home anywhere in the domain today (Delivery Week/Date, Ship
--    Via/Term, Payment Term - docs/official-po-integration-detailed-design.md
--    §3.2). Integration-specific, staff-entered at Official-PO-confirm time -
--    deliberately kept off portal_order/portal_order_detail rather than
--    polluting Draft/Order editing with G-SYS-Excel-only concepts. All
--    nullable, matching the Excel contract's own Required/Optional split
--    (Delivery Week/Date required by Legacy at Import time, not at Portal
--    confirm time; Ship Via/Term/Payment Term optional in both places).
--
-- 2) New AuditEvent types: OFFICIAL_PO_NUMBER_CONFIRMED (written when the PO
--    No. itself changes - not on every idempotent re-confirm with the same
--    value), OFFICIAL_PO_EXCEL_GENERATED (written every generate call,
--    mirroring PRECHECK_COMPLETED's own precedent). Full allow-list
--    repeated wholesale (Postgres CHECK constraints are replaced, not
--    appended - V9/V15's own established pattern).

ALTER TABLE official_po_integration_request ADD COLUMN delivery_week VARCHAR(5);
ALTER TABLE official_po_integration_request ADD COLUMN delivery_date VARCHAR(50);
ALTER TABLE official_po_integration_request ADD COLUMN ship_via VARCHAR(100);
ALTER TABLE official_po_integration_request ADD COLUMN ship_term VARCHAR(100);
ALTER TABLE official_po_integration_request ADD COLUMN payment_term VARCHAR(100);

ALTER TABLE audit_event DROP CONSTRAINT ck_audit_event_type;
ALTER TABLE audit_event ADD CONSTRAINT ck_audit_event_type CHECK (event_type IN (
    'ORDER_DRAFT_CREATED','ORDER_QTY_CHANGED','ORDER_READY','DEMO_SENT','STATUS_CHANGED',
    'SUPPLIER_RESPONSE_RECEIVED','QUANTITY_CHANGED','DELIVERY_CHANGED',
    'ATTENTION_ADDED','ATTENTION_RESOLVED','REQUESTED_DELIVERY_CHANGED','REMARK_CHANGED',
    'ORDER_DATE_CHANGED','ORDER_RETURNED_TO_DRAFT',
    'SUBMITTED_FOR_APPROVAL','ORDER_APPROVED','APPROVED_WITH_CHANGES','RETURNED_FOR_CORRECTION',
    'OFFICIAL_PO_INTEGRATION_REQUESTED','PRECHECK_COMPLETED',
    'MAIL_PREVIEW_GENERATED',
    'SUPPLIER_RESPONSE_AGREED','ORDER_REVISION_CREATED','AGREEMENT_REOPENED',
    'FOLLOW_UP_CREATED','FOLLOW_UP_UPDATED','FOLLOW_UP_CLOSED','REORDER_CREATED',
    'LEGACY_PO_BASELINE_CAPTURED','LEGACY_PO_CHANGE_DETECTED',
    'EDI_SEND_RECORDED',
    'PRICE_CHANGE_SET_CREATED','PRICE_CHANGE_DETAIL_ADDED','PRICE_CHANGE_PROPOSED_PRICE_CHANGED',
    'PRICE_CHANGE_DETAIL_REMOVED','PRICE_CHANGE_NOTE_CHANGED',
    'OFFICIAL_PO_NUMBER_CONFIRMED','OFFICIAL_PO_EXCEL_GENERATED'
));
