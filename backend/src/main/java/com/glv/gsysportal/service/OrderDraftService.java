package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.PortalOrderDetail;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.request.UpdateDraftRequest;
import com.glv.gsysportal.dto.response.OrderDraftDetailResponse;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.exception.EmptySkuListException;
import com.glv.gsysportal.exception.MixedSupplierException;
import com.glv.gsysportal.exception.SkuNotFoundException;
import com.glv.gsysportal.repository.legacy.LegacyStockReadRepository;
import com.glv.gsysportal.repository.legacy.row.LegacyStockRow;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Create Draft orchestrator (implementation instructions 4章/6章):
 *  Step A - Legacy READ ONLY re-fetch (own transaction, via
 *           {@link LegacyStockReadRepository}, never trusting Frontend values)
 *  Step B - Prototype write (own transaction, via
 *           {@link OrderDraftPersistenceService})
 * No distributed transaction spans both.
 */
@Service
public class OrderDraftService {

    private final LegacyStockReadRepository legacyStockReadRepository;
    private final OrderDraftPersistenceService persistenceService;
    private final PortalOrderRepository portalOrderRepository;

    public OrderDraftService(LegacyStockReadRepository legacyStockReadRepository,
                              OrderDraftPersistenceService persistenceService,
                              PortalOrderRepository portalOrderRepository) {
        this.legacyStockReadRepository = legacyStockReadRepository;
        this.persistenceService = persistenceService;
        this.portalOrderRepository = portalOrderRepository;
    }

    public OrderDraftResponse createDraft(CreateDraftRequest request, String performedBy) {
        Set<String> requestedSkus = new LinkedHashSet<>(request.skus());
        if (requestedSkus.isEmpty()) {
            throw new EmptySkuListException();
        }

        // Step A: Legacy READ ONLY, own transaction (LegacyStockReadRepository.findBySkus
        // is itself @Transactional(readOnly=true, transactionManager="legacyTransactionManager"),
        // fully completes and closes before this method proceeds any further).
        List<LegacyStockRow> legacyRows = legacyStockReadRepository.findBySkus(requestedSkus);

        Set<String> foundSkus = legacyRows.stream().map(LegacyStockRow::itemCd).collect(java.util.stream.Collectors.toSet());
        Set<String> missing = new LinkedHashSet<>(requestedSkus);
        missing.removeAll(foundSkus);
        if (!missing.isEmpty()) {
            throw new SkuNotFoundException(missing);
        }

        Set<String> supplierCodes = legacyRows.stream()
                .map(LegacyStockRow::supplierCd)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (supplierCodes.size() > 1) {
            throw new MixedSupplierException(supplierCodes);
        }

        // Step B: Prototype write, own transaction.
        PortalOrder saved = persistenceService.create(request, legacyRows, performedBy);
        return toResponse(saved);
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public OrderDraftResponse getDraft(Long id) {
        PortalOrder order = portalOrderRepository.findById(id).orElseThrow(() -> new DraftNotFoundException(id));
        return toResponse(order);
    }

    public OrderDraftResponse updateDraft(Long id, UpdateDraftRequest request, String performedBy) {
        PortalOrder saved = persistenceService.update(id, request, performedBy);
        return toResponse(saved);
    }

    static OrderDraftResponse toResponse(PortalOrder order) {
        List<OrderDraftDetailResponse> details = order.getDetails().stream()
                .filter(d -> !d.isRemoved())
                .map(OrderDraftService::toDetailResponse)
                .toList();

        List<String> headerWarnings = details.stream()
                .flatMap(d -> d.warningCodes().stream())
                .distinct()
                .toList();

        return new OrderDraftResponse(
                order.getId(),
                order.getDraftNo(),
                order.getPrototypePoNo(),
                order.getSupplierCode(),
                order.getSupplierNameSnapshot(),
                order.getBrandCode(),
                order.getBrandNameSnapshot(),
                order.getOrderDate(),
                order.getRequestedDelivery(),
                order.getCurrency(),
                order.getStatus(),
                order.getRemark(),
                order.getTotalQty(),
                order.getTotalAmount(),
                order.getDataSource(),
                order.getCreatedBy(),
                order.getCreatedAt(),
                order.getUpdatedBy(),
                order.getUpdatedAt(),
                details,
                headerWarnings
        );
    }

    private static OrderDraftDetailResponse toDetailResponse(PortalOrderDetail d) {
        return new OrderDraftDetailResponse(
                d.getId(), d.getSku(), d.getItemNameSnapshot(), d.getRecommendedQty(), d.getOrderQty(),
                d.getUnitPrice(), d.getAmount(), d.getCurrentStockSnapshot(), d.getSafetyStockSnapshot(),
                d.getOpenPoSnapshot(), d.getRecentSalesSnapshot(), d.getLeadTimeSnapshot(), d.getItemStatusSnapshot(),
                d.getDataSource(), warningCodes(d.getRecommendedQty(), d.getOrderQty())
        );
    }

    /**
     * Requirements MD 13章 / Phase 0.5 34章: ±50% deviation from Recommended
     * Qty is a Warning, never a hard Error - the response always includes
     * this so the Frontend can display it without a separate call, and it is
     * recomputed fresh on every read (not stored) so it always reflects the
     * current order_qty/recommended_qty pair.
     */
    static List<String> warningCodes(int recommendedQty, int orderQty) {
        boolean significant;
        if (recommendedQty > 0) {
            double ratio = orderQty / (double) recommendedQty;
            significant = ratio < 0.5 || ratio > 1.5;
        } else {
            significant = orderQty > 0;
        }
        return significant ? List.of("ORDER_QTY_DIFFERS_SIGNIFICANTLY") : List.of();
    }
}
