package com.glv.gsysportal.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** GET /api/orders/{id} (implementation instructions 25章). READ ONLY - no
 * write endpoint for this view; edits go through the Draft/Supplier
 * Response screens. */
public record OrderHistoryDetailResponse(
        Long id,
        String draftNo,
        String prototypePoNo,
        String supplierCode,
        String supplierName,
        String brandCode,
        String brandName,
        LocalDate orderDate,
        LocalDate requestedDelivery,
        String currency,
        String remark,
        String status,
        int totalQty,
        BigDecimal totalAmount,
        List<OrderHistoryDetailLineView> details,
        List<AttentionSummary> orderAttentions,
        // Phase 7-H (EDI発注Workflow Foundation): null until the first Send
        // (either Channel) - see PortalOrder.communicationChannel's Javadoc.
        // Shown here (not on PO Preview, which is unreachable once an Order
        // has moved past APPROVED - PoPreviewService's own existing Status
        // Gate) since Order Detail is the one screen reachable for every
        // Status, Send included.
        String communicationChannel,
        // Phase 9-D: the Manufacturer Channel Master's resolved value for
        // this Order's supplier+brand - EMAIL/EDI/null (unresolved, no
        // Master row yet). Independent of communicationChannel above (which
        // records what actually happened on a past Send); this is "what
        // SHOULD happen" per the Master, used by the Frontend to decide
        // which of Email Send / EDI status tracker to show.
        String resolvedManufacturerChannel,
        String ediStatus,
        String ediCompletedBy,
        /** i18n localization audit (matches {@code AuditEventView.performedByDisplayName}'s
         * documented idiom) - Frontend prefers this for display, falling back
         * to ediCompletedBy when null. */
        String ediCompletedByDisplayName,
        OffsetDateTime ediCompletedAt,
        /** Gap Analysis §12 (docs/gulliver-20260917-phase1-gap-analysis.md
         * 12章): Domestic/Overseas Foundation - DOMESTIC/OVERSEAS/null
         * (unresolved, no Master row yet). Portal-only classification, NEVER
         * derived from Legacy and NEVER consulted by recommendedQty's
         * calculation - display only. */
        String resolvedRegionClassification
) {
}
