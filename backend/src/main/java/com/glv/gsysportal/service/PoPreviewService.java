package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.PortalOrderDetail;
import com.glv.gsysportal.dto.response.ManufacturerCommunicationResponse;
import com.glv.gsysportal.dto.response.PoPreviewDetailResponse;
import com.glv.gsysportal.dto.response.PoPreviewResponse;
import com.glv.gsysportal.dto.response.PoPreviewSummaryResponse;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.exception.InvalidOrderStatusException;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * POST /api/orders/drafts/{id}/preview (implementation instructions 2章):
 * strictly READ ONLY - never writes, never changes Status. Builds entirely
 * from the persisted Draft (Prototype DB as the sole source of truth); any
 * Order Qty etc. the Frontend might send is never read here (the endpoint
 * takes no request body at all - see {@link com.glv.gsysportal.controller.PoPreviewController}).
 *
 * Status DRAFT and READY_TO_ORDER are both accepted (not "DRAFT only" as a
 * literal reading of implementation instructions 3章 might suggest): section
 * 12 of the same instructions has the Frontend re-fetch this exact Preview
 * after a successful Confirm to redisplay the READY_TO_ORDER state ("Prototype
 * PO No.: 採番済み"), so a strict DRAFT-only gate here would make that
 * required post-Confirm refresh impossible. Any other Status (SENT and
 * beyond - unreachable in Step 3's scope) is rejected. This interpretation
 * is called out explicitly in the Step 3 final report for Techlead/customer
 * confirmation.
 */
@Service
public class PoPreviewService {

    private final PortalOrderRepository portalOrderRepository;
    private final PoPreviewValidator validator;
    private final DemoManufacturerCommunicationFactory communicationFactory;

    public PoPreviewService(PortalOrderRepository portalOrderRepository,
                             PoPreviewValidator validator,
                             DemoManufacturerCommunicationFactory communicationFactory) {
        this.portalOrderRepository = portalOrderRepository;
        this.validator = validator;
        this.communicationFactory = communicationFactory;
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public PoPreviewResponse preview(Long draftId) {
        PortalOrder order = portalOrderRepository.findById(draftId).orElseThrow(() -> new DraftNotFoundException(draftId));

        if (!PortalOrder.STATUS_DRAFT.equals(order.getStatus()) && !PortalOrder.STATUS_READY_TO_ORDER.equals(order.getStatus())) {
            throw new InvalidOrderStatusException(order.getStatus());
        }

        List<PortalOrderDetail> orderable = validator.validateAndGetOrderableLines(order);
        return buildResponse(order, orderable);
    }

    private PoPreviewResponse buildResponse(PortalOrder order, List<PortalOrderDetail> orderable) {
        List<PoPreviewDetailResponse> details = orderable.stream()
                .map(d -> new PoPreviewDetailResponse(
                        d.getLineNo(), d.getSku(), d.getItemNameSnapshot(), d.getOrderQty(),
                        d.getUnitPrice(), d.getUnitPrice().multiply(BigDecimal.valueOf(d.getOrderQty()))
                ))
                .toList();

        int totalQty = details.stream().mapToInt(PoPreviewDetailResponse::orderQty).sum();
        BigDecimal totalAmount = details.stream().map(PoPreviewDetailResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        PoPreviewSummaryResponse summary = new PoPreviewSummaryResponse(details.size(), totalQty, totalAmount);

        ManufacturerCommunicationResponse communication = communicationFactory.build(order, order.getPrototypePoNo());

        return new PoPreviewResponse(
                order.getId(), order.getDraftNo(), order.getPrototypePoNo(),
                order.getSupplierCode(), order.getSupplierNameSnapshot(),
                order.getBrandCode(), order.getBrandNameSnapshot(),
                order.getOrderDate(), order.getRequestedDelivery(), order.getCurrency(), order.getRemark(),
                order.getStatus(), details, summary, communication, true
        );
    }
}
