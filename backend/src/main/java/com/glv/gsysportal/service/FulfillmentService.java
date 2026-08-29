package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.PortalOrderDetail;
import com.glv.gsysportal.dto.response.FulfillmentLineView;
import com.glv.gsysportal.dto.response.FulfillmentView;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.repository.legacy.FulfillmentReadRepository;
import com.glv.gsysportal.repository.legacy.row.LegacyInvoiceLineRow;
import com.glv.gsysportal.repository.legacy.row.LegacyPoHeaderRow;
import com.glv.gsysportal.repository.legacy.row.LegacyPoLineRow;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * GET /api/orders/{id}/fulfillment (Phase 7-C7A 3章/4章). Pure Legacy READ
 * ONLY - no row is ever written to Prototype or Legacy by this class. Never
 * audited per-call (7-C7A 21章's explicit permission - this is a plain
 * informational view, like every other GET endpoint in this codebase).
 */
@Service
public class FulfillmentService {

    private final PortalOrderRepository portalOrderRepository;
    private final FulfillmentReadRepository fulfillmentReadRepository;

    public FulfillmentService(PortalOrderRepository portalOrderRepository,
                               FulfillmentReadRepository fulfillmentReadRepository) {
        this.portalOrderRepository = portalOrderRepository;
        this.fulfillmentReadRepository = fulfillmentReadRepository;
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public FulfillmentView getFulfillment(Long orderId) {
        PortalOrder order = portalOrderRepository.findById(orderId).orElseThrow(() -> new DraftNotFoundException(orderId));

        String officialPoNo = order.getOfficialPoNo();
        if (officialPoNo == null) {
            // 7-C7A 4章: never shown as 0-line/未納 - an explicit NOT_LINKED state instead.
            return new FulfillmentView(orderId, null, FulfillmentView.LINK_STATE_NOT_LINKED, null, List.of());
        }

        Optional<LegacyPoHeaderRow> header = fulfillmentReadRepository.findPoHeader(officialPoNo);
        if (header.isEmpty()) {
            return new FulfillmentView(orderId, officialPoNo, FulfillmentView.LINK_STATE_PO_NOT_FOUND, null, List.of());
        }

        List<LegacyPoLineRow> poLines = fulfillmentReadRepository.findPoLines(officialPoNo);
        List<LegacyInvoiceLineRow> invoiceLines = fulfillmentReadRepository.findInvoiceLines(header.get().supplierCd(), officialPoNo);

        Map<String, String> itemNameBySku = new LinkedHashMap<>();
        for (PortalOrderDetail d : order.getDetails()) {
            itemNameBySku.putIfAbsent(d.getSku(), d.getItemNameSnapshot());
        }

        List<FulfillmentLineView> lines = poLines.stream()
                .map(poLine -> toLineView(poLine, invoiceLines, officialPoNo, itemNameBySku))
                .toList();

        String fulfillmentStatus = aggregateStatus(lines);

        return new FulfillmentView(orderId, officialPoNo, FulfillmentView.LINK_STATE_LINKED, fulfillmentStatus, lines);
    }

    private static FulfillmentLineView toLineView(LegacyPoLineRow poLine, List<LegacyInvoiceLineRow> invoiceLines,
                                                   String officialPoNo, Map<String, String> itemNameBySku) {
        int orderedQty = poLine.qtyPo() == null ? 0 : poLine.qtyPo();

        // 7-C7A 8章/FulfillmentReadRepository Javadoc: invoicedQty only counts
        // the Original PO's own invoice line(s) - a linked Credit line's own
        // .qty mirrors the discrepancy adjustment and must never be added to
        // "what was invoiced". stockInQty nets BOTH Original and Credit
        // qty_stk_in together - that is exactly what makes it the TRUE
        // physical stock-in qty.
        int invoicedQty = 0;
        int stockInQty = 0;
        for (LegacyInvoiceLineRow line : invoiceLines) {
            if (!poLine.itemCd().equals(line.itemCd())) {
                continue;
            }
            boolean isOriginal = officialPoNo.equals(line.poNo());
            if (isOriginal) {
                invoicedQty += line.qty() == null ? 0 : line.qty();
            }
            stockInQty += line.qtyStkIn() == null ? 0 : line.qtyStkIn();
        }

        int outstandingQty = orderedQty - stockInQty;
        String lineStatus = lineStatus(invoicedQty, stockInQty, orderedQty);
        String itemName = itemNameBySku.getOrDefault(poLine.itemCd(), poLine.itemCd());

        return new FulfillmentLineView(poLine.itemCd(), itemName, orderedQty, invoicedQty, stockInQty, outstandingQty, lineStatus);
    }

    /** 7-C7A 1章/3章: nothing invoiced yet -> OPEN; fully (or over-)received
     * -> FULFILLED; anything else -> PARTIAL. */
    private static String lineStatus(int invoicedQty, int stockInQty, int orderedQty) {
        if (invoicedQty == 0 && stockInQty == 0) {
            return FulfillmentLineView.STATUS_OPEN;
        }
        if (stockInQty >= orderedQty) {
            return FulfillmentLineView.STATUS_FULFILLED;
        }
        return FulfillmentLineView.STATUS_PARTIAL;
    }

    /** 7-C7A 3章: Order-level Status is aggregated from line Status - all
     * OPEN stays OPEN, all FULFILLED stays FULFILLED, anything mixed is
     * PARTIAL. An Official PO with zero lines (should not normally happen)
     * is treated as OPEN rather than throwing. */
    private static String aggregateStatus(List<FulfillmentLineView> lines) {
        if (lines.isEmpty()) {
            return FulfillmentLineView.STATUS_OPEN;
        }
        boolean allOpen = lines.stream().allMatch(l -> FulfillmentLineView.STATUS_OPEN.equals(l.lineStatus()));
        if (allOpen) {
            return FulfillmentLineView.STATUS_OPEN;
        }
        boolean allFulfilled = lines.stream().allMatch(l -> FulfillmentLineView.STATUS_FULFILLED.equals(l.lineStatus()));
        if (allFulfilled) {
            return FulfillmentLineView.STATUS_FULFILLED;
        }
        return FulfillmentLineView.STATUS_PARTIAL;
    }
}
