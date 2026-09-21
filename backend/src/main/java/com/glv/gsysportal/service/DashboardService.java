package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.FollowUpCase;
import com.glv.gsysportal.domain.OrderAttention;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.PriceChangeSet;
import com.glv.gsysportal.dto.response.DashboardBrandRow;
import com.glv.gsysportal.dto.response.DashboardResponse;
import com.glv.gsysportal.repository.legacy.LegacyStockReadRepository;
import com.glv.gsysportal.repository.legacy.LegacyStockReadRepository.DashboardStockAggregateRow;
import com.glv.gsysportal.repository.legacy.row.LegacyStockRow;
import com.glv.gsysportal.repository.prototype.FollowUpCaseRepository;
import com.glv.gsysportal.repository.prototype.OrderAttentionRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import com.glv.gsysportal.repository.prototype.PriceChangeSetRepository;
import com.glv.gsysportal.service.SupplierRegionClassificationResolutionService.RegionClassificationLookup;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * <p>Stage 5E Targeted Remediation (RC-A, docs/real-data-audit/
 * gops-stage5e-targeted-remediation.md): previously fetched the ENTIRE
 * unpaginated candidate catalog via {@code OrderCandidateService
 * .findOrderCandidates(null,null,null)} and counted with Java {@code Stream}
 * filters - confirmed by Stage 5D as the primary cause of this endpoint's
 * real-Production-scale failure (351-407 seconds, frequent client-disconnect
 * errors). Stage 5D split the KPIs into two categories: outOfStockCount/
 * longTermOutOfStockCount are pure arithmetic on current_stock/open_po
 * ("Category A" - now a genuine SQL {@code GROUP BY} aggregate,
 * {@link LegacyStockReadRepository#findDashboardStockAggregateByBrand()});
 * candidateCount genuinely requires the full calc4 Recommended-Qty formula
 * chain ("Category B" - cannot be a SQL aggregate) but is now computed from
 * a lean query that omits the ms_comm brand/supplier NAME joins Stage 5D
 * identified as the dominant per-row cost
 * ({@link LegacyStockReadRepository#findDashboardCandidateInputs()}), with
 * region classification resolved via one bulk lookup
 * ({@link SupplierRegionClassificationResolutionService#loadAll()}) instead
 * of up to 4 Portal round-trips per item (Stage 5D's confirmed N+1).
 * candidateCount's own DEFINITION (recommendedQty > 0, via the exact same
 * calc4 this codebase has always used) is unchanged - only how it is
 * computed changed.
 */
@Service
public class DashboardService {

    private final LegacyStockReadRepository legacyStockReadRepository;
    private final RecommendedQtyCalculator recommendedQtyCalculator;
    private final SupplierRegionClassificationResolutionService regionResolutionService;
    private final PortalOrderRepository portalOrderRepository;
    private final OrderAttentionRepository orderAttentionRepository;
    private final FollowUpCaseRepository followUpCaseRepository;
    private final PriceChangeSetRepository priceChangeSetRepository;

    public DashboardService(LegacyStockReadRepository legacyStockReadRepository,
                             RecommendedQtyCalculator recommendedQtyCalculator,
                             SupplierRegionClassificationResolutionService regionResolutionService,
                             PortalOrderRepository portalOrderRepository,
                             OrderAttentionRepository orderAttentionRepository,
                             FollowUpCaseRepository followUpCaseRepository,
                             PriceChangeSetRepository priceChangeSetRepository) {
        this.legacyStockReadRepository = legacyStockReadRepository;
        this.recommendedQtyCalculator = recommendedQtyCalculator;
        this.regionResolutionService = regionResolutionService;
        this.portalOrderRepository = portalOrderRepository;
        this.orderAttentionRepository = orderAttentionRepository;
        this.followUpCaseRepository = followUpCaseRepository;
        this.priceChangeSetRepository = priceChangeSetRepository;
    }

    // Stage 5E Targeted Remediation (RC-F, docs/real-data-audit/
    // gops-stage5e-targeted-remediation.md): deliberately NOT
    // @Transactional here. Stage 5D confirmed this method-level annotation
    // held one Portal connection reserved for this method's ENTIRE body -
    // including the Legacy-side computation below, which can run for
    // seconds to minutes - starving the capped (max 5) Portal pool under
    // concurrent load. Each Portal repository call below (portalOrderRepository
    // .findAll(), etc.) still runs inside its own short, independent
    // transaction - Spring Data JPA's SimpleJpaRepository is itself
    // @Transactional(readOnly=true) per method when no broader transaction
    // is already active - so removing this outer annotation does not make
    // any individual Portal read non-transactional, it only stops those
    // reads from sharing one artificially long-lived connection with the
    // unrelated Legacy work.
    public DashboardResponse getDashboard() {
        List<DashboardStockAggregateRow> stockAggregateByBrand = legacyStockReadRepository.findDashboardStockAggregateByBrand();
        Map<String, Integer> candidateCountByBrand = computeCandidateCountByBrand();
        Map<String, String> brandNames = legacyStockReadRepository.findAllBrandNames();

        List<PortalOrder> orders = portalOrderRepository.findAll();
        Set<Long> orderIdsWithActiveAttention = orderAttentionRepository.findByActiveTrue().stream()
                .map(OrderAttention::getPortalOrderId)
                .collect(Collectors.toSet());

        int candidateCount = candidateCountByBrand.values().stream().mapToInt(Integer::intValue).sum();
        int outOfStockCount = stockAggregateByBrand.stream().mapToInt(DashboardStockAggregateRow::outOfStockCount).sum();
        int longTermOutOfStockCount = stockAggregateByBrand.stream().mapToInt(DashboardStockAggregateRow::longTermOutOfStockCount).sum();
        int draftCount = (int) orders.stream().filter(o -> PortalOrder.STATUS_DRAFT.equals(o.getStatus())).count();
        // Phase 7-C1 14章: ADMIN's approval queue entry point.
        int pendingApprovalCount = (int) orders.stream().filter(o -> PortalOrder.STATUS_PENDING_APPROVAL.equals(o.getStatus())).count();
        int awaitingSupplierCount = (int) orders.stream().filter(o -> PortalOrder.STATUS_AWAITING_SUPPLIER.equals(o.getStatus())).count();
        int attentionCount = (int) orders.stream().filter(o -> orderIdsWithActiveAttention.contains(o.getId())).count();
        int openFollowUpCaseCount = (int) followUpCaseRepository.countByStatusIn(
                List.of(FollowUpCase.STATUS_OPEN, FollowUpCase.STATUS_INQUIRY_PREPARED));
        // Phase 8-J 11章/13章: Price Change Draft count - see DashboardResponse Javadoc.
        int priceChangeDraftCount = (int) priceChangeSetRepository.countByStatus(PriceChangeSet.STATUS_DRAFT);

        List<DashboardBrandRow> brands = buildBrandRows(
                stockAggregateByBrand, candidateCountByBrand, brandNames, orders, orderIdsWithActiveAttention);

        return new DashboardResponse(
                candidateCount, outOfStockCount, longTermOutOfStockCount,
                draftCount, pendingApprovalCount, awaitingSupplierCount, attentionCount, openFollowUpCaseCount,
                priceChangeDraftCount, brands
        );
    }

    /** candidateCount, overall and per-Brand - see class Javadoc "Category B". */
    private Map<String, Integer> computeCandidateCountByBrand() {
        List<LegacyStockRow> rows = legacyStockReadRepository.findDashboardCandidateInputs();
        RegionClassificationLookup regionLookup = regionResolutionService.loadAll();
        Map<String, Integer> countByBrand = new HashMap<>();
        for (LegacyStockRow row : rows) {
            if (row.brandCd() == null) {
                continue;
            }
            Integer recommendedQty = recommendedQtyCalculator.calc4(row, regionLookup);
            if (recommendedQty != null && recommendedQty > 0) {
                countByBrand.merge(row.brandCd(), 1, Integer::sum);
            }
        }
        return countByBrand;
    }

    private static List<DashboardBrandRow> buildBrandRows(List<DashboardStockAggregateRow> stockAggregateByBrand,
                                                            Map<String, Integer> candidateCountByBrand,
                                                            Map<String, String> brandNames,
                                                            List<PortalOrder> orders,
                                                            Set<Long> orderIdsWithActiveAttention) {
        // LinkedHashMap to keep a stable, deterministic Brand order (first-seen,
        // stock-aggregate order first, then any Portal-only Brand codes).
        Map<String, String> brandNameByCode = new LinkedHashMap<>();
        for (DashboardStockAggregateRow row : stockAggregateByBrand) {
            if (row.brandCd() != null) {
                brandNameByCode.putIfAbsent(row.brandCd(), brandNames.getOrDefault(row.brandCd(), row.brandCd()));
            }
        }
        for (PortalOrder o : orders) {
            if (o.getBrandCode() != null) {
                brandNameByCode.putIfAbsent(o.getBrandCode(), o.getBrandNameSnapshot() != null ? o.getBrandNameSnapshot() : o.getBrandCode());
            }
        }

        Map<String, DashboardStockAggregateRow> stockByBrand = stockAggregateByBrand.stream()
                .collect(Collectors.toMap(DashboardStockAggregateRow::brandCd, r -> r, (a, b) -> a));

        return brandNameByCode.entrySet().stream()
                .map(entry -> {
                    String brandCode = entry.getKey();
                    DashboardStockAggregateRow stock = stockByBrand.get(brandCode);
                    int candidateCount = candidateCountByBrand.getOrDefault(brandCode, 0);
                    int outOfStockCount = stock == null ? 0 : stock.outOfStockCount();
                    int longTermOutOfStockCount = stock == null ? 0 : stock.longTermOutOfStockCount();
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
