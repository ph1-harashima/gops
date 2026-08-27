package com.glv.gsysportal.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * PO Preview (implementation instructions 2章/4章/5章). READ ONLY - built
 * entirely from the already-persisted Draft (Prototype DB as the source of
 * truth); Order Qty and other business values sent by the Frontend are never
 * used to build this response. A dedicated DTO, deliberately not reusing
 * OrderDraftResponse/OrderCandidate, so internal judgement fields never leak
 * into a Manufacturer-facing view (implementation instructions 5章).
 */
public record PoPreviewResponse(
        Long draftId,
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
        List<PoPreviewDetailResponse> details,
        PoPreviewSummaryResponse summary,
        ManufacturerCommunicationResponse manufacturerCommunication,
        boolean demoMode
) {
}
