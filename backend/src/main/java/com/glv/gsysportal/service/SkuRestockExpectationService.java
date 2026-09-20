package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.SkuExpectedRestock;
import com.glv.gsysportal.domain.SkuManufacturerStockoutHistory;
import com.glv.gsysportal.dto.request.SkuExpectedRestockRequest;
import com.glv.gsysportal.dto.response.SkuManufacturerStockoutHistoryEntryResponse;
import com.glv.gsysportal.dto.response.SkuRestockExpectationResponse;
import com.glv.gsysportal.exception.InvalidSkuExpectedRestockException;
import com.glv.gsysportal.repository.legacy.ArrivalReadRepository;
import com.glv.gsysportal.repository.legacy.row.LegacyExpectedArrivalRow;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.SkuExpectedRestockRepository;
import com.glv.gsysportal.repository.prototype.SkuManufacturerStockoutHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Post-Freeze Business Refinement (docs/gops-20260917-business-requirements-re-audit.md
 * §5-11) - merges Legacy Expected Arrival (Type A, READ ONLY) and Portal
 * Manual Expected Restock (Type C) into the single Display-Priority view
 * every screen (Candidate List/SKU Detail/Approval Detail/Stock-Sales)
 * renders, per re-audit doc §8: Legacy always wins when present; Manual
 * data is never overwritten or hidden by it, only out-prioritized for
 * display.
 *
 * <p>Post-Freeze Business Refinement 2
 * (docs/gops-manufacturer-stockout-information-management.md) - broadened
 * from "just a restock date" into "Manufacturer Stockout Information"
 * (status/shortage qty/information source/contact method), and now also
 * writes a Business-facing History snapshot on every change (§11) and
 * computes a {@code hasConflict} flag when Legacy and the Manufacturer
 * actively disagree (§23), rather than letting Legacy silently hide the
 * Manufacturer's own account.
 */
@Service
public class SkuRestockExpectationService {

    private static final Set<String> VALID_STATUSES = Set.of(
            SkuRestockExpectationResponse.STOCKOUT_STATUS_STOCKOUT,
            SkuRestockExpectationResponse.STOCKOUT_STATUS_LONG_TERM,
            SkuRestockExpectationResponse.STOCKOUT_STATUS_RESOLVED
    );
    private static final Set<String> VALID_CONTACT_METHODS = Set.of(
            SkuRestockExpectationResponse.CONTACT_METHOD_PHONE,
            SkuRestockExpectationResponse.CONTACT_METHOD_EMAIL,
            SkuRestockExpectationResponse.CONTACT_METHOD_ORDER_RESPONSE,
            SkuRestockExpectationResponse.CONTACT_METHOD_OTHER
    );

    private final SkuExpectedRestockRepository restockRepository;
    private final SkuManufacturerStockoutHistoryRepository historyRepository;
    private final ArrivalReadRepository legacyArrivalReadRepository;
    private final AuditEventRepository auditEventRepository;

    public SkuRestockExpectationService(SkuExpectedRestockRepository restockRepository,
                                         SkuManufacturerStockoutHistoryRepository historyRepository,
                                         ArrivalReadRepository legacyArrivalReadRepository,
                                         AuditEventRepository auditEventRepository) {
        this.restockRepository = restockRepository;
        this.historyRepository = historyRepository;
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

    /** Business-facing History timeline (requirements doc §11/§21) - the
     * full state at each past change, oldest first. Separate from
     * {@code audit_event} (Technical Audit, unchanged). */
    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public List<SkuManufacturerStockoutHistoryEntryResponse> getHistory(String skuCode) {
        return historyRepository.findBySkuCodeOrderByRecordedAtAsc(skuCode).stream()
                .map(h -> new SkuManufacturerStockoutHistoryEntryResponse(
                        h.getStockoutStatus(), h.getExpectedRestockDate(), h.isUnknown(),
                        h.getShortageQty(), h.getInformationReceivedDate(), h.getContactMethod(),
                        h.getMemo(), h.getRecordedBy(), h.getRecordedAt()))
                .toList();
    }

    @Transactional(transactionManager = "prototypeTransactionManager")
    public SkuRestockExpectationResponse update(String skuCode, SkuExpectedRestockRequest request, String performedBy) {
        if (request.unknown() && request.expectedRestockDate() != null) {
            throw new InvalidSkuExpectedRestockException();
        }
        if (request.stockoutStatus() != null && !VALID_STATUSES.contains(request.stockoutStatus())) {
            throw new InvalidSkuExpectedRestockException("stockoutStatus must be one of " + VALID_STATUSES + " or null");
        }
        if (request.contactMethod() != null && !VALID_CONTACT_METHODS.contains(request.contactMethod())) {
            throw new InvalidSkuExpectedRestockException("contactMethod must be one of " + VALID_CONTACT_METHODS + " or null");
        }
        if (request.shortageQty() != null && request.shortageQty() < 0) {
            throw new InvalidSkuExpectedRestockException("shortageQty must not be negative");
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
        record.setStockoutStatus(request.stockoutStatus());
        record.setShortageQty(request.shortageQty());
        record.setInformationReceivedDate(request.informationReceivedDate());
        record.setContactMethod(request.contactMethod());
        record.setUpdatedBy(performedBy);
        record.setUpdatedAt(now);
        record = restockRepository.save(record);

        String after = summarize(record);
        auditEventRepository.save(AuditEvent.forSku(skuCode, AuditEvent.SKU_EXPECTED_RESTOCK_CHANGED,
                "expectedRestock", before, after, performedBy, now));

        SkuManufacturerStockoutHistory history = new SkuManufacturerStockoutHistory();
        history.setSkuCode(skuCode);
        history.setStockoutStatus(record.getStockoutStatus());
        history.setExpectedRestockDate(record.getExpectedRestockDate());
        history.setUnknown(record.isUnknown());
        history.setShortageQty(record.getShortageQty());
        history.setInformationReceivedDate(record.getInformationReceivedDate());
        history.setContactMethod(record.getContactMethod());
        history.setMemo(record.getMemo());
        history.setRecordedBy(performedBy);
        history.setRecordedAt(now);
        historyRepository.save(history);

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
        String stockoutStatus = manual == null ? null : manual.getStockoutStatus();
        Integer shortageQty = manual == null ? null : manual.getShortageQty();
        LocalDate informationReceivedDate = manual == null ? null : manual.getInformationReceivedDate();
        String contactMethod = manual == null ? null : manual.getContactMethod();
        // requirements doc §23: Legacy has an incoming Arrival, but the
        // Manufacturer is still telling us (via a STOCKOUT/LONG_TERM_STOCKOUT
        // status) that this SKU is short - a real disagreement worth
        // surfacing, not resolving silently in either direction.
        boolean hasConflict = legacyDate != null
                && (SkuRestockExpectationResponse.STOCKOUT_STATUS_STOCKOUT.equals(stockoutStatus)
                    || SkuRestockExpectationResponse.STOCKOUT_STATUS_LONG_TERM.equals(stockoutStatus));

        if (legacyDate != null) {
            // Type A always wins for the merged DISPLAY field - never
            // overwritten by Manual data (re-audit doc §8) - but every raw
            // Manufacturer field, plus hasConflict above, still rides along
            // so a screen can show both when they disagree (§23).
            return new SkuRestockExpectationResponse(skuCode, SkuRestockExpectationResponse.SOURCE_LEGACY,
                    legacyDate, manualMemo, manualUpdatedBy, manualUpdatedAt, manualDate, manualUnknown,
                    legacyDate, stockoutStatus, shortageQty, informationReceivedDate, contactMethod, hasConflict);
        }
        if (manualUnknown) {
            return new SkuRestockExpectationResponse(skuCode, SkuRestockExpectationResponse.SOURCE_PORTAL_UNKNOWN,
                    null, manualMemo, manualUpdatedBy, manualUpdatedAt, manualDate, manualUnknown,
                    legacyDate, stockoutStatus, shortageQty, informationReceivedDate, contactMethod, hasConflict);
        }
        if (manualDate != null) {
            return new SkuRestockExpectationResponse(skuCode, SkuRestockExpectationResponse.SOURCE_PORTAL_MANUAL,
                    manualDate, manualMemo, manualUpdatedBy, manualUpdatedAt, manualDate, manualUnknown,
                    legacyDate, stockoutStatus, shortageQty, informationReceivedDate, contactMethod, hasConflict);
        }
        return new SkuRestockExpectationResponse(skuCode, SkuRestockExpectationResponse.SOURCE_NONE,
                null, manualMemo, manualUpdatedBy, manualUpdatedAt, manualDate, manualUnknown,
                legacyDate, stockoutStatus, shortageQty, informationReceivedDate, contactMethod, hasConflict);
    }

    private static String summarize(SkuExpectedRestock r) {
        if (r == null) {
            return "(none)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(r.getStockoutStatus() != null ? r.getStockoutStatus() : "(no status)");
        if (r.isUnknown()) {
            sb.append("; restock=unknown");
        } else if (r.getExpectedRestockDate() != null) {
            sb.append("; restock=").append(r.getExpectedRestockDate());
        }
        if (r.getShortageQty() != null) {
            sb.append("; shortageQty=").append(r.getShortageQty());
        }
        if (r.getInformationReceivedDate() != null) {
            sb.append("; receivedDate=").append(r.getInformationReceivedDate());
        }
        if (r.getContactMethod() != null) {
            sb.append("; contact=").append(r.getContactMethod());
        }
        if (r.getMemo() != null) {
            sb.append("; memo=").append(r.getMemo());
        }
        return sb.toString();
    }
}
