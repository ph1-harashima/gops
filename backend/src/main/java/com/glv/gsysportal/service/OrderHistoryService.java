package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.OrderAttention;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.PortalOrderDetail;
import com.glv.gsysportal.domain.SupplierResponseDetail;
import com.glv.gsysportal.dto.response.AuditEventView;
import com.glv.gsysportal.dto.response.OrderHistoryDetailLineView;
import com.glv.gsysportal.dto.response.OrderHistoryDetailResponse;
import com.glv.gsysportal.dto.response.OrderHistorySummaryResponse;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.OrderAttentionRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import com.glv.gsysportal.repository.prototype.SupplierResponseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GET /api/orders/history, GET /api/orders/{id}, GET /api/orders/{id}/events
 * (implementation instructions 24章/25章/26章). Strictly READ ONLY - no
 * write method exists in this class; edits go through the Draft / Supplier
 * Response screens only (Requirements MD 31.2 "History画面は原則READ ONLY").
 */
@Service
public class OrderHistoryService {

    private final PortalOrderRepository portalOrderRepository;
    private final SupplierResponseRepository supplierResponseRepository;
    private final OrderAttentionRepository orderAttentionRepository;
    private final AuditEventRepository auditEventRepository;

    public OrderHistoryService(PortalOrderRepository portalOrderRepository,
                                SupplierResponseRepository supplierResponseRepository,
                                OrderAttentionRepository orderAttentionRepository,
                                AuditEventRepository auditEventRepository) {
        this.portalOrderRepository = portalOrderRepository;
        this.supplierResponseRepository = supplierResponseRepository;
        this.orderAttentionRepository = orderAttentionRepository;
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public List<OrderHistorySummaryResponse> list(String supplierCode, String brandCode, String status) {
        return portalOrderRepository.findAll().stream()
                .filter(o -> supplierCode == null || supplierCode.equals(o.getSupplierCode()))
                .filter(o -> brandCode == null || brandCode.equals(o.getBrandCode()))
                .filter(o -> status == null || status.equals(o.getStatus()))
                .sorted(Comparator.comparing(PortalOrder::getUpdatedAt).reversed())
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public OrderHistoryDetailResponse detail(Long id) {
        PortalOrder order = portalOrderRepository.findById(id).orElseThrow(() -> new DraftNotFoundException(id));

        Map<Long, SupplierResponseDetail> confirmedByDetailId = supplierResponseRepository.findByPortalOrderId(id)
                .map(sr -> sr.getDetails().stream()
                        .collect(Collectors.toMap(d -> d.getPortalOrderDetail().getId(), d -> d)))
                .orElse(Map.of());

        List<OrderAttention> active = orderAttentionRepository.findByPortalOrderIdAndActiveTrue(id);

        List<OrderHistoryDetailLineView> lines = order.getDetails().stream()
                .filter(d -> !d.isRemoved())
                .map(d -> toLineView(order, d, confirmedByDetailId.get(d.getId()), active))
                .toList();

        List<String> orderAttentionTypes = active.stream()
                .filter(a -> a.getPortalOrderDetailId() == null)
                .map(OrderAttention::getAttentionType)
                .toList();

        return new OrderHistoryDetailResponse(
                order.getId(), order.getDraftNo(), order.getPrototypePoNo(),
                order.getSupplierCode(), order.getSupplierNameSnapshot(),
                order.getBrandCode(), order.getBrandNameSnapshot(),
                order.getOrderDate(), order.getRequestedDelivery(), order.getCurrency(), order.getRemark(),
                order.getStatus(), order.getTotalQty(), order.getTotalAmount(),
                lines, orderAttentionTypes
        );
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public List<AuditEventView> events(Long id) {
        if (!portalOrderRepository.existsById(id)) {
            throw new DraftNotFoundException(id);
        }
        return auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(id).stream()
                .map(e -> new AuditEventView(
                        e.getEventType(), e.getPortalOrderDetailId(), e.getFieldName(),
                        e.getOldValue(), e.getNewValue(), e.getPerformedBy(), e.getPerformedAt()))
                .toList();
    }

    private OrderHistorySummaryResponse toSummary(PortalOrder order) {
        List<String> activeTypes = orderAttentionRepository.findByPortalOrderIdAndActiveTrue(order.getId()).stream()
                .map(OrderAttention::getAttentionType)
                .distinct()
                .toList();
        long skuCount = order.getDetails().stream().filter(d -> !d.isRemoved()).count();
        return new OrderHistorySummaryResponse(
                order.getId(), order.getDraftNo(), order.getPrototypePoNo(), order.getOrderDate(),
                order.getSupplierCode(), order.getSupplierNameSnapshot(), order.getBrandCode(), order.getBrandNameSnapshot(),
                (int) skuCount, order.getTotalQty(), order.getTotalAmount(), order.getStatus(), activeTypes, order.getUpdatedAt()
        );
    }

    private static OrderHistoryDetailLineView toLineView(PortalOrder order, PortalOrderDetail detail,
                                                           SupplierResponseDetail response, List<OrderAttention> active) {
        List<String> attentionTypes = active.stream()
                .filter(a -> detail.getId().equals(a.getPortalOrderDetailId()))
                .map(OrderAttention::getAttentionType)
                .toList();
        return new OrderHistoryDetailLineView(
                detail.getSku(), detail.getItemNameSnapshot(), detail.getRecommendedQty(), detail.getOrderQty(),
                response == null ? null : response.getConfirmedQty(),
                response == null ? order.getRequestedDelivery() : response.getRequestedDelivery(),
                response == null ? null : response.getConfirmedDelivery(),
                attentionTypes
        );
    }
}
