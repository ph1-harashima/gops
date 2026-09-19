package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.PortalOrderDetail;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.request.UpdateDraftRequest;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.exception.InvalidOrderQtyException;
import com.glv.gsysportal.exception.OrderNotEditableException;
import com.glv.gsysportal.repository.legacy.row.LegacyStockRow;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Step B of Create Draft (Technical Design 9章 two-phase pattern): the
 * Prototype-only write transaction. Called AFTER Step A (Legacy READ ONLY
 * fetch, {@link LegacyStockReadRepository#findBySkus}) has already completed
 * and closed its own separate transaction - this class never touches
 * legacyDataSource, so no distributed transaction is ever created
 * (implementation instructions 6章).
 *
 * portal_order + portal_order_detail(*) + audit_event(ORDER_DRAFT_CREATED)
 * are written atomically: if anything fails partway, the whole method rolls
 * back (implementation instructions 6章/16章 Rollback Test).
 */
@Service
public class OrderDraftPersistenceService {

    private final PortalOrderRepository portalOrderRepository;
    private final AuditEventRepository auditEventRepository;
    private final DraftNoGenerator draftNoGenerator;
    private final RecommendedQtyCalculator recommendedQtyCalculator;

    public OrderDraftPersistenceService(PortalOrderRepository portalOrderRepository,
                                         AuditEventRepository auditEventRepository,
                                         DraftNoGenerator draftNoGenerator,
                                         RecommendedQtyCalculator recommendedQtyCalculator) {
        this.portalOrderRepository = portalOrderRepository;
        this.auditEventRepository = auditEventRepository;
        this.draftNoGenerator = draftNoGenerator;
        this.recommendedQtyCalculator = recommendedQtyCalculator;
    }

    @Transactional(transactionManager = "prototypeTransactionManager")
    public PortalOrder create(CreateDraftRequest request, List<LegacyStockRow> legacyRows, String performedBy) {
        OffsetDateTime now = OffsetDateTime.now();
        LocalDate orderDate = request.orderDate() != null ? request.orderDate() : LocalDate.now();

        LegacyStockRow first = legacyRows.get(0);

        PortalOrder order = new PortalOrder();
        order.setDraftNo(draftNoGenerator.generate(orderDate));
        order.setSupplierCode(first.supplierCd());
        order.setSupplierNameSnapshot(first.supplierName());
        order.setBrandCode(first.brandCd());
        order.setBrandNameSnapshot(first.brandName());
        order.setOrderDate(orderDate);
        order.setRequestedDelivery(request.requestedDelivery());
        order.setCurrency(first.currency());
        order.setStatus(PortalOrder.STATUS_DRAFT);
        order.setRemark(request.remark());
        order.setDataSource(PortalOrder.DATA_SOURCE_DEMO_LEGACY);
        order.setCreatedBy(performedBy);
        order.setCreatedAt(now);
        order.setUpdatedBy(performedBy);
        order.setUpdatedAt(now);

        int lineNo = 1;
        int totalQty = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (LegacyStockRow row : legacyRows) {
            Integer recommendedQty = recommendedQtyCalculator.calc4(row);
            int recommendedQtyValue = recommendedQty == null ? 0 : recommendedQty;

            PortalOrderDetail detail = new PortalOrderDetail();
            detail.setPortalOrder(order);
            detail.setLineNo(lineNo++);
            detail.setSku(row.itemCd());
            detail.setItemNameSnapshot(row.itemName());
            detail.setRecommendedQty(recommendedQtyValue);
            // Requirements MD 13章: Order Qty = Recommended Qty at Create Draft time.
            detail.setOrderQty(recommendedQtyValue);
            detail.setUnitPrice(row.unitPrice());
            detail.setAmount(row.unitPrice() == null
                    ? BigDecimal.ZERO
                    : row.unitPrice().multiply(BigDecimal.valueOf(recommendedQtyValue)));
            detail.setCurrentStockSnapshot(row.currentStock());
            detail.setSafetyStockSnapshot(null); // see OrderCandidateService: not computed this Step
            detail.setOpenPoSnapshot(sum(row.openPo(), row.openArrival()));
            detail.setRecentSalesSnapshot(row.monthlySales());
            detail.setLeadTimeSnapshot(row.leadTime());
            detail.setItemStatusSnapshot(row.itemStatus());
            detail.setDataSource(PortalOrder.DATA_SOURCE_DEMO_LEGACY);
            detail.setCreatedAt(now);
            detail.setUpdatedAt(now);

            order.getDetails().add(detail);
            totalQty += recommendedQtyValue;
            totalAmount = totalAmount.add(detail.getAmount());
        }
        order.setTotalQty(totalQty);
        order.setTotalAmount(totalAmount);

        PortalOrder saved = portalOrderRepository.save(order);

        auditEventRepository.save(new AuditEvent(
                saved.getId(), null, AuditEvent.ORDER_DRAFT_CREATED,
                null, null, saved.getDraftNo(), performedBy, now
        ));

        return saved;
    }

    private static Integer sum(Integer a, Integer b) {
        return (a == null ? 0 : a) + (b == null ? 0 : b);
    }

    /**
     * Save Draft (implementation instructions 7章/9章/11章/13章):
     *  - Only orderDate/requestedDelivery/remark/detail.orderQty are mutable.
     *  - Recommended Qty, Supplier, Brand, SKU, Snapshot fields are untouched
     *    (UpdateDraftRequest has no fields for them at all).
     *  - total_qty/total_amount are always recomputed server-side here, never
     *    trusted from the Frontend (implementation instructions 11章).
     *  - Audit is written per changed field, Save-unit (not per keystroke),
     *    with old/new/performed_by/performed_at (implementation instructions 9章).
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public PortalOrder update(Long id, UpdateDraftRequest request, String performedBy, boolean performerIsAdmin) {
        PortalOrder order = portalOrderRepository.findById(id).orElseThrow(() -> new DraftNotFoundException(id));

        // Phase 7-C1 editability matrix (checked first, before touching any
        // field, so a rejected save never partially applies):
        //   DRAFT            -> creator or ADMIN (minimal ownership rule -
        //                       Supplier/Brand assignment scoping stays
        //                       CUSTOMER REVIEW, 7-C1 13章)
        //   PENDING_APPROVAL -> ADMIN only (the edit-and-approve path, 11章;
        //                       the OPERATOR's Draft is locked while queued)
        //   anything else    -> not editable (unchanged Step 2 rule)
        if (PortalOrder.STATUS_DRAFT.equals(order.getStatus())) {
            if (!performerIsAdmin && !performedBy.equals(order.getCreatedBy())) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Only the Draft's creator or an ADMIN may edit it");
            }
        } else if (PortalOrder.STATUS_PENDING_APPROVAL.equals(order.getStatus())) {
            if (!performerIsAdmin) {
                throw new OrderNotEditableException(order.getStatus());
            }
        } else {
            throw new OrderNotEditableException(order.getStatus());
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<AuditEvent> events = new ArrayList<>();

        if (request.orderDate() != null && !request.orderDate().equals(order.getOrderDate())) {
            events.add(new AuditEvent(order.getId(), null, AuditEvent.ORDER_DATE_CHANGED, "orderDate",
                    String.valueOf(order.getOrderDate()), String.valueOf(request.orderDate()), performedBy, now));
            order.setOrderDate(request.orderDate());
        }

        if (request.requestedDelivery() != null && !request.requestedDelivery().equals(order.getRequestedDelivery())) {
            events.add(new AuditEvent(order.getId(), null, AuditEvent.REQUESTED_DELIVERY_CHANGED, "requestedDelivery",
                    String.valueOf(order.getRequestedDelivery()), String.valueOf(request.requestedDelivery()), performedBy, now));
            order.setRequestedDelivery(request.requestedDelivery());
        }

        if (request.remark() != null && !Objects.equals(request.remark(), order.getRemark())) {
            events.add(new AuditEvent(order.getId(), null, AuditEvent.REMARK_CHANGED, "remark",
                    order.getRemark(), request.remark(), performedBy, now));
            order.setRemark(request.remark());
        }

        if (request.details() != null) {
            for (UpdateDraftRequest.DetailQtyUpdate lineUpdate : request.details()) {
                if (lineUpdate.orderQty() == null) {
                    continue;
                }
                if (lineUpdate.orderQty() < 0) {
                    throw new InvalidOrderQtyException(lineUpdate.orderQty());
                }
                PortalOrderDetail detail = order.getDetails().stream()
                        .filter(d -> d.getId().equals(lineUpdate.detailId()))
                        .findFirst()
                        .orElse(null);
                if (detail == null || detail.isRemoved()) {
                    continue; // unknown/removed line id - silently ignored rather than failing the whole save
                }
                if (!lineUpdate.orderQty().equals(detail.getOrderQty())) {
                    events.add(new AuditEvent(order.getId(), detail.getId(), AuditEvent.ORDER_QTY_CHANGED, "orderQty",
                            String.valueOf(detail.getOrderQty()), String.valueOf(lineUpdate.orderQty()), performedBy, now));
                    detail.setOrderQty(lineUpdate.orderQty());
                    detail.setAmount(detail.getUnitPrice() == null
                            ? BigDecimal.ZERO
                            : detail.getUnitPrice().multiply(BigDecimal.valueOf(lineUpdate.orderQty())));
                    detail.setUpdatedAt(now);
                }
            }
        }

        // Recompute totals server-side from persisted lines - never trust any
        // total the Frontend might have sent (it can't; not in the DTO either).
        int totalQty = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PortalOrderDetail d : order.getDetails()) {
            if (d.isRemoved()) {
                continue;
            }
            totalQty += d.getOrderQty();
            totalAmount = totalAmount.add(d.getAmount());
        }
        order.setTotalQty(totalQty);
        order.setTotalAmount(totalAmount);
        order.setUpdatedBy(performedBy);
        order.setUpdatedAt(now);

        PortalOrder saved = portalOrderRepository.save(order);
        if (!events.isEmpty()) {
            auditEventRepository.saveAll(events);
        }
        return saved;
    }
}
