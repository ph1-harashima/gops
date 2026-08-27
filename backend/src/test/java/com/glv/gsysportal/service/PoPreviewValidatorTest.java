package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.PortalOrderDetail;
import com.glv.gsysportal.exception.MissingUnitPriceException;
import com.glv.gsysportal.exception.NoOrderableItemsException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure unit tests, no Spring context - implementation instructions 3章. */
class PoPreviewValidatorTest {

    private final PoPreviewValidator validator = new PoPreviewValidator();

    private static PortalOrder order(PortalOrderDetail... details) {
        PortalOrder order = new PortalOrder();
        order.setId(1L);
        order.setSupplierCode("SUP_ALPHA");
        order.setBrandCode("BR_KITCHEN");
        order.setOrderDate(LocalDate.of(2026, 8, 27));
        for (PortalOrderDetail d : details) {
            d.setPortalOrder(order);
            order.getDetails().add(d);
        }
        return order;
    }

    private static PortalOrderDetail detail(String sku, int orderQty, BigDecimal unitPrice, boolean removed) {
        PortalOrderDetail d = new PortalOrderDetail();
        d.setId((long) sku.hashCode());
        d.setSku(sku);
        d.setOrderQty(orderQty);
        d.setUnitPrice(unitPrice);
        d.setRemoved(removed);
        return d;
    }

    @Test
    void ordersWithAtLeastOneOrderableLineAndAllPricesPresentPass() {
        PortalOrder order = order(
                detail("SKU-1", 5, BigDecimal.TEN, false),
                detail("SKU-2", 0, BigDecimal.ONE, false) // orderQty=0 excluded, not blocking
        );

        List<PortalOrderDetail> orderable = validator.validateAndGetOrderableLines(order);

        assertEquals(1, orderable.size());
        assertEquals("SKU-1", orderable.get(0).getSku());
    }

    @Test
    void removedLinesAreExcludedEvenWithPositiveOrderQty() {
        PortalOrder order = order(
                detail("SKU-1", 5, BigDecimal.TEN, true) // removed
        );

        assertThrows(NoOrderableItemsException.class, () -> validator.validateAndGetOrderableLines(order));
    }

    @Test
    void allZeroOrderQtyThrowsNoOrderableItems() {
        PortalOrder order = order(detail("SKU-1", 0, BigDecimal.TEN, false));

        assertThrows(NoOrderableItemsException.class, () -> validator.validateAndGetOrderableLines(order));
    }

    @Test
    void emptyDetailListThrowsNoOrderableItems() {
        PortalOrder order = order();

        assertThrows(NoOrderableItemsException.class, () -> validator.validateAndGetOrderableLines(order));
    }

    @Test
    void missingUnitPriceOnAnOrderableLineThrowsMissingUnitPrice() {
        PortalOrder order = order(
                detail("SKU-1", 5, BigDecimal.TEN, false),
                detail("SKU-2", 3, null, false)
        );

        MissingUnitPriceException ex = assertThrows(MissingUnitPriceException.class,
                () -> validator.validateAndGetOrderableLines(order));
        assertTrue(ex.skus().contains("SKU-2"));
        assertEquals(1, ex.skus().size());
    }

    @Test
    void missingUnitPriceOnANonOrderableLineDoesNotBlock() {
        // orderQty = 0 line with null unitPrice must not block Preview - only
        // orderable (orderQty > 0) lines are price-validated.
        PortalOrder order = order(
                detail("SKU-1", 5, BigDecimal.TEN, false),
                detail("SKU-2", 0, null, false)
        );

        List<PortalOrderDetail> orderable = validator.validateAndGetOrderableLines(order);
        assertEquals(1, orderable.size());
    }
}
