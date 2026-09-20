package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.SkuExpectedRestock;
import com.glv.gsysportal.dto.request.SkuExpectedRestockRequest;
import com.glv.gsysportal.dto.response.SkuRestockExpectationResponse;
import com.glv.gsysportal.exception.InvalidSkuExpectedRestockException;
import com.glv.gsysportal.repository.legacy.ArrivalReadRepository;
import com.glv.gsysportal.repository.legacy.row.LegacyExpectedArrivalRow;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.SkuExpectedRestockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Post-Freeze Business Refinement (docs/gops-20260917-business-requirements-re-audit.md
 * §5-11) - merges Legacy Expected Arrival (Type A, READ ONLY) and Portal
 * Manual Expected Restock (Type C) into the single Display-Priority view
 * every screen (Candidate List/SKU Detail/Approval Detail/Stock-Sales)
 * renders, per re-audit doc §8: Legacy always wins when present; Manual
 * data is never overwritten or hidden by it, only out-prioritized for
 * display.
 */
@Service
public class SkuRestockExpectationService {

    private final SkuExpectedRestockRepository restockRepository;
    private final ArrivalReadRepository legacyArrivalReadRepository;
    private final AuditEventRepository auditEventRepository;

    public SkuRestockExpectationService(SkuExpectedRestockRepository restockRepository,
                                         ArrivalReadRepository legacyArrivalReadRepository,
                                         AuditEventRepository auditEventRepository) {
        this.restockRepository = restockRepository;
        this.legacyArrivalReadRepository = legacyArrivalReadRepository;
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public SkuRestockExpectationResponse get(String skuCode) {
        Map<String, LocalDate> legacyBySku = legacyExpectedArrivalMap(List.of(skuCode));
        SkuExpectedRestock manual = restockRepository.findBySkuCode(skuCode).orElse(null);
        return merge(skuCode, legacyBySku.get(skuCode), manual);
    }

    /** Bulk variant for List screens - two queries total (one Legacy, one
     * Portal) regardless of how many SKUs are on the page, never one query
     * per row. */
    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public Map<String, SkuRestockExpectationResponse> getBulk(Collection<String> skuCodes) {
        if (skuCodes == null || skuCodes.isEmpty()) {
            return Map.of();
        }
        List<String> distinctSkus = skuCodes.stream().distinct().toList();
        Map<String, LocalDate> legacyBySku = legacyExpectedArrivalMap(distinctSkus);
        Map<String, SkuExpectedRestock> manualBySku = new HashMap<>();
        for (SkuExpectedRestock r : restockRepository.findBySkuCodeIn(distinctSkus)) {
            manualBySku.put(r.getSkuCode(), r);
        }
        Map<String, SkuRestockExpectationResponse> result = new HashMap<>();
        for (String sku : distinctSkus) {
            result.put(sku, merge(sku, legacyBySku.get(sku), manualBySku.get(sku)));
        }
        return result;
    }

    @Transactional(transactionManager = "prototypeTransactionManager")
    public SkuRestockExpectationResponse update(String skuCode, SkuExpectedRestockRequest request, String performedBy) {
        if (request.unknown() && request.expectedRestockDate() != null) {
            throw new InvalidSkuExpectedRestockException();
        }

        SkuExpectedRestock record = restockRepository.findBySkuCode(skuCode).orElse(null);
        String before = summarize(record);
        OffsetDateTime now = OffsetDateTime.now();
        if (record == null) {
            record = new SkuExpectedRestock();
            record.setSkuCode(skuCode);
            record.setCreatedBy(performedBy);
            record.setCreatedAt(now);
        }
        record.setExpectedRestockDate(request.expectedRestockDate());
        record.setUnknown(request.unknown());
        record.setMemo(request.memo());
        record.setUpdatedBy(performedBy);
        record.setUpdatedAt(now);
        record = restockRepository.save(record);

        String after = summarize(record);
        auditEventRepository.save(AuditEvent.forSku(skuCode, AuditEvent.SKU_EXPECTED_RESTOCK_CHANGED,
                "expectedRestock", before, after, performedBy, now));

        Map<String, LocalDate> legacyBySku = legacyExpectedArrivalMap(List.of(skuCode));
        return merge(skuCode, legacyBySku.get(skuCode), record);
    }

    private Map<String, LocalDate> legacyExpectedArrivalMap(Collection<String> skuCodes) {
        Map<String, LocalDate> map = new HashMap<>();
        for (LegacyExpectedArrivalRow row : legacyArrivalReadRepository.findExpectedArrivalBySkus(skuCodes)) {
            map.put(row.skuCode(), row.expectedArrivalDate());
        }
        return map;
    }

    private static SkuRestockExpectationResponse merge(String skuCode, LocalDate legacyDate, SkuExpectedRestock manual) {
        String manualMemo = manual == null ? null : manual.getMemo();
        String manualUpdatedBy = manual == null ? null : manual.getUpdatedBy();
        OffsetDateTime manualUpdatedAt = manual == null ? null : manual.getUpdatedAt();
        // Exposed separately from the merged `date`/`source` below so the
        // Edit form can always prefill the real Manual value, even while
        // Legacy is winning display priority (see class Javadoc).
        LocalDate manualDate = manual == null ? null : manual.getExpectedRestockDate();
        boolean manualUnknown = manual != null && manual.isUnknown();

        if (legacyDate != null) {
            // Type A always wins for DISPLAY - never overwritten by Manual
            // data (re-audit doc §8) - but the Manual record's own
            // audit-trail fields still ride along underneath it (see class
            // Javadoc) so the Edit form can show "last set by X" even here.
            return new SkuRestockExpectationResponse(skuCode, SkuRestockExpectationResponse.SOURCE_LEGACY,
                    legacyDate, manualMemo, manualUpdatedBy, manualUpdatedAt, manualDate, manualUnknown);
        }
        if (manualUnknown) {
            return new SkuRestockExpectationResponse(skuCode, SkuRestockExpectationResponse.SOURCE_PORTAL_UNKNOWN,
                    null, manualMemo, manualUpdatedBy, manualUpdatedAt, manualDate, manualUnknown);
        }
        if (manualDate != null) {
            return new SkuRestockExpectationResponse(skuCode, SkuRestockExpectationResponse.SOURCE_PORTAL_MANUAL,
                    manualDate, manualMemo, manualUpdatedBy, manualUpdatedAt, manualDate, manualUnknown);
        }
        return new SkuRestockExpectationResponse(skuCode, SkuRestockExpectationResponse.SOURCE_NONE,
                null, manualMemo, manualUpdatedBy, manualUpdatedAt, manualDate, manualUnknown);
    }

    private static String summarize(SkuExpectedRestock r) {
        if (r == null) {
            return "(none)";
        }
        if (r.isUnknown()) {
            return "unknown" + (r.getMemo() != null ? "; memo=" + r.getMemo() : "");
        }
        if (r.getExpectedRestockDate() != null) {
            return r.getExpectedRestockDate() + (r.getMemo() != null ? "; memo=" + r.getMemo() : "");
        }
        return "(cleared)";
    }
}
