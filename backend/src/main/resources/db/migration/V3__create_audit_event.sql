-- Prototype PostgreSQL only. Technical Design 5.6.
-- Append-only by application convention: no repository/service method in this
-- codebase issues UPDATE/DELETE against audit_event (only INSERT via
-- AuditEventService). DB-role-level enforcement (REVOKE UPDATE/DELETE) is not
-- applied here because the connecting role is also the migration/table owner
-- in this Prototype, which Postgres exempts from such REVOKEs; a stronger
-- guarantee would require a separate non-owner application role and is
-- deferred as a later hardening item, not required for this Step's scope.

CREATE TABLE audit_event (
    id                          BIGSERIAL PRIMARY KEY,
    portal_order_id             BIGINT          NOT NULL REFERENCES portal_order(id),
    portal_order_detail_id      BIGINT          REFERENCES portal_order_detail(id),
    event_type                  VARCHAR(30)     NOT NULL,
    field_name                  VARCHAR(50),
    old_value                   VARCHAR(500),
    new_value                   VARCHAR(500),
    performed_by                VARCHAR(50)     NOT NULL,
    performed_at                TIMESTAMPTZ     NOT NULL DEFAULT now(),
    note                        TEXT,
    CONSTRAINT ck_audit_event_type CHECK (event_type IN (
        'ORDER_DRAFT_CREATED','ORDER_QTY_CHANGED','ORDER_READY','DEMO_SENT','STATUS_CHANGED',
        'SUPPLIER_RESPONSE_RECEIVED','QUANTITY_CHANGED','DELIVERY_CHANGED',
        'ATTENTION_ADDED','ATTENTION_RESOLVED','REQUESTED_DELIVERY_CHANGED','REMARK_CHANGED',
        'ORDER_DATE_CHANGED'
    ))
);

CREATE INDEX idx_audit_event_order_id_performed_at ON audit_event (portal_order_id, performed_at);
