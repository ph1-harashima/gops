package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.PortalOrderDetail;
import com.glv.gsysportal.exception.MissingUnitPriceException;
import com.glv.gsysportal.exception.NoOrderableItemsException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared PO Preview / Confirm Order Validation (implementation instructions
 * 3章/7章: "Previewと同じValidationを再実行する"). A single implementation
 * used by both {@link PoPreviewService} (read-only display) and
 * {@link OrderStatusTransitionService#confirm} (re-validated immediately
 * before the Status transition, never trusting that a client already saw a
 * successful Preview).
 *
 * supplierCode/brandCode/orderDate/sku presence and orderQty >= 0 are
 * guaranteed by NOT NULL / CHECK constraints on portal_order and
 * portal_order_detail (V1/V2 migrations) - re-asserted here defensively
 * (would only fail if the schema itself were compromised) rather than
 * silently assumed.
 */
@Component
public class PoPreviewValidator {

    public List<PortalOrderDetail> validateAndGetOrderableLines(PortalOrder order) {
        if (order.getSupplierCode() == null || order.getBrandCode() == null || order.getOrderDate() == null) {
            // Unreachable under the current schema (all three are NOT NULL columns) -
            // defense in depth per implementation instructions 3章's explicit checklist.
            throw new IllegalStateException("Draft " + order.getId() + " is missing required header data");
        }

        List<PortalOrderDetail> orderable = order.getDetails().stream()
                .filter(d -> !d.isRemoved())
                .filter(d -> d.getOrderQty() > 0)
                .toList();

        if (orderable.isEmpty()) {
            throw new NoOrderableItemsException(order.getId());
        }

        for (PortalOrderDetail d : orderable) {
            if (d.getSku() == null || d.getOrderQty() < 0) {
                throw new IllegalStateException("Draft " + order.getId() + " line " + d.getId() + " is invalid");
            }
        }

        Set<String> missingUnitPrice = new LinkedHashSet<>();
        for (PortalOrderDetail d : orderable) {
            if (d.getUnitPrice() == null) {
                missingUnitPrice.add(d.getSku());
            }
        }
        if (!missingUnitPrice.isEmpty()) {
            throw new MissingUnitPriceException(missingUnitPrice);
        }

        return orderable;
    }
}
