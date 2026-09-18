-- Gap Analysis C-5 (docs/gulliver-20260917-phase1-gap-analysis.md 10章):
-- Email To/CC Override at Send time. order_email.to_addresses/cc_addresses
-- already record "what was actually sent" (Phase 9-E) - this adds a second,
-- separate pair recording "what the Master (Supplier Contact Resolution)
-- actually resolved to", plus a flag, so an Override is always distinguishable
-- from the normal (Master-only) path in the Audit/history record. The Master
-- itself (supplier_contact) is never written to by an Override - this is
-- purely a per-Send record.
ALTER TABLE order_email
    ADD COLUMN master_to_addresses TEXT,
    ADD COLUMN master_cc_addresses TEXT,
    ADD COLUMN recipient_override_used BOOLEAN NOT NULL DEFAULT FALSE;

-- Gap Analysis §11 (docs/gulliver-20260917-phase1-gap-analysis.md 11章):
-- Default CC Foundation - a Portal-wide setting, ADMIN-editable, that merely
-- PREFILLS the send-time CC Override field in the Frontend (still editable,
-- still requires the explicit Send click). This table deliberately does NOT
-- express "always CC this address" as an enforced Business Rule - the
-- customer instruction is explicit that no such automatic rule may be
-- implemented ("誰を必ずCCするというBusiness Ruleは実装しないでください").
-- Single-row (id=1) singleton, matching the "Portal-wide setting" shape with
-- no per-Supplier/Brand scoping - CHECK constraint enforces the singleton.
CREATE TABLE portal_mail_settings (
    id BIGINT PRIMARY KEY,
    default_cc TEXT,
    updated_by VARCHAR(50) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_portal_mail_settings_singleton CHECK (id = 1)
);
