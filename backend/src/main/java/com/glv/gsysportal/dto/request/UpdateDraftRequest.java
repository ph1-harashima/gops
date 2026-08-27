package com.glv.gsysportal.dto.request;

import java.time.LocalDate;
import java.util.List;

/**
 * PUT /api/orders/drafts/{id} - only these fields are mutable (implementation
 * instructions 7章). recommendedQty/supplier/brand/sku/snapshot values are
 * NOT present here and therefore cannot be changed via this endpoint.
 */
public record UpdateDraftRequest(
        LocalDate orderDate,
        LocalDate requestedDelivery,
        String remark,
        List<DetailQtyUpdate> details
) {
    public record DetailQtyUpdate(Long detailId, Integer orderQty) {
    }
}
