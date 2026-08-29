package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.FollowUpCase;
import com.glv.gsysportal.domain.OrderAttention;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.response.DashboardBrandRow;
import com.glv.gsysportal.dto.response.DashboardResponse;
import com.glv.gsysportal.dto.response.OrderCandidateResponse;
import com.glv.gsysportal.repository.prototype.FollowUpCaseRepository;
import com.glv.gsysportal.repository.prototype.OrderAttentionRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 */
@Service
public class DashboardService {

    private final OrderCandidateService orderCandidateService;
    private final PortalOrderRepository portalOrderRepository;
    private final OrderAttentionRepository orderAttentionRepository;
    private final FollowUpCaseRepository followUpCaseRepository;

    public DashboardService(OrderCandidateService orderCandidateService,
                             PortalOrderRepository portalOrderRepository,
                             OrderAttentionRepository orderAttentionRepository,
                             FollowUpCaseRepository followUpCaseRepository) {
        this.orderCandidateService = orderCandidateService;
        this.portalOrderRepository = portalOrderRepository;
        this.orderAttentionRepository = orderAttentionRepository;
        this.followUpCaseRepository = followUpCaseRepository;
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public DashboardResponse getDashboard() {
        List<OrderCandidateResponse> candidates = orderCandidateService.findOrderCandidates(null, null, null);
        List<PortalOrder> orders = portalOrderRepository.findAll();
        Set<Long> orderIdsWithActiveAttention = orderAttentionRepository.findByActiveTrue().stream()
                .map(OrderAttention::getPortalOrderId)
                .collect(Collectors.toSet());

        int candidateCount = (int) candidates.stream().filter(DashboardService::isCandidate).count();
        int outOfStockCount = (int) candidates.stream().filter(DashboardService::isOutOfStock).count();
        int longTermOutOfStockCount = (int) candidates.stream().filter(DashboardService::isLongTermOutOfStock).count();
        int draftCount = (int) orders.stream().filter(o -> PortalOrder.STATUS_DRAFT.equals(o.getStatus())).count();
        // Phase 7-C1 14章: ADMIN's approval queue entry point.
        int pendingApprovalCount = (int) orders.stream().filter(o -> PortalOrder.STATUS_PENDING_APPROVAL.equals(o.getStatus())).count();
        int awaitingSupplierCount = (int) orders.stream().filter(o -> PortalOrder.STATUS_AWAITING_SUPPLIER.equals(o.getStatus())).count();
        int attentionCount = (int) orders.stream().filter(o -> orderIdsWithActiveAttention.contains(o.getId())).count();
        int openFollowUpCaseCount = (int) followUpCaseRepository.countByStatusIn(
                List.of(FollowUpCase.STATUS_OPEN, FollowUpCase.STATUS_INQUIRY_PREPARED));

        List<DashboardBrandRow> brands = buildBrandRows(candidates, orders, orderIdsWithActiveAttention);

        return new DashboardResponse(
                candidateCount, outOfStockCount, longTermOutOfStockCount,
                draftCount, pendingApprovalCount, awaitingSupplierCount, attentionCount, openFollowUpCaseCount, brands
        );
    }

    private static List<DashboardBrandRow> buildBrandRows(List<OrderCandidateResponse> candidates, List<PortalOrder> orders,
                                                            Set<Long> orderIdsWithActiveAttention) {
        // LinkedHashMap to keep a stable, deterministic Brand order (first-seen in the Candidate list).
        Map<String, String> brandNameByCode = new LinkedHashMap<>();
        for (OrderCandidateResponse c : candidates) {
            if (c.brandCode() != null) {
                brandNameByCode.putIfAbsent(c.brandCode(), c.brandName() != null ? c.brandName() : c.brandCode());
            }
        }
        for (PortalOrder o : orders) {
            if (o.getBrandCode() != null) {
                brandNameByCode.putIfAbsent(o.getBrandCode(), o.getBrandNameSnapshot() != null ? o.getBrandNameSnapshot() : o.getBrandCode());
            }
        }

        return brandNameByCode.entrySet().stream()
                .map(entry -> {
                    String brandCode = entry.getKey();
                    int candidateCount = (int) candidates.stream()
                            .filter(c -> brandCode.equals(c.brandCode())).filter(DashboardService::isCandidate).count();
                    int outOfStockCount = (int) candidates.stream()
                            .filter(c -> brandCode.equals(c.brandCode())).filter(DashboardService::isOutOfStock).count();
                    int draftCount = (int) orders.stream()
                            .filter(o -> brandCode.equals(o.getBrandCode()) && PortalOrder.STATUS_DRAFT.equals(o.getStatus())).count();
                    int awaitingSupplierCount = (int) orders.stream()
                            .filter(o -> brandCode.equals(o.getBrandCode()) && PortalOrder.STATUS_AWAITING_SUPPLIER.equals(o.getStatus())).count();
                    int attentionCount = (int) orders.stream()
                            .filter(o -> brandCode.equals(o.getBrandCode()) && orderIdsWithActiveAttention.contains(o.getId())).count();
                    return new DashboardBrandRow(brandCode, entry.getValue(), candidateCount, outOfStockCount,
                            draftCount, awaitingSupplierCount, attentionCount);
                })
                .toList();
    }

    private static boolean isCandidate(OrderCandidateResponse c) {
        return c.recommendedQty() != null && c.recommendedQty() > 0;
    }

    private static boolean isOutOfStock(OrderCandidateResponse c) {
        return c.currentStock() != null && c.currentStock() == 0;
    }

    private static boolean isLongTermOutOfStock(OrderCandidateResponse c) {
        return isOutOfStock(c) && (c.openPo() == null || c.openPo() == 0);
    }
}
