package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.response.FulfillmentLineView;
import com.glv.gsysportal.dto.response.FulfillmentView;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 7-C7A 25章 Heavy Regression: Fulfillment READ / Calculation /
 * NOT_LINKED / Partial / Fulfilled, against the real Legacy Demo MySQL Test
 * Fixtures (docs/fulfillment-follow-up-foundation.md 5章 - PO-OUTDOOR-01/02/05,
 * added to backend/demo-data alongside the tr_inv/tr_inv_dtl schema this
 * Phase introduced). Same whole-test-method Prototype transaction + rollback
 * pattern as the other Step 2+ integration tests - Legacy is never written
 * to by any test in this class (READ ONLY throughout).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class FulfillmentServiceIntegrationTest {

    private static final String ADMIN = "admin01";

    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private PortalOrderRepository portalOrderRepository;
    @Autowired
    private FulfillmentService fulfillmentService;

    private PortalOrder createDraftOrder(String sku) {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(sku), null, null, null), ADMIN);
        return portalOrderRepository.findById(draft.id()).orElseThrow();
    }

    /** Test-only officialPoNo (7-C7A 5章: "Prototype Demo DB内だけでtest-only
     * officialPoNoを設定する。Production番号と混同しない") - simulates what
     * 7-C2B will eventually assign for real. */
    private PortalOrder linkToOfficialPo(PortalOrder order, String officialPoNo) {
        order.setOfficialPoNo(officialPoNo);
        return portalOrderRepository.save(order);
    }

    @Test
    void notLinkedWhenOfficialPoNoIsNull() {
        PortalOrder order = createDraftOrder("OD-TENT-001");

        FulfillmentView view = fulfillmentService.getFulfillment(order.getId());

        assertEquals(FulfillmentView.LINK_STATE_NOT_LINKED, view.linkState());
        assertEquals(null, view.officialPoNo());
        assertEquals(null, view.fulfillmentStatus());
        // 7-C7A 4章: never shown as 0件/未納 - lines stays empty, not a
        // misleading zero-quantity Fulfilled/Open line.
        assertTrue(view.lines().isEmpty());
    }

    @Test
    void poNotFoundWhenOfficialPoNoDoesNotExistInLegacy() {
        PortalOrder order = linkToOfficialPo(createDraftOrder("OD-TENT-001"), "PO-DOES-NOT-EXIST-IN-LEGACY");

        FulfillmentView view = fulfillmentService.getFulfillment(order.getId());

        assertEquals(FulfillmentView.LINK_STATE_PO_NOT_FOUND, view.linkState());
        assertTrue(view.lines().isEmpty());
    }

    @Test
    void fulfilledWhenFullyStockedIn() {
        PortalOrder order = linkToOfficialPo(createDraftOrder("OD-TENT-001"), "PO-OUTDOOR-01");

        FulfillmentView view = fulfillmentService.getFulfillment(order.getId());

        assertEquals(FulfillmentView.LINK_STATE_LINKED, view.linkState());
        assertEquals(FulfillmentLineView.STATUS_FULFILLED, view.fulfillmentStatus());
        assertEquals(1, view.lines().size());
        FulfillmentLineView line = view.lines().get(0);
        assertEquals("OD-TENT-001", line.skuCode());
        assertEquals(3, line.orderedQty());
        assertEquals(3, line.invoicedQty());
        assertEquals(3, line.stockInQty());
        assertEquals(0, line.outstandingQty());
        assertEquals(FulfillmentLineView.STATUS_FULFILLED, line.lineStatus());
    }

    @Test
    void partialWhenOnlySomeStockedIn() {
        PortalOrder order = linkToOfficialPo(createDraftOrder("OD-TENT-002"), "PO-OUTDOOR-02");

        FulfillmentView view = fulfillmentService.getFulfillment(order.getId());

        assertEquals(FulfillmentLineView.STATUS_PARTIAL, view.fulfillmentStatus());
        FulfillmentLineView line = view.lines().get(0);
        assertEquals(3, line.orderedQty());
        assertEquals(3, line.invoicedQty());
        assertEquals(2, line.stockInQty());
        assertEquals(1, line.outstandingQty());
    }

    @Test
    void openWhenNothingInvoicedYet() {
        PortalOrder order = linkToOfficialPo(createDraftOrder("OD-CHAIR-001"), "PO-OUTDOOR-03");

        FulfillmentView view = fulfillmentService.getFulfillment(order.getId());

        assertEquals(FulfillmentLineView.STATUS_OPEN, view.fulfillmentStatus());
        FulfillmentLineView line = view.lines().get(0);
        assertEquals(3, line.orderedQty());
        assertEquals(0, line.invoicedQty());
        assertEquals(0, line.stockInQty());
        assertEquals(3, line.outstandingQty());
        assertEquals(FulfillmentLineView.STATUS_OPEN, line.lineStatus());
    }

    /** The most important regression in this class: proves the Credit PO
     * netting mechanism (docs/fulfillment-follow-up-foundation.md 2章) -
     * without it, this would misread as fully FULFILLED at qty 3 instead of
     * the true PARTIAL at qty 2. */
    @Test
    void creditPoIsNettedIntoTrueStockInQty() {
        PortalOrder order = linkToOfficialPo(createDraftOrder("OD-BAG-001"), "PO-OUTDOOR-05");

        FulfillmentView view = fulfillmentService.getFulfillment(order.getId());

        FulfillmentLineView line = view.lines().get(0);
        assertEquals(3, line.orderedQty(), "orderedQty must come from the ORIGINAL PO only, never the Credit PO");
        assertEquals(3, line.invoicedQty(), "invoicedQty must come from the ORIGINAL invoice line only");
        assertEquals(2, line.stockInQty(), "stockInQty must net Original(3) + Credit(-1) = 2, the TRUE received qty");
        assertEquals(1, line.outstandingQty());
        assertEquals(FulfillmentLineView.STATUS_PARTIAL, line.lineStatus());
    }

    @Test
    void getFulfillmentOnUnknownOrderThrowsNotFound() {
        assertThrows(DraftNotFoundException.class, () -> fulfillmentService.getFulfillment(-1L));
    }
}
