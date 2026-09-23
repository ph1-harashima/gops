package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.DashboardAggregateCurrent;
import com.glv.gsysportal.domain.DashboardBrandLegacyAggregate;
import com.glv.gsysportal.domain.DashboardLegacyAggregate;
import com.glv.gsysportal.domain.FollowUpCase;
import com.glv.gsysportal.domain.OfficialPoIntegrationRequest;
import com.glv.gsysportal.domain.OrderAttention;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.PriceChangeSet;
import com.glv.gsysportal.dto.response.DashboardBrandRow;
import com.glv.gsysportal.dto.response.DashboardResponse;
import com.glv.gsysportal.repository.prototype.DashboardAggregateCurrentRepository;
import com.glv.gsysportal.repository.prototype.DashboardBrandLegacyAggregateRepository;
import com.glv.gsysportal.repository.prototype.DashboardLegacyAggregateRepository;
import com.glv.gsysportal.repository.prototype.FollowUpCaseRepository;
import com.glv.gsysportal.repository.prototype.OfficialPoIntegrationRequestRepository;
import com.glv.gsysportal.repository.prototype.OrderAttentionRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import com.glv.gsysportal.repository.prototype.PriceChangeSetRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * GET /api/dashboard (implementation instructions Step 5 3章). An Action /
 * Operation Cockpit, not Analytics: every number here answers "what needs a
 * decision right now", never a trend/margin/turnover figure - none of those
 * exist anywhere in this class or its Response DTO.
 *
 * [PROTOTYPE DECISION] 欠品/長期欠品/発注停止の正式な業務定義は
 * Requirements MD 27.4の [TBD - CUSTOMER REVIEW] のまま未確定である。本Stepでは
 * 既存データのみから導出できる最小限のProxyを暫定的に採用する：
 * 欠品 = currentStock == 0、長期欠品 = currentStock == 0 かつ openPo == 0
 * （入荷予定も無い状態）。日数等の期間ベース定義は、そのようなデータが
 * Prototype側に存在しないため採用していない。
 *
 * <p>Stage 5K (docs/real-data-audit/gops-stage5k-dashboard-read-model-implementation.md):
 * {@code candidateCount}/{@code outOfStockCount}/{@code
 * longTermOutOfStockCount} (overall and per-Brand) no longer run {@code
 * calc4} here at all - this method now only reads the Portal DB Read
 * Model ({@code dashboard_aggregate_current} -> {@code
 * dashboard_legacy_aggregate}/{@code dashboard_brand_legacy_aggregate}),
 * populated by {@link DashboardRefreshService} on a schedule
 * ({@code DashboardRefreshScheduler}) or on demand (Manual Refresh). The
 * previous live-computation logic (Stage 5E RC-A's lean
 * {@code findDashboardCandidateInputs}/{@code calc4} path) moved to
 * {@link DashboardRefreshService} verbatim - candidateCount's DEFINITION
 * (recommendedQty > 0, via the exact same calc4 this codebase has always
 * used) is unchanged, only WHEN it is computed changed (Stage 5J §1
 * Executive Summary). Portal-derived KPIs (draftCount et al.) are
 * deliberately UNCHANGED - still a live, request-time read (Stage 5J §4:
 * Portal's own tables are small enough that this stays simpler and
 * strictly more correct than Read-Modeling them too).
 */
@Service
public class DashboardService {

    private final DashboardAggregateCurrentRepository aggregateCurrentRepository;
    private final DashboardLegacyAggregateRepository legacyAggregateRepository;
    private final DashboardBrandLegacyAggregateRepository brandLegacyAggregateRepository;
    private final PortalOrderRepository portalOrderRepository;
    private final OrderAttentionRepository orderAttentionRepository;
    private final FollowUpCaseRepository followUpCaseRepository;
    private final PriceChangeSetRepository priceChangeSetRepository;
    private final OfficialPoIntegrationRequestRepository integrationRequestRepository;

    public DashboardService(DashboardAggregateCurrentRepository aggregateCurrentRepository,
                             DashboardLegacyAggregateRepository legacyAggregateRepository,
                             DashboardBrandLegacyAggregateRepository brandLegacyAggregateRepository,
                             PortalOrderRepository portalOrderRepository,
                             OrderAttentionRepository orderAttentionRepository,
                             FollowUpCaseRepository followUpCaseRepository,
                             PriceChangeSetRepository priceChangeSetRepository,
                             OfficialPoIntegrationRequestRepository integrationRequestRepository) {
        this.aggregateCurrentRepository = aggregateCurrentRepository;
        this.legacyAggregateRepository = legacyAggregateRepository;
        this.brandLegacyAggregateRepository = brandLegacyAggregateRepository;
        this.portalOrderRepository = portalOrderRepository;
        this.orderAttentionRepository = orderAttentionRepository;
        this.followUpCaseRepository = followUpCaseRepository;
        this.priceChangeSetRepository = priceChangeSetRepository;
        this.integrationRequestRepository = integrationRequestRepository;
    }

    // Stage 5E Targeted Remediation (RC-F) / Stage 5K: still deliberately
    // NOT @Transactional here - the Read Model reads below are now all
    // small, indexed, Portal-DB-only SELECTs (no Legacy work at all left
    // in this method, Stage 5J §12), but each Portal repository call still
    // opens/commits its own short transaction independently rather than
    // sharing one held open for this method's whole body.
    public DashboardResponse getDashboard() {
        Optional<DashboardAggregateCurrent> current = aggregateCurrentRepository.findBySingleton(Boolean.TRUE);
        List<PortalOrder> orders = portalOrderRepository.findAll();
        Set<Long> orderIdsWithActiveAttention = orderAttentionRepository.findByActiveTrue().stream()
                .map(OrderAttention::getPortalOrderId)
                .collect(Collectors.toSet());
        int draftCount = (int) orders.stream().filter(o -> PortalOrder.STATUS_DRAFT.equals(o.getStatus())).count();
        // Phase 7-C1 14章: ADMIN's approval queue entry point.
        int pendingApprovalCount = (int) orders.stream().filter(o -> PortalOrder.STATUS_PENDING_APPROVAL.equals(o.getStatus())).count();
        int awaitingSupplierCount = (int) orders.stream().filter(o -> PortalOrder.STATUS_AWAITING_SUPPLIER.equals(o.getStatus())).count();
        int attentionCount = (int) orders.stream().filter(o -> orderIdsWithActiveAttention.contains(o.getId())).count();
        int openFollowUpCaseCount = (int) followUpCaseRepository.countByStatusIn(
                List.of(FollowUpCase.STATUS_OPEN, FollowUpCase.STATUS_INQUIRY_PREPARED));
        // Phase 8-J 11章/13章: Price Change Draft count - see DashboardResponse Javadoc.
        int priceChangeDraftCount = (int) priceChangeSetRepository.countByStatus(PriceChangeSet.STATUS_DRAFT);

        // Phase D (Signature axis KPIs, see DashboardResponse Javadoc): the
        // CURRENT (latest revision) Integration Request per Order - same
        // "small Portal table, reduce in Java" precedent as orders/
        // orderIdsWithActiveAttention above, never a bulk SQL GROUP BY.
        Map<Long, OfficialPoIntegrationRequest> currentIntegrationRequestByOrderId = integrationRequestRepository.findAll().stream()
                .collect(Collectors.toMap(OfficialPoIntegrationRequest::getPortalOrderId, r -> r,
                        (a, b) -> a.getRevisionNo() >= b.getRevisionNo() ? a : b));
        int signaturePendingCount = (int) orders.stream()
                .filter(o -> {
                    OfficialPoIntegrationRequest r = currentIntegrationRequestByOrderId.get(o.getId());
                    return r != null && OfficialPoIntegrationRequest.SIGNATURE_PENDING.equals(r.getSignatureStatus());
                }).count();
        int readyToSendCount = (int) orders.stream()
                .filter(o -> PortalOrder.STATUS_APPROVED.equals(o.getStatus()))
                .filter(o -> {
                    OfficialPoIntegrationRequest r = currentIntegrationRequestByOrderId.get(o.getId());
                    return r != null && r.isReadyToSend();
                }).count();

        if (current.isEmpty()) {
            // Stage 5J §15 Startup/Empty State: only reachable if the
            // one-time startup Refresh (DashboardRefreshStartupRunner)
            // itself failed and no later scheduled/manual Refresh has
            // succeeded since - never a fabricated 0 candidateCount
            // presented as real (Stage 5K §16 "no synchronous 13-second
            // fallback" is about the request thread never computing
            // calc4 itself - it does NOT mean silently faking a value).
            List<DashboardBrandRow> portalOnlyBrands = buildBrandRows(
                    List.of(), orders, orderIdsWithActiveAttention);
            return new DashboardResponse(null, false, 0, 0, 0, draftCount, pendingApprovalCount,
                    awaitingSupplierCount, attentionCount, openFollowUpCaseCount, priceChangeDraftCount,
                    signaturePendingCount, readyToSendCount, portalOnlyBrands);
        }

        Long refreshRunId = current.get().getActiveRefreshRunId();
        DashboardLegacyAggregate overall = legacyAggregateRepository.findById(refreshRunId).orElseThrow(
                () -> new IllegalStateException("dashboard_aggregate_current points at refresh_run_id=" + refreshRunId
                        + " but no dashboard_legacy_aggregate row exists for it"));
        List<DashboardBrandLegacyAggregate> brandAggregates = brandLegacyAggregateRepository.findByRefreshRunId(refreshRunId);

        List<DashboardBrandRow> brands = buildBrandRows(brandAggregates, orders, orderIdsWithActiveAttention);

        return new DashboardResponse(
                current.get().getActivatedAt(), true,
                overall.getCandidateCount(), overall.getOutOfStockCount(), overall.getLongTermOutOfStockCount(),
                draftCount, pendingApprovalCount, awaitingSupplierCount, attentionCount, openFollowUpCaseCount,
                priceChangeDraftCount, signaturePendingCount, readyToSendCount, brands
        );
    }

    private static List<DashboardBrandRow> buildBrandRows(List<DashboardBrandLegacyAggregate> legacyBrandAggregates,
                                                            List<PortalOrder> orders,
                                                            Set<Long> orderIdsWithActiveAttention) {
        // LinkedHashMap to keep a stable, deterministic Brand order (first-seen,
        // Legacy aggregate order first, then any Portal-only Brand codes).
        Map<String, String> brandNameByCode = new LinkedHashMap<>();
        for (DashboardBrandLegacyAggregate row : legacyBrandAggregates) {
            brandNameByCode.putIfAbsent(row.getBrandCode(),
                    row.getBrandName() != null ? row.getBrandName() : row.getBrandCode());
        }
        for (PortalOrder o : orders) {
            if (o.getBrandCode() != null) {
                brandNameByCode.putIfAbsent(o.getBrandCode(), o.getBrandNameSnapshot() != null ? o.getBrandNameSnapshot() : o.getBrandCode());
            }
        }

        Map<String, DashboardBrandLegacyAggregate> legacyByBrand = legacyBrandAggregates.stream()
                .collect(Collectors.toMap(DashboardBrandLegacyAggregate::getBrandCode, r -> r, (a, b) -> a));

        return brandNameByCode.entrySet().stream()
                .map(entry -> {
                    String brandCode = entry.getKey();
                    DashboardBrandLegacyAggregate legacy = legacyByBrand.get(brandCode);
                    int candidateCount = legacy == null ? 0 : legacy.getCandidateCount();
                    int outOfStockCount = legacy == null ? 0 : legacy.getOutOfStockCount();
                    int longTermOutOfStockCount = legacy == null ? 0 : legacy.getLongTermOutOfStockCount();
                    int draftCount = (int) orders.stream()
                            .filter(o -> brandCode.equals(o.getBrandCode()) && PortalOrder.STATUS_DRAFT.equals(o.getStatus())).count();
                    int awaitingSupplierCount = (int) orders.stream()
                            .filter(o -> brandCode.equals(o.getBrandCode()) && PortalOrder.STATUS_AWAITING_SUPPLIER.equals(o.getStatus())).count();
                    int attentionCount = (int) orders.stream()
                            .filter(o -> brandCode.equals(o.getBrandCode()) && orderIdsWithActiveAttention.contains(o.getId())).count();
                    return new DashboardBrandRow(brandCode, entry.getValue(), candidateCount, outOfStockCount,
                            longTermOutOfStockCount, draftCount, awaitingSupplierCount, attentionCount);
                })
                .toList();
    }
}
