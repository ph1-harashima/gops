package com.glv.gsysportal.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** GET /api/orders/history row (implementation instructions 24章). */
public record OrderHistorySummaryResponse(
        Long id,
        String draftNo,
        String prototypePoNo,
        // Phase 1 Final Cleanup (Order History Number Model Audit): the
        // Official PO No. (G-SYS/Excel/PDF/Manufacturer Send source of
        // truth) and its Revision were previously absent from this DTO -
        // the List could not show them even though prototypePoNo (Portal管理番号)
        // was already distinguishable from officialPoNo at the entity level.
        // Both null until Official PO integration first assigns them
        // (PortalOrder.officialPoNo / currentRevisionNo Javadoc) - never
        // substitute draftNo/prototypePoNo for these.
        String officialPoNo,
        Integer revisionNo,
        // Post-Freeze Visual Walkthrough Findings Fix (Finding #4,
        // docs/gops-visual-walkthrough-findings-fix.md): the CURRENT
        // (latest-revision) Official PO Integration Request's own lifecycle
        // axis (ACTIVE/SUPERSEDED/CANCEL_REQUESTED/CANCELLED, same value the
        // Order Detail page's Revision History table already reads) - null
        // until an Official PO integration first exists. status above is
        // PortalOrder's own Business Workflow Status and is intentionally
        // NEVER changed by a PO cancellation (Reissue eligibility/history
        // depend on it staying APPROVED); the Frontend combines both so a
        // cancelled Order's Primary Status is shown correctly in this List
        // without altering status itself.
        String lifecycleStatus,
        LocalDate orderDate,
        String supplierCode,
        String supplierName,
        String brandCode,
        String brandName,
        int skuCount,
        int totalOrderedQty,
        BigDecimal totalAmount,
        String status,
        List<String> activeAttentionTypes,
        OffsetDateTime updatedAt
) {
}
