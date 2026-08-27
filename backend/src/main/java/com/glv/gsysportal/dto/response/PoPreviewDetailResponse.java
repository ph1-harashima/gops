package com.glv.gsysportal.dto.response;

import java.math.BigDecimal;

/**
 * Manufacturer-facing PO line. Deliberately excludes Recommended Qty /
 * Current Stock / Safety Stock / Recent Sales / Formula / Item Status -
 * those are internal judgement information (implementation instructions 5章
 * / Requirements MD 31.2) and must never appear on a PO shown to a Supplier.
 */
public record PoPreviewDetailResponse(
        int lineNo,
        String sku,
        String itemName,
        int orderQty,
        BigDecimal unitPrice,
        BigDecimal amount
) {
}
