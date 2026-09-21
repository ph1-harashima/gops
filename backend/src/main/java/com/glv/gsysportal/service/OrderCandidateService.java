package com.glv.gsysportal.service;

import com.glv.gsysportal.dto.response.OrderCandidateResponse;
import com.glv.gsysportal.dto.response.PageResponse;
import com.glv.gsysportal.dto.response.SkuRestockExpectationResponse;
import com.glv.gsysportal.repository.legacy.LegacyStockReadRepository;
import com.glv.gsysportal.repository.legacy.LegacyStockReadRepository.StockSalesListFilter;
import com.glv.gsysportal.repository.legacy.row.LegacyStockRow;
import com.glv.gsysportal.service.SupplierRegionClassificationResolutionService.RegionClassificationLookup;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Order Candidate List - Legacy Read only, no Prototype DB writes.
 *
 * Recommended Qty is calc4, computed via the verbatim-ported Legacy calc logic
 * (com.glv.gsysportal.legacy.calc) through {@link RecommendedQtyCalculator},
 * never a fixed/seeded value (Technical Design 4.3, implementation
 * instructions 4章/7章).
 */
@Service
public class OrderCandidateService {

    // Stage 4 Targeted Real-Data Remediation (docs/real-data-audit/
    // gops-stage4-targeted-real-data-remediation.md): matches
    // StockSalesService's own MAX/DEFAULT_PAGE_SIZE exactly - one shared
    // Candidate List / Stock-Sales List pagination contract, not two
    // independently-chosen ones.
    static final int MAX_PAGE_SIZE = 100;
    static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * All data in this Step is sourced from the Legacy Demo Instance
     * (see docker-compose.yml legacy-demo-mysql) rather than a shared/real
     * Legacy G-SYS database - surfaced to the Frontend so it is never confused
     * with production data (implementation instructions 0章/7章 data source
     * identification requirement).
     */
    public static final String DATA_SOURCE_CODE = "DEMO_LEGACY";

    private final LegacyStockReadRepository legacyStockReadRepository;
    private final RecommendedQtyCalculator recommendedQtyCalculator;
    private final SkuRestockExpectationService restockExpectationService;
    private final SupplierRegionClassificationResolutionService regionResolutionService;

    public OrderCandidateService(LegacyStockReadRepository legacyStockReadRepository,
                                  RecommendedQtyCalculator recommendedQtyCalculator,
                                  SkuRestockExpectationService restockExpectationService,
                                  SupplierRegionClassificationResolutionService regionResolutionService) {
        this.legacyStockReadRepository = legacyStockReadRepository;
        this.recommendedQtyCalculator = recommendedQtyCalculator;
        this.restockExpectationService = restockExpectationService;
        this.regionResolutionService = regionResolutionService;
    }

    /**
     * Full, unpaginated Order Candidate browse. As of Stage 5H (docs/real-data-audit/
     * gops-stage5h-systematic-performance-remediation.md, Dead Full-Catalog
     * Path Audit): when called with no filter at all
     * ({@code (null, null, null)}) this scans and calc4-evaluates the
     * entire active catalog (116,842 SKUs against the Production Snapshot)
     * - it has NO HTTP-reachable caller anymore ({@code DashboardService}
     * stopped calling it in Stage 5E's RC-A; {@code SupplierMasterService}
     * stopped calling it in Stage 5H's RC-H). Kept (not deleted) because it
     * is still a correct, independently useful "browse with an optional
     * filter, no pagination" primitive and a test
     * ({@code OrderCandidateServicePaginationIntegrationTest
     * .unpaginatedFindOrderCandidatesStillReturnsEveryRow}) relies on it as
     * a correctness cross-check against {@link #findOrderCandidatesPage}'s
     * total count. Before adding any new caller of the unpaginated
     * {@code (null, null, null)} form, prefer a lean, SQL-side query for
     * just the fields actually needed - see {@code SupplierBrandAssociationQuery.sql}
     * for the pattern this Stage established.
     */
    public List<OrderCandidateResponse> findOrderCandidates(String brandCode, String supplierCode, String keyword) {
        List<LegacyStockRow> rows = legacyStockReadRepository.findOrderCandidates(brandCode, supplierCode, keyword);
        // Post-Freeze Business Refinement: one bulk restock-expectation
        // lookup for the whole List, not one per row (same "no N+1"
        // discipline the rest of this List already follows).
        Map<String, SkuRestockExpectationResponse> restockBySku =
                restockExpectationService.getBulk(rows.stream().map(LegacyStockRow::itemCd).toList());
        // Stage 5E Targeted Remediation (RC-A): one bulk region-classification
        // load for the whole List, not one (or, before this Stage, two)
        // Portal query per row - the confirmed N+1 root cause at this
        // unpaginated method's full-catalog scale (Stage 5D).
        RegionClassificationLookup regionLookup = regionResolutionService.loadAll();
        return rows.stream().map(row -> toResponse(row, restockBySku.get(row.itemCd()), regionLookup)).toList();
    }

    /**
     * Stage 4 Targeted Real-Data Remediation (docs/real-data-audit/
     * gops-stage3-real-data-compatibility-review.md Finding #3 - a
     * confirmed real Brand has 18,596 SKUs and this List had no pagination
     * at all). Backend-paginated - {@link #findOrderCandidates} itself is
     * deliberately left unchanged (kept as a general-purpose primitive; see
     * its own Javadoc). Historical note, corrected in Stage 5H (the
     * original reason given here - that {@code DashboardService} and
     * {@code SupplierMasterService} both called it expecting the complete,
     * unpaginated result - is no longer true of either: {@code
     * DashboardService} stopped in Stage 5E's RC-A, {@code
     * SupplierMasterService} stopped in Stage 5H's RC-H). Reuses
     * {@link LegacyStockReadRepository#findStockSalesList}/
     * {@link LegacyStockReadRepository#countStockSalesList} - the exact
     * SQL/pagination contract {@code StockSalesService} already uses
     * (same base query, same default/max page size), not a second,
     * independently-maintained pagination implementation.
     */
    public PageResponse<OrderCandidateResponse> findOrderCandidatesPage(String brandCode, String supplierCode, String keyword,
                                                                          Integer page, Integer size) {
        StockSalesListFilter filter = new StockSalesListFilter(
                blankToNull(keyword), blankToNull(brandCode), blankToNull(supplierCode),
                null, null, null, null);
        int clampedSize = clampSize(size);
        int clampedPage = page == null || page < 0 ? 0 : page;
        int offset = clampedPage * clampedSize;

        long total = legacyStockReadRepository.countStockSalesList(filter);
        List<LegacyStockRow> rows = legacyStockReadRepository.findStockSalesList(filter, clampedSize, offset);
        Map<String, SkuRestockExpectationResponse> restockBySku =
                restockExpectationService.getBulk(rows.stream().map(LegacyStockRow::itemCd).toList());
        RegionClassificationLookup regionLookup = regionResolutionService.loadAll();
        List<OrderCandidateResponse> content = rows.stream()
                .map(row -> toResponse(row, restockBySku.get(row.itemCd()), regionLookup))
                .toList();
        return PageResponse.of(content, clampedPage, clampedSize, total);
    }

    /**
     * Stage 5E Targeted Remediation (RC-B, docs/real-data-audit/
     * gops-stage5e-targeted-remediation.md): the lightweight Brand code->name
     * lookup {@code CandidateListPage}'s Filter Chip needs for display -
     * previously obtained via {@code useDashboard()}, which pulled in
     * Dashboard's entire candidate-count computation (Stage 5D RC-B/RC-A)
     * just to read one field of it. Reuses the same bulk query
     * {@link DashboardService} itself now uses for the same purpose
     * (Stage 5E RC-A) - one small (~1,500 row) query, not a second,
     * independently-maintained Brand Master read.
     */
    public Map<String, String> findBrandNames() {
        return legacyStockReadRepository.findAllBrandNames();
    }

    private static int clampSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    OrderCandidateResponse toResponse(LegacyStockRow row, SkuRestockExpectationResponse restock, RegionClassificationLookup regionLookup) {
        // Stage 5E Targeted Remediation (RC-A): region resolved exactly once
        // per row (via the pre-loaded bulk lookup), then reused for calc4 -
        // previously resolved twice per row (once inside the old calc4(row),
        // once again here), doubling the N+1 cost Stage 5D identified.
        String region = recommendedQtyCalculator.resolveRegion(row, regionLookup);
        Integer calc4 = recommendedQtyCalculator.calc4(row, region);
        return new OrderCandidateResponse(
                row.itemCd(),
                row.itemName(),
                row.brandCd(),
                row.brandName(),
                row.supplierCd(),
                row.supplierName(),
                row.currentStock(),
                null, // safetyStock: Legacy computes this dynamically via Formula.getSafetyStk(soldQty),
                      // not part of this Step's reduced Read Query scope - left null rather than guessed.
                // Stage 5E Targeted Remediation (RC-E, docs/real-data-audit/
                // gops-stage5e-targeted-remediation.md): this field is Open
                // PO alone - it must never be combined with Open Arrival
                // (Stage 5D confirmed this was the only call site, across
                // Candidate List/Dashboard/Stock-Sales/SKU Detail, that did
                // so; Stock/Sales and SKU Detail were already correct).
                row.openPo(),
                row.monthlySales(),
                row.leadTime(),
                calc4,
                row.itemStatus(),
                row.discon(),
                row.unitPrice(),
                row.currency(),
                DATA_SOURCE_CODE,
                region,
                restock == null ? SkuRestockExpectationResponse.SOURCE_NONE : restock.source(),
                restock == null ? null : restock.date(),
                restock == null ? null : restock.stockoutStatus(),
                restock == null ? null : restock.informationReceivedDate(),
                restock == null ? null : restock.contactMethod(),
                restock != null && restock.hasConflict()
        );
    }
}
