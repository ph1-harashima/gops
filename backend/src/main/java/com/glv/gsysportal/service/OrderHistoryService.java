package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.OfficialPoIntegrationRequest;
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
import com.glv.gsysportal.dto.response.PageResponse;
import com.glv.gsysportal.dto.response.SkuRestockExpectationResponse;
import com.glv.gsysportal.domain.PortalUser;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.repository.legacy.LegacyStockReadRepository;
import com.glv.gsysportal.repository.legacy.row.LegacyStockRow;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.OfficialPoIntegrationRequestRepository;
import com.glv.gsysportal.repository.prototype.OrderAttentionRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import com.glv.gsysportal.repository.prototype.PortalUserRepository;
import com.glv.gsysportal.repository.prototype.SupplierResponseRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * GET /api/orders/history, GET /api/orders/{id}, GET /api/orders/{id}/events
 * (implementation instructions 24章/25章/26章). Strictly READ ONLY - no
 * write method exists in this class; edits go through the Draft / Supplier
 * Response screens only (Requirements MD 31.2 "History画面は原則READ ONLY").
 */
@Service
public class OrderHistoryService {

    /** Phase 8-J 3章/4章: same clamp convention as ArrivalService/
     * WarehouseStockService/StockSalesService - a hard ceiling so an
     * unbounded ?size= can never force an effectively-unpaginated fetch. */
    static final int MAX_PAGE_SIZE = 100;
    static final int DEFAULT_PAGE_SIZE = 20;

    private final PortalOrderRepository portalOrderRepository;
    private final SupplierResponseRepository supplierResponseRepository;
    private final OrderAttentionRepository orderAttentionRepository;
    private final AuditEventRepository auditEventRepository;
    private final PortalUserRepository portalUserRepository;
    private final ManufacturerChannelResolutionService channelResolutionService;
    private final LegacyStockReadRepository legacyStockReadRepository;
    private final SupplierRegionClassificationResolutionService regionClassificationResolutionService;
    private final OfficialPoIntegrationRequestRepository officialPoIntegrationRequestRepository;
    private final SkuRestockExpectationService restockExpectationService;

    public OrderHistoryService(PortalOrderRepository portalOrderRepository,
                                SupplierResponseRepository supplierResponseRepository,
                                OrderAttentionRepository orderAttentionRepository,
                                AuditEventRepository auditEventRepository,
                                PortalUserRepository portalUserRepository,
                                ManufacturerChannelResolutionService channelResolutionService,
                                LegacyStockReadRepository legacyStockReadRepository,
                                SupplierRegionClassificationResolutionService regionClassificationResolutionService,
                                OfficialPoIntegrationRequestRepository officialPoIntegrationRequestRepository,
                                SkuRestockExpectationService restockExpectationService) {
        this.portalOrderRepository = portalOrderRepository;
        this.supplierResponseRepository = supplierResponseRepository;
        this.orderAttentionRepository = orderAttentionRepository;
        this.auditEventRepository = auditEventRepository;
        this.portalUserRepository = portalUserRepository;
        this.channelResolutionService = channelResolutionService;
        this.legacyStockReadRepository = legacyStockReadRepository;
        this.regionClassificationResolutionService = regionClassificationResolutionService;
        this.officialPoIntegrationRequestRepository = officialPoIntegrationRequestRepository;
        this.restockExpectationService = restockExpectationService;
    }

    /**
     * Phase 7-H (Order List search/filter audit): orderNoKeyword/itemKeyword/
     * updatedFrom/updatedTo are the SAME Backend query params as
     * supplierCode/brandCode/status (never a Frontend-only display Filter
     * over an unfiltered fetch).
     *
     * Phase 8-J 3章/4章: the previously-documented `findAll()`+in-JVM Stream
     * filter (Section 9 audit finding, explicitly flagged as deferred
     * technical debt at the time) is fixed here - every Filter below,
     * including hasAttentionOnly, is now a DB-pushed-down WHERE/EXISTS
     * predicate via {@link Specification}, and the result itself is
     * DB-level LIMIT/OFFSET paginated (Pageable), not fetched-then-sliced.
     * No filter's MEANING changed: each predicate is a direct SQL
     * translation of the exact same condition the removed Stream lambda
     * expressed (see git history for the pre-8-J version) - this is a
     * Query-level rewrite, not a Business Rule change.
     */
    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public PageResponse<OrderHistorySummaryResponse> list(String supplierCode, String brandCode, String status,
                                                    String orderNoKeyword, String itemKeyword,
                                                    LocalDate updatedFrom, LocalDate updatedTo,
                                                    Boolean hasAttentionOnly, Integer page, Integer size) {
        String normalizedOrderNoKeyword = normalizeKeyword(orderNoKeyword);
        String normalizedItemKeyword = normalizeKeyword(itemKeyword);
        Specification<PortalOrder> spec = buildSpecification(supplierCode, brandCode, status,
                normalizedOrderNoKeyword, normalizedItemKeyword, updatedFrom, updatedTo, hasAttentionOnly);

        int clampedSize = clampSize(size);
        int clampedPage = page == null || page < 0 ? 0 : page;
        Pageable pageable = PageRequest.of(clampedPage, clampedSize, Sort.by(Sort.Direction.DESC, "updatedAt"));

        Page<PortalOrder> result = portalOrderRepository.findAll(spec, pageable);
        List<OrderHistorySummaryResponse> content = result.getContent().stream().map(this::toSummary).toList();
        return PageResponse.of(content, clampedPage, clampedSize, result.getTotalElements());
    }

    private static int clampSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /** Builds every List Filter as one composed {@link Specification} so the
     * DB does the filtering (WHERE/EXISTS), not a Java Stream over the whole
     * table. Each predicate here is a direct, unmodified translation of the
     * pre-8-J Stream lambda it replaces - see the field-level Javadoc below
     * for the provenance of each one. */
    private static Specification<PortalOrder> buildSpecification(String supplierCode, String brandCode, String status,
            String normalizedOrderNoKeyword, String normalizedItemKeyword,
            LocalDate updatedFrom, LocalDate updatedTo, Boolean hasAttentionOnly) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (supplierCode != null && !supplierCode.isBlank()) {
                predicates.add(cb.equal(root.get("supplierCode"), supplierCode));
            }
            if (brandCode != null && !brandCode.isBlank()) {
                predicates.add(cb.equal(root.get("brandCode"), brandCode));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            // Matches Draft No., Portal管理番号 (prototypePoNo), or 正式PO番号
            // (officialPoNo) - same "either number the user might be
            // holding" convention as the pre-8-J matchesOrderNoKeyword();
            // Phase 1 Final Cleanup (Order History Number Model Audit)
            // extends this to officialPoNo, which was previously
            // unsearchable even though it's the number Excel/PDF/
            // Manufacturer Send actually use.
            if (normalizedOrderNoKeyword != null) {
                String pattern = "%" + normalizedOrderNoKeyword + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("draftNo")), pattern),
                        cb.like(cb.lower(root.get("prototypePoNo")), pattern),
                        cb.like(cb.lower(root.get("officialPoNo")), pattern)));
            }
            // Matches SKU or item name on any non-removed line - same
            // "!d.isRemoved()" convention as detail()/toLineView() and the
            // pre-8-J matchesItemKeyword(). EXISTS avoids duplicate parent
            // rows a plain JOIN would produce for an Order with multiple
            // matching lines.
            if (normalizedItemKeyword != null) {
                String pattern = "%" + normalizedItemKeyword + "%";
                Subquery<Long> sub = query.subquery(Long.class);
                var detailRoot = sub.from(PortalOrderDetail.class);
                sub.select(detailRoot.get("id"));
                sub.where(cb.and(
                        cb.equal(detailRoot.get("portalOrder"), root),
                        cb.isFalse(detailRoot.get("removed")),
                        cb.or(
                                cb.like(cb.lower(detailRoot.get("sku")), pattern),
                                cb.like(cb.lower(detailRoot.get("itemNameSnapshot")), pattern))));
                predicates.add(cb.exists(sub));
            }
            // Same "updatedAt's local DATE, updatedTo inclusive of the whole
            // day" semantics as the pre-8-J matchesUpdatedRange() - the
            // range boundary is built in the JVM's own zone (the same zone
            // every setUpdatedAt(OffsetDateTime.now()) call already uses
            // throughout this codebase), so the DB range comparison lands on
            // the identical calendar day the removed toLocalDate() call did.
            ZoneId zone = ZoneId.systemDefault();
            if (updatedFrom != null) {
                OffsetDateTime from = updatedFrom.atStartOfDay(zone).toOffsetDateTime();
                predicates.add(cb.greaterThanOrEqualTo(root.get("updatedAt"), from));
            }
            if (updatedTo != null) {
                OffsetDateTime toExclusive = updatedTo.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
                predicates.add(cb.lessThan(root.get("updatedAt"), toExclusive));
            }
            // Dashboard-parity hasAttentionOnly (Phase 6-D) - previously a
            // Frontend-only display filter over the full fetched set;
            // Phase 8-J 3章 moves it to the SAME EXISTS-against-order_attention
            // condition OrderAttentionRepository.findByPortalOrderIdAndActiveTrue
            // already expresses, so it now composes correctly with Pagination
            // instead of only filtering whatever happened to be on the
            // current page.
            if (Boolean.TRUE.equals(hasAttentionOnly)) {
                Subquery<Long> sub = query.subquery(Long.class);
                var attentionRoot = sub.from(OrderAttention.class);
                sub.select(attentionRoot.get("id"));
                sub.where(cb.and(
                        cb.equal(attentionRoot.get("portalOrderId"), root.get("id")),
                        cb.isTrue(attentionRoot.get("active"))));
                predicates.add(cb.exists(sub));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String normalizeKeyword(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
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

        // Gap Analysis B-1 (docs/gulliver-20260917-phase1-gap-analysis.md 5章):
        // one bulk Legacy READ ONLY lookup for every line's SKU, keyed by
        // Item Code - same LegacyStockReadRepository.findBySkus source
        // SKU Detail/Candidate List already use (Fact, no new calculation).
        // A SKU absent from the result (e.g. since removed from Legacy
        // Master) simply has no entry - toLineView leaves those fields null.
        java.util.Set<String> skus = order.getDetails().stream()
                .filter(d -> !d.isRemoved())
                .map(PortalOrderDetail::getSku)
                .collect(Collectors.toSet());
        Map<String, LegacyStockRow> stockBySku = skus.isEmpty() ? Map.of()
                : legacyStockReadRepository.findBySkus(skus).stream()
                        .collect(Collectors.toMap(LegacyStockRow::itemCd, r -> r, (a, b) -> a));
        // Post-Freeze Business Refinement (re-audit doc §10-4): same bulk,
        // no-N+1 lookup pattern as stockBySku above.
        Map<String, SkuRestockExpectationResponse> restockBySku = restockExpectationService.getBulk(skus);

        List<OrderHistoryDetailLineView> lines = order.getDetails().stream()
                .filter(d -> !d.isRemoved())
                .map(d -> toLineView(order, d, confirmedByDetailId.get(d.getId()), active, stockBySku.get(d.getSku()), restockBySku.get(d.getSku())))
                .toList();

        List<AttentionSummary> orderAttentions = active.stream()
                .filter(a -> a.getPortalOrderDetailId() == null)
                .map(a -> new AttentionSummary(a.getId(), a.getAttentionType()))
                .toList();

        String resolvedChannel = channelResolutionService.resolve(order.getSupplierCode(), order.getBrandCode());
        String resolvedRegionClassification = regionClassificationResolutionService.resolve(order.getSupplierCode(), order.getBrandCode());
        String ediCompletedByDisplayName = order.getEdiCompletedBy() == null ? null
                : portalUserRepository.findByUsername(order.getEdiCompletedBy()).map(PortalUser::getDisplayName).orElse(null);

        return new OrderHistoryDetailResponse(
                order.getId(), order.getDraftNo(), order.getPrototypePoNo(),
                order.getSupplierCode(), order.getSupplierNameSnapshot(),
                order.getBrandCode(), order.getBrandNameSnapshot(),
                order.getOrderDate(), order.getRequestedDelivery(), order.getCurrency(), order.getRemark(),
                order.getStatus(), order.getTotalQty(), order.getTotalAmount(),
                lines, orderAttentions, order.getCommunicationChannel(),
                resolvedChannel, order.getEdiStatus(), order.getEdiCompletedBy(), ediCompletedByDisplayName,
                order.getEdiCompletedAt(), resolvedRegionClassification
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
        // Phase 1 Final Cleanup (Order History Number Model Audit):
        // PortalOrder.currentRevisionNo is NOT the same value as of this
        // Phase - it's only set by demoSend (Email path), while the Official
        // PO Integration Request's own revisionNo is set immediately at
        // Request creation, independent of any Send. Order Detail's
        // "Revision" display already reads from OfficialPoIntegrationRequest
        // (via a separate API call) for exactly this reason - History must
        // read the SAME source, not PortalOrder.currentRevisionNo, to avoid
        // showing a false "—" for an Order that already has a Revision.
        Optional<OfficialPoIntegrationRequest> currentIntegration = officialPoIntegrationRequestRepository
                .findFirstByPortalOrderIdOrderByRevisionNoDesc(order.getId());
        Integer revisionNo = currentIntegration.map(OfficialPoIntegrationRequest::getRevisionNo).orElse(null);
        // Post-Freeze Visual Walkthrough Findings Fix (Finding #4): same
        // lookup as revisionNo above - see OrderHistorySummaryResponse's own
        // Javadoc on lifecycleStatus for why this is read separately from
        // (and never overwrites) order.getStatus().
        String lifecycleStatus = currentIntegration.map(OfficialPoIntegrationRequest::getLifecycleStatus).orElse(null);
        return new OrderHistorySummaryResponse(
                order.getId(), order.getDraftNo(), order.getPrototypePoNo(),
                order.getOfficialPoNo(), revisionNo, lifecycleStatus, order.getOrderDate(),
                order.getSupplierCode(), order.getSupplierNameSnapshot(), order.getBrandCode(), order.getBrandNameSnapshot(),
                (int) skuCount, order.getTotalQty(), order.getTotalAmount(), order.getStatus(), activeTypes, order.getUpdatedAt()
        );
    }

    private static OrderHistoryDetailLineView toLineView(PortalOrder order, PortalOrderDetail detail,
                                                           SupplierResponseDetail response, List<OrderAttention> active,
                                                           LegacyStockRow stock, SkuRestockExpectationResponse restock) {
        List<AttentionSummary> attentions = active.stream()
                .filter(a -> detail.getId().equals(a.getPortalOrderDetailId()))
                .map(a -> new AttentionSummary(a.getId(), a.getAttentionType()))
                .toList();
        return new OrderHistoryDetailLineView(
                detail.getSku(), detail.getItemNameSnapshot(), detail.getRecommendedQty(), detail.getOrderQty(),
                response == null ? null : response.getConfirmedQty(),
                response == null ? order.getRequestedDelivery() : response.getRequestedDelivery(),
                response == null ? null : response.getConfirmedDelivery(),
                attentions,
                stock == null ? null : stock.currentStock(),
                stock == null ? null : stock.monthlySales(),
                stock == null ? null : stock.leadTime(),
                stock == null ? null : stock.openArrival(),
                stock == null ? null : stock.itemStatus(),
                stock == null ? null : stock.discon(),
                stock == null ? null : stock.openPo(),
                restock == null ? SkuRestockExpectationResponse.SOURCE_NONE : restock.source(),
                restock == null ? null : restock.date(),
                restock == null ? null : restock.stockoutStatus(),
                restock == null ? null : restock.shortageQty(),
                restock == null ? null : restock.informationReceivedDate(),
                restock == null ? null : restock.contactMethod(),
                restock != null && restock.hasConflict()
        );
    }
}
