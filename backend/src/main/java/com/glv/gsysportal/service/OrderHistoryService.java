package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.OrderAttention;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.PortalOrderDetail;
import com.glv.gsysportal.domain.SupplierResponse;
import com.glv.gsysportal.domain.SupplierResponseDetail;
import com.glv.gsysportal.dto.response.AttentionSummary;
import com.glv.gsysportal.dto.response.AuditEventView;
import com.glv.gsysportal.dto.response.OrderHistoryDetailLineView;
import com.glv.gsysportal.dto.response.OrderHistoryDetailResponse;
import com.glv.gsysportal.dto.response.OrderHistorySummaryResponse;
import com.glv.gsysportal.domain.PortalUser;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.OrderAttentionRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import com.glv.gsysportal.repository.prototype.PortalUserRepository;
import com.glv.gsysportal.repository.prototype.SupplierResponseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private final PortalUserRepository portalUserRepository;

    public OrderHistoryService(PortalOrderRepository portalOrderRepository,
                                SupplierResponseRepository supplierResponseRepository,
                                OrderAttentionRepository orderAttentionRepository,
                                AuditEventRepository auditEventRepository,
                                PortalUserRepository portalUserRepository) {
        this.portalOrderRepository = portalOrderRepository;
        this.supplierResponseRepository = supplierResponseRepository;
        this.orderAttentionRepository = orderAttentionRepository;
        this.auditEventRepository = auditEventRepository;
        this.portalUserRepository = portalUserRepository;
    }

    /**
     * Phase 7-H (Order List search/filter audit): orderNoKeyword/itemKeyword/
     * updatedFrom/updatedTo are new (PO No./Draft No. keyword, SKU/item name
     * keyword, updatedAt date range) - added the SAME way supplierCode/
     * brandCode/status already work (Backend query params, not a
     * Frontend-only display Filter over an unfiltered fetch), so a Filter
     * added here is consistent with the 3 that already exist rather than a
     * new, different pattern.
     *
     * Architecture note (Section 9 audit, not fixed this Phase - see
     * completion report): this method is, and remains, a `findAll()` fetch
     * of the ENTIRE portal_order table followed by an in-JVM Stream filter,
     * not a DB-pushed-down WHERE clause - a pre-existing characteristic of
     * every Filter here, including the 3 that predate this Phase. Adding
     * keyword/date Filters as more Stream predicates does not make this
     * pattern any worse than it already was; it does not fix it either. If
     * Order volume grows enough for this to matter, the fix is a proper
     * Repository query (JPQL/Specification) + Pagination, which is a
     * separate, larger change than this Phase's scope.
     */
    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public List<OrderHistorySummaryResponse> list(String supplierCode, String brandCode, String status,
                                                    String orderNoKeyword, String itemKeyword,
                                                    LocalDate updatedFrom, LocalDate updatedTo) {
        String normalizedOrderNoKeyword = normalizeKeyword(orderNoKeyword);
        String normalizedItemKeyword = normalizeKeyword(itemKeyword);
        return portalOrderRepository.findAll().stream()
                .filter(o -> supplierCode == null || supplierCode.equals(o.getSupplierCode()))
                .filter(o -> brandCode == null || brandCode.equals(o.getBrandCode()))
                .filter(o -> status == null || status.equals(o.getStatus()))
                .filter(o -> matchesOrderNoKeyword(o, normalizedOrderNoKeyword))
                .filter(o -> matchesItemKeyword(o, normalizedItemKeyword))
                .filter(o -> matchesUpdatedRange(o, updatedFrom, updatedTo))
                .sorted(Comparator.comparing(PortalOrder::getUpdatedAt).reversed())
                .map(this::toSummary)
                .toList();
    }

    private static String normalizeKeyword(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    /** Matches either PO No. (prototypePoNo - unassigned until first
     * Approval, Section 5.1 of official-po-integration-detailed-design.md)
     * or Draft No. (draftNo - always assigned at Draft creation), so a
     * keyword typed from either number the user might be holding (a printed
     * PO, or a Draft No. noted earlier) finds the same Order. */
    private static boolean matchesOrderNoKeyword(PortalOrder o, String normalizedKeyword) {
        if (normalizedKeyword == null) {
            return true;
        }
        String draftNo = o.getDraftNo() == null ? "" : o.getDraftNo().toLowerCase(Locale.ROOT);
        String poNo = o.getPrototypePoNo() == null ? "" : o.getPrototypePoNo().toLowerCase(Locale.ROOT);
        return draftNo.contains(normalizedKeyword) || poNo.contains(normalizedKeyword);
    }

    /** Matches SKU or item name on any non-removed line - a line the user
     * removed from the Draft is no longer part of "what this Order is
     * about", same convention as detail()/toLineView() already filtering
     * `!d.isRemoved()`. */
    private static boolean matchesItemKeyword(PortalOrder o, String normalizedKeyword) {
        if (normalizedKeyword == null) {
            return true;
        }
        return o.getDetails().stream()
                .filter(d -> !d.isRemoved())
                .anyMatch(d -> {
                    String sku = d.getSku() == null ? "" : d.getSku().toLowerCase(Locale.ROOT);
                    String itemName = d.getItemNameSnapshot() == null ? "" : d.getItemNameSnapshot().toLowerCase(Locale.ROOT);
                    return sku.contains(normalizedKeyword) || itemName.contains(normalizedKeyword);
                });
    }

    /** Filters on updatedAt's local DATE (not date-time) so a Date Picker's
     * whole-day granularity matches user expectation - `updatedTo` is
     * inclusive of that entire day. */
    private static boolean matchesUpdatedRange(PortalOrder o, LocalDate updatedFrom, LocalDate updatedTo) {
        if (updatedFrom == null && updatedTo == null) {
            return true;
        }
        LocalDate updatedDate = o.getUpdatedAt().toLocalDate();
        if (updatedFrom != null && updatedDate.isBefore(updatedFrom)) {
            return false;
        }
        if (updatedTo != null && updatedDate.isAfter(updatedTo)) {
            return false;
        }
        return true;
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public OrderHistoryDetailResponse detail(Long id) {
        PortalOrder order = portalOrderRepository.findById(id).orElseThrow(() -> new DraftNotFoundException(id));

        // Phase 7-C5 5章: an Order may now have multiple Responses (one per
        // Revision); Order Detail always shows the CURRENT one - the last row
        // created, since Responses are created in strict Send order and never
        // reordered (mirrors SupplierResponseService.requireCurrentRevision's
        // "latest wins" resolution without needing a second query here).
        List<SupplierResponse> responses = supplierResponseRepository.findByPortalOrderIdOrderByIdAsc(id);
        Map<Long, SupplierResponseDetail> confirmedByDetailId = responses.isEmpty() ? Map.of()
                : responses.get(responses.size() - 1).getDetails().stream()
                        .collect(Collectors.toMap(d -> d.getPortalOrderDetail().getId(), d -> d));

        List<OrderAttention> active = orderAttentionRepository.findByPortalOrderIdAndActiveTrue(id);

        List<OrderHistoryDetailLineView> lines = order.getDetails().stream()
                .filter(d -> !d.isRemoved())
                .map(d -> toLineView(order, d, confirmedByDetailId.get(d.getId()), active))
                .toList();

        List<AttentionSummary> orderAttentions = active.stream()
                .filter(a -> a.getPortalOrderDetailId() == null)
                .map(a -> new AttentionSummary(a.getId(), a.getAttentionType()))
                .toList();

        return new OrderHistoryDetailResponse(
                order.getId(), order.getDraftNo(), order.getPrototypePoNo(),
                order.getSupplierCode(), order.getSupplierNameSnapshot(),
                order.getBrandCode(), order.getBrandNameSnapshot(),
                order.getOrderDate(), order.getRequestedDelivery(), order.getCurrency(), order.getRemark(),
                order.getStatus(), order.getTotalQty(), order.getTotalAmount(),
                lines, orderAttentions
        );
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public List<AuditEventView> events(Long id) {
        if (!portalOrderRepository.existsById(id)) {
            throw new DraftNotFoundException(id);
        }
        List<AuditEvent> rows = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(id);

        // i18n localization audit: resolve each event's performedBy (Login
        // ID, as persisted - never rewritten) to the current
        // portal_user.display_name for READ-time display only. One lookup
        // per distinct Login ID in this Timeline, not per row.
        // (Collectors.toMap rejects null values, and no matching portal_user
        // - e.g. a since-removed account - is an expected case here, so this
        // builds the map manually instead.)
        Map<String, String> displayNameByUsername = new HashMap<>();
        rows.stream().map(AuditEvent::getPerformedBy).distinct().forEach(username ->
                displayNameByUsername.put(username, portalUserRepository.findByUsername(username)
                        .map(PortalUser::getDisplayName)
                        .orElse(null)));

        return rows.stream()
                .map(e -> new AuditEventView(
                        e.getEventType(), e.getPortalOrderDetailId(), e.getFieldName(),
                        e.getOldValue(), e.getNewValue(), e.getPerformedBy(),
                        displayNameByUsername.get(e.getPerformedBy()), e.getPerformedAt()))
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
        List<AttentionSummary> attentions = active.stream()
                .filter(a -> detail.getId().equals(a.getPortalOrderDetailId()))
                .map(a -> new AttentionSummary(a.getId(), a.getAttentionType()))
                .toList();
        return new OrderHistoryDetailLineView(
                detail.getSku(), detail.getItemNameSnapshot(), detail.getRecommendedQty(), detail.getOrderQty(),
                response == null ? null : response.getConfirmedQty(),
                response == null ? order.getRequestedDelivery() : response.getRequestedDelivery(),
                response == null ? null : response.getConfirmedDelivery(),
                attentions
        );
    }
}
