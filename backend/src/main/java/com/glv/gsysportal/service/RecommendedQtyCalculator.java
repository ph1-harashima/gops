package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.SupplierRegionClassification;
import com.glv.gsysportal.repository.legacy.row.LegacyStockRow;
import com.glv.gsysportal.service.SupplierRegionClassificationResolutionService.RegionClassificationLookup;
import org.springframework.stereotype.Component;

/**
 * BR-09 (docs/gulliver-20260917-confirmed-business-rules.md): Strategy
 * dispatch point for Recommended Qty - resolves the Supplier[+Brand]'s
 * region classification ({@link SupplierRegionClassificationResolutionService},
 * already existing Phase 9 Foundation) and picks {@link OverseasRecommendedQtyStrategy}
 * or {@link DomesticRecommendedQtyStrategy} accordingly.
 *
 * <p>Unclassified (no {@code supplier_region_classification} row at all -
 * every Supplier today, since this Master starts empty) resolves to
 * OVERSEAS, never DOMESTIC - the confirmed default per BR-09 ("従来Gulliver
 * 社では海外Supplier中心で運用") and the only choice that keeps every
 * existing Order/Test/Demo dataset's Recommended Qty byte-for-byte
 * unchanged (a silent switch to "not yet configured" for currently-working
 * Suppliers would be a regression, not a Feature).
 */
@Component
public class RecommendedQtyCalculator {

    private final SupplierRegionClassificationResolutionService regionResolutionService;
    private final RecommendedQtyStrategy overseasStrategy = new OverseasRecommendedQtyStrategy();
    private final RecommendedQtyStrategy domesticStrategy = new DomesticRecommendedQtyStrategy();

    public RecommendedQtyCalculator(SupplierRegionClassificationResolutionService regionResolutionService) {
        this.regionResolutionService = regionResolutionService;
    }

    public Integer calc4(LegacyStockRow row) {
        String region = resolveRegion(row);
        return calc4(row, region);
    }

    /** Exposes the resolved region alongside {@link #calc4} so a caller can
     * tell apart "no recommendation because Domestic's Rule is unconfirmed"
     * from any other reason a value came back null, without re-querying
     * {@link SupplierRegionClassificationResolutionService} a second time
     * (e.g. {@code OrderCandidateService}'s own "設定準備中" display).
     * Single-row DB lookup - fine for callers handling one SKU at a time
     * (e.g. {@code SkuDetailService}). List/Dashboard callers handling many
     * rows in one request must use the {@link RegionClassificationLookup}
     * overloads below instead (Stage 5D RC-A - this per-row DB lookup was
     * the confirmed N+1 root cause at full-catalog scale). */
    public String resolveRegion(LegacyStockRow row) {
        return regionResolutionService.resolve(row.supplierCd(), row.brandCd());
    }

    /**
     * Stage 5E Targeted Remediation (RC-A): bulk variant - resolves region
     * from an already-loaded {@link RegionClassificationLookup}
     * ({@link SupplierRegionClassificationResolutionService#loadAll()},
     * fetched once per request) instead of one Portal query per row. Same
     * result as {@link #calc4(LegacyStockRow)} for any given row.
     */
    public Integer calc4(LegacyStockRow row, RegionClassificationLookup lookup) {
        String region = resolveRegion(row, lookup);
        return calc4(row, region);
    }

    /** Bulk variant of {@link #resolveRegion(LegacyStockRow)} - see {@link #calc4(LegacyStockRow, RegionClassificationLookup)}. */
    public String resolveRegion(LegacyStockRow row, RegionClassificationLookup lookup) {
        return lookup.resolve(row.supplierCd(), row.brandCd());
    }

    /** Lowest-level overload - callers that already have both the row and
     * its resolved region (e.g. {@code OrderCandidateService.toResponse},
     * which needs the resolved region for its own response field too and
     * must not re-resolve it a second time) call this directly instead of
     * one of the resolving overloads above. */
    public Integer calc4(LegacyStockRow row, String region) {
        RecommendedQtyStrategy strategy = SupplierRegionClassification.DOMESTIC.equals(region)
                ? domesticStrategy : overseasStrategy;
        return strategy.calculate(row);
    }
}
