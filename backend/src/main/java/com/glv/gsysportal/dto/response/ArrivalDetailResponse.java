package com.glv.gsysportal.dto.response;

import java.util.List;

/** GET /api/arrivals/{supplierCode}/{poNumber}/{invoiceNumber} (Phase 8-G
 * 6章). {@code header} carries the same header-level Qty facts as one
 * {@link ArrivalSummaryResponse} row; {@code lines} is the per-SKU
 * PO->Invoice->Stock-In breakdown ({@link ArrivalLineView}) - together this
 * is the full "この発注がどこまで入荷処理されているか" view, READ ONLY,
 * with no new Workflow State. */
public record ArrivalDetailResponse(ArrivalSummaryResponse header, List<ArrivalLineView> lines) {
}
