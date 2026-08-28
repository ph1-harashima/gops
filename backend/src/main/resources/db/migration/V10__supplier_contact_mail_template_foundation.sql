-- Prototype PostgreSQL only. Phase 7-C3: Supplier Contact / Mail Template
-- Foundation. Forward-only. Legacy MySQL is never touched by any migration.
--
-- Legacy has no Supplier email/contact/CC Master at all (7-C3 1章), so both
-- tables here are Portal-only, with no FK to Legacy (a different database
-- entirely) - supplier_code/brand_code are validated Legacy READ ONLY at
-- write time instead (SupplierContactService/MailTemplateService).
--
-- Neither table is added to DemoResetRunner's TRUNCATE list - both are
-- configuration-like Master data (same category as portal_user, which Demo
-- Reset already preserves), not per-Order business-workflow data.

CREATE TABLE supplier_contact (
    id                  BIGSERIAL PRIMARY KEY,
    supplier_code       VARCHAR(10)     NOT NULL,
    brand_code          VARCHAR(10),
    contact_name        VARCHAR(100)    NOT NULL,
    email               VARCHAR(200)    NOT NULL,
    contact_type        VARCHAR(10)     NOT NULL,
    language            VARCHAR(10)     NOT NULL,
    region              VARCHAR(50),
    procurement_type    VARCHAR(50),
    is_primary          BOOLEAN         NOT NULL DEFAULT false,
    is_active           BOOLEAN         NOT NULL DEFAULT true,
    created_by          VARCHAR(50)     NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_by          VARCHAR(50)     NOT NULL,
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT ck_supplier_contact_contact_type CHECK (contact_type IN ('TO', 'CC')),
    CONSTRAINT ck_supplier_contact_language CHECK (language IN ('ja', 'en'))
);

-- 7-C3 4章's Uniqueness policy: (supplier, brand, email) unique among ACTIVE
-- rows only - re-adding a previously-deactivated Contact with the same
-- email is allowed. COALESCE(brand_code, '') so two rows both having a NULL
-- brand_code (the "applies to every Brand" tier) are still compared as
-- equal by this index - a plain UNIQUE index would treat every NULL as
-- distinct and never catch that duplicate.
CREATE UNIQUE INDEX uq_supplier_contact_active_identity ON supplier_contact
    (supplier_code, COALESCE(brand_code, ''), lower(email))
    WHERE is_active = true;

CREATE INDEX idx_supplier_contact_supplier_brand ON supplier_contact (supplier_code, brand_code);

CREATE TABLE mail_template (
    id                  BIGSERIAL PRIMARY KEY,
    template_name       VARCHAR(100)    NOT NULL,
    template_type       VARCHAR(30)     NOT NULL,
    supplier_code       VARCHAR(10),
    brand_code          VARCHAR(10),
    language            VARCHAR(10)     NOT NULL,
    subject_template    VARCHAR(500)    NOT NULL,
    body_template        TEXT            NOT NULL,
    attachment_type      VARCHAR(50),
    is_active            BOOLEAN         NOT NULL DEFAULT true,
    created_by           VARCHAR(50)     NOT NULL,
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_by           VARCHAR(50)     NOT NULL,
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT ck_mail_template_type CHECK (template_type IN
        ('PURCHASE_ORDER', 'PURCHASE_ORDER_REVISION', 'FOLLOW_UP', 'CANCELLATION')),
    CONSTRAINT ck_mail_template_language CHECK (language IN ('ja', 'en'))
);

-- 7-C3 7章's Resolution must never be ambiguous by construction going
-- forward: at most one ACTIVE Template per (type, supplier, brand,
-- language) tier. Same COALESCE trick as above, doubled up for the two
-- nullable columns.
CREATE UNIQUE INDEX uq_mail_template_active_identity ON mail_template
    (template_type, COALESCE(supplier_code, ''), COALESCE(brand_code, ''), language)
    WHERE is_active = true;

CREATE INDEX idx_mail_template_supplier_brand ON mail_template (supplier_code, brand_code);

-- New AuditEvent type: MAIL_PREVIEW_GENERATED (7-C3 17章). SUPPLIER_CONTACT_*/
-- MAIL_TEMPLATE_* CREATED/UPDATED are deliberately not added here - see
-- AuditEvent.java's Javadoc for why forcing Master changes into the
-- Order-scoped audit_event table would be unnatural.
ALTER TABLE audit_event DROP CONSTRAINT ck_audit_event_type;
ALTER TABLE audit_event ADD CONSTRAINT ck_audit_event_type CHECK (event_type IN (
    'ORDER_DRAFT_CREATED','ORDER_QTY_CHANGED','ORDER_READY','DEMO_SENT','STATUS_CHANGED',
    'SUPPLIER_RESPONSE_RECEIVED','QUANTITY_CHANGED','DELIVERY_CHANGED',
    'ATTENTION_ADDED','ATTENTION_RESOLVED','REQUESTED_DELIVERY_CHANGED','REMARK_CHANGED',
    'ORDER_DATE_CHANGED','ORDER_RETURNED_TO_DRAFT',
    'SUBMITTED_FOR_APPROVAL','ORDER_APPROVED','APPROVED_WITH_CHANGES','RETURNED_FOR_CORRECTION',
    'OFFICIAL_PO_INTEGRATION_REQUESTED','PRECHECK_COMPLETED','MAIL_PREVIEW_GENERATED'
));
