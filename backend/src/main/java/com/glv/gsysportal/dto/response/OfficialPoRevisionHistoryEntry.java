package com.glv.gsysportal.dto.response;

import java.time.OffsetDateTime;

/** Gap Analysis C-2 (docs/gulliver-20260917-phase1-gap-analysis.md 7章):
 * one row per Official PO Integration Request ever created for an Order
 * (one per Revision - old ones are never deleted, only marked
 * SUPERSEDED/CANCELLED). {@code integrationStatus} is the existing
 * PENDING/GENERATED/SUBMITTED/CONFIRMED/FAILED axis; {@code lifecycleStatus}
 * (ACTIVE/SUPERSEDED/CANCELLED) is the separate Document lifecycle axis -
 * the two are never conflated (7章's explicit instruction). */
public record OfficialPoRevisionHistoryEntry(
        int revisionNo,
        OffsetDateTime createdAt,
        String createdBy,
        String createdByDisplayName,
        String officialPoNo,
        String integrationStatus,
        boolean excelGenerated,
        boolean pdfGenerated,
        String lifecycleStatus,
        String lifecycleReason,
        String lifecycleChangedBy,
        OffsetDateTime lifecycleChangedAt,
        /** Gap Analysis C-2 7章's "Send Status" column - only the EMAIL
         * channel's per-Revision {@code order_email.status} (DRAFT/SENT/
         * FAILED), null when no Email row exists for this Revision (never
         * sent via Email, or the Order's resolved Channel is EDI). EDI
         * completion status is NOT per-Revision in the current Data Model
         * ({@code portal_order.edi_status} is a single column on the Order
         * itself, Fact re-confirmed this Phase) - left null rather than
         * showing a misleading, not-actually-Revision-scoped value here. */
        String emailSendStatus
) {
}
