-- Prototype PostgreSQL only. Phase 7-H (EDI発注Workflow監査 - Foundation
-- only, no real EDI integration): confirmed via Source that
-- OrderStatusTransitionService.demoSend was the ONLY code path that ever
-- created a portal_order_revision/supplier_response row - a Supplier never
-- Email-sent could never reach Supplier Response at all. This migration adds
-- ONLY the column needed to record which Channel an Order was actually sent
-- through (EMAIL - the existing Demo Send path, unchanged behavior - or EDI
-- - new, Foundation-only) and the matching Audit event type. No Approval /
-- Supplier Response / Official PO / Fulfillment Business Rule changes -
-- Supplier Response/Revision/Agreement all key off portal_order.status
-- alone and never read this column (confirmed via Source before adding it).

ALTER TABLE portal_order ADD COLUMN communication_channel VARCHAR(20);

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
    'EDI_SEND_RECORDED'
));
