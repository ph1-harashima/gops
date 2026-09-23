-- G-OPS Operational Workflow Realignment Phase F §16 (Draft Lifecycle):
-- adds DRAFT deletion. Soft delete, not hard delete - audit_event.portal_order_id
-- is a real, enforced FK (REFERENCES portal_order(id), V3, no ON DELETE
-- CASCADE) and this codebase's own established convention treats every
-- audit/history table as permanent (AuditEvent/PortalOrderRevision are
-- both explicitly documented "never UPDATEd or DELETEd"). A hard DELETE
-- would therefore either violate that FK (blocking deletion outright) or
-- require deleting the Draft's own creation audit trail, which
-- contradicts that established design. Soft delete preserves full
-- referential/audit integrity while still making the Draft functionally
-- gone from every normal view (see PortalOrder's own @SQLRestriction).
ALTER TABLE portal_order
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN deleted_by VARCHAR(50);

-- Same drop-and-recreate-the-whole-allowlist pattern every prior feature
-- migration touching this constraint has used (V35 is the immediately
-- preceding version).
ALTER TABLE audit_event DROP CONSTRAINT ck_audit_event_type;
ALTER TABLE audit_event ADD CONSTRAINT ck_audit_event_type CHECK (event_type IN (
    'ORDER_DRAFT_CREATED','ORDER_QTY_CHANGED','ORDER_READY','DEMO_SENT','STATUS_CHANGED',
    'SUPPLIER_RESPONSE_RECEIVED','QUANTITY_CHANGED','DELIVERY_CHANGED','ATTENTION_ADDED',
    'ATTENTION_RESOLVED','REQUESTED_DELIVERY_CHANGED','REMARK_CHANGED','ORDER_DATE_CHANGED',
    'ORDER_RETURNED_TO_DRAFT','SUBMITTED_FOR_APPROVAL','ORDER_APPROVED','APPROVED_WITH_CHANGES',
    'RETURNED_FOR_CORRECTION','OFFICIAL_PO_INTEGRATION_REQUESTED','PRECHECK_COMPLETED',
    'MAIL_PREVIEW_GENERATED','SUPPLIER_RESPONSE_AGREED','ORDER_REVISION_CREATED',
    'AGREEMENT_REOPENED','FOLLOW_UP_CREATED','FOLLOW_UP_UPDATED','FOLLOW_UP_CLOSED',
    'REORDER_CREATED','LEGACY_PO_BASELINE_CAPTURED','LEGACY_PO_CHANGE_DETECTED',
    'EDI_SEND_RECORDED','PRICE_CHANGE_SET_CREATED','PRICE_CHANGE_DETAIL_ADDED',
    'PRICE_CHANGE_PROPOSED_PRICE_CHANGED','PRICE_CHANGE_DETAIL_REMOVED','PRICE_CHANGE_NOTE_CHANGED',
    'OFFICIAL_PO_NUMBER_CONFIRMED','OFFICIAL_PO_EXCEL_GENERATED','OFFICIAL_PO_FILE_PLACED',
    'OFFICIAL_PO_FILE_PLACEMENT_FAILED','OFFICIAL_PO_IMPORT_CONFIRMED','EDI_INPUT_COMPLETED',
    'EMAIL_SENT','EMAIL_SEND_FAILED','OFFICIAL_PO_PDF_GENERATED','OFFICIAL_PO_REISSUED',
    'OFFICIAL_PO_CANCELLED','EMAIL_RECIPIENT_OVERRIDE_USED','OFFICIAL_PO_CANCEL_REQUESTED',
    'OFFICIAL_PO_CANCEL_NOTIFIED','SKU_EXPECTED_RESTOCK_CHANGED',
    'OFFICIAL_PO_SIGNATURE_PENDING','OFFICIAL_PO_SIGNED',
    'ORDER_DRAFT_DELETED'
));
