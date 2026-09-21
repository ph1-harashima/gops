package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.PortalUser;
import com.glv.gsysportal.domain.PriceChangeSet;
import com.glv.gsysportal.domain.PriceChangeSetDetail;
import com.glv.gsysportal.dto.response.PriceChangeAuditEventView;
import com.glv.gsysportal.dto.response.PriceChangeSetDetailResponse;
import com.glv.gsysportal.dto.response.PriceChangeSetLineResponse;
import com.glv.gsysportal.dto.response.PriceChangeSetSummary;
import com.glv.gsysportal.exception.DuplicateSkuInChangeSetException;
import com.glv.gsysportal.exception.PriceChangeSetDetailNotFoundException;
import com.glv.gsysportal.exception.PriceChangeSetNotEditableException;
import com.glv.gsysportal.exception.PriceChangeSetNotFoundException;
import com.glv.gsysportal.exception.SkuNotFoundException;
import com.glv.gsysportal.legacy.calc.MarginCalculator;
import com.glv.gsysportal.repository.legacy.LegacyPriceReadRepository;
import com.glv.gsysportal.repository.legacy.row.LegacyPriceRow;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.PortalUserRepository;
import com.glv.gsysportal.repository.prototype.PriceChangeSetDetailRepository;
import com.glv.gsysportal.repository.prototype.PriceChangeSetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Price Change Foundation (Phase 8-B, docs/target-price-change-workflow.md).
 * Everything this Service does is deliberately scoped to what that
 * Document's 16章 classified as "Customer回答前でも安全に実装可能": DRAFT
 * Change Set CRUD, Baseline Snapshot capture (via
 * {@link LegacyPriceReadRepository}, Legacy READ ONLY), a live Concurrency
 * comparison, and Margin Preview display. No Approval routing, no Scheduled/
 * Future Price, no Threshold/Warning Action, no real G-SYS reflection - see
 * {@link PriceChangeSet}'s own State constants Javadoc for exactly which
 * States this Phase can and cannot reach.
 */
@Service
public class PriceChangeSetService {

    private final PriceChangeSetRepository priceChangeSetRepository;
    private final PriceChangeSetDetailRepository priceChangeSetDetailRepository;
    private final LegacyPriceReadRepository legacyPriceReadRepository;
    private final AuditEventRepository auditEventRepository;
    private final PortalUserRepository portalUserRepository;

    public PriceChangeSetService(PriceChangeSetRepository priceChangeSetRepository,
                                  PriceChangeSetDetailRepository priceChangeSetDetailRepository,
                                  LegacyPriceReadRepository legacyPriceReadRepository,
                                  AuditEventRepository auditEventRepository,
                                  PortalUserRepository portalUserRepository) {
        this.priceChangeSetRepository = priceChangeSetRepository;
        this.priceChangeSetDetailRepository = priceChangeSetDetailRepository;
        this.legacyPriceReadRepository = legacyPriceReadRepository;
        this.auditEventRepository = auditEventRepository;
        this.portalUserRepository = portalUserRepository;
    }

    /** Product Selection search (target-price-change-workflow.md 15章 -
     * folded into Create/Edit). Legacy READ ONLY, same query
     * {@link #addDetail}/{@link #addItemGroupDetails} re-use for the actual
     * Baseline capture - this method is display-only and adds nothing. */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public List<com.glv.gsysportal.dto.response.PriceChangeCandidateResponse> searchCandidates(
            String brandCode, String itemGrpCd, String keyword) {
        return legacyPriceReadRepository.search(brandCode, itemGrpCd, keyword).stream()
                .map(PriceChangeSetService::toCandidateResponse)
                .toList();
    }

    static final int MAX_PAGE_SIZE = 100;
    static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * Stage 4 Targeted Real-Data Remediation (Remediation D,
     * docs/real-data-audit/gops-stage4-targeted-real-data-remediation.md):
     * Backend-paginated Product Selection search - {@link #searchCandidates}
     * itself is deliberately left unchanged for any other/future caller
     * expecting the full unpaginated result. Same page/size contract as
     * {@code OrderCandidateService#findOrderCandidatesPage}/
     * {@code StockSalesService#list}.
     */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public com.glv.gsysportal.dto.response.PageResponse<com.glv.gsysportal.dto.response.PriceChangeCandidateResponse> searchCandidatesPage(
            String brandCode, String itemGrpCd, String keyword, Integer page, Integer size) {
        int clampedSize = clampSize(size);
        int clampedPage = page == null || page < 0 ? 0 : page;
        int offset = clampedPage * clampedSize;

        long total = legacyPriceReadRepository.countSearch(brandCode, itemGrpCd, keyword);
        List<com.glv.gsysportal.dto.response.PriceChangeCandidateResponse> content =
                legacyPriceReadRepository.searchPage(brandCode, itemGrpCd, keyword, clampedSize, offset).stream()
                        .map(PriceChangeSetService::toCandidateResponse)
                        .toList();
        return com.glv.gsysportal.dto.response.PageResponse.of(content, clampedPage, clampedSize, total);
    }

    private static com.glv.gsysportal.dto.response.PriceChangeCandidateResponse toCandidateResponse(LegacyPriceRow row) {
        return new com.glv.gsysportal.dto.response.PriceChangeCandidateResponse(
                row.itemCd(), row.itemName(), row.brandCd(), row.brandName(), row.itemGrpCd(),
                row.itemStatus(), row.prcSellWTax(), row.costThisMonthAvg());
    }

    private static int clampSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /** Item Group picker options (9章) - see {@link LegacyPriceReadRepository#findDistinctItemGroupCodes()}
     * for why there is no label beyond the code itself. */
    @Transactional(readOnly = true, transactionManager = "legacyTransactionManager")
    public List<String> listItemGroupCodes() {
        return legacyPriceReadRepository.findDistinctItemGroupCodes();
    }

    /** Price Change List (target-price-change-workflow.md 15章). */
    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public List<PriceChangeSetSummary> list(String status) {
        List<PriceChangeSet> sets = priceChangeSetRepository.findAllForList(status);
        Map<String, String> displayNames = resolveDisplayNames(sets.stream().map(PriceChangeSet::getCreatedBy).toList());
        return sets.stream()
                .map(s -> new PriceChangeSetSummary(s.getId(), s.getStatus(), s.getNote(),
                        displayNames.get(s.getCreatedBy()), s.getCreatedAt(), s.getDetails().size(), s.getUpdatedAt()))
                .toList();
    }

    @Transactional(transactionManager = "prototypeTransactionManager")
    public PriceChangeSetDetailResponse createDraft(String note, String performedBy) {
        OffsetDateTime now = OffsetDateTime.now();
        PriceChangeSet set = new PriceChangeSet();
        set.setStatus(PriceChangeSet.STATUS_DRAFT);
        set.setNote(note);
        set.setCreatedBy(performedBy);
        set.setCreatedAt(now);
        set.setUpdatedBy(performedBy);
        set.setUpdatedAt(now);
        set = priceChangeSetRepository.save(set);

        audit(set.getId(), AuditEvent.PRICE_CHANGE_SET_CREATED, null, null, null, performedBy, now);

        return toDetailResponse(set);
    }

    @Transactional(readOnly = true, transactionManager = "prototypeTransactionManager")
    public PriceChangeSetDetailResponse getDetail(Long id) {
        return toDetailResponse(findSetOrThrow(id));
    }

    /** SKU個別選択 (9章) - re-fetches the SKU from Legacy (never trusts a
     * Frontend-sent price) and captures it as this Detail's Baseline. */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public PriceChangeSetDetailResponse addDetail(Long id, String itemCd, String performedBy) {
        PriceChangeSet set = findEditableSetOrThrow(id);
        List<LegacyPriceRow> rows = legacyPriceReadRepository.findBySkus(List.of(itemCd));
        if (rows.isEmpty()) {
            throw new SkuNotFoundException(Set.of(itemCd));
        }
        addDetailFromRow(set, rows.get(0), performedBy, OffsetDateTime.now());
        return toDetailResponse(set);
    }

    /** Item Groupを起点とした複数選択 (9章) - resolved server-side; SKUs
     * already present in this Change Set are skipped rather than duplicated
     * or erroring (idempotent). */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public PriceChangeSetDetailResponse addItemGroupDetails(Long id, String itemGrpCd, String performedBy) {
        PriceChangeSet set = findEditableSetOrThrow(id);
        List<LegacyPriceRow> rows = legacyPriceReadRepository.search(null, itemGrpCd, null);
        if (rows.isEmpty()) {
            throw new SkuNotFoundException(Set.of(itemGrpCd));
        }
        Set<String> existing = existingSkus(set);
        OffsetDateTime now = OffsetDateTime.now();
        for (LegacyPriceRow row : rows) {
            if (existing.contains(row.itemCd())) {
                continue;
            }
            addDetailFromRow(set, row, performedBy, now);
            existing.add(row.itemCd());
        }
        return toDetailResponse(set);
    }

    @Transactional(transactionManager = "prototypeTransactionManager")
    public PriceChangeSetDetailResponse updateProposedPrice(Long id, Long detailId, BigDecimal proposedPrice,
                                                              String performedBy) {
        PriceChangeSet set = findEditableSetOrThrow(id);
        PriceChangeSetDetail detail = findDetailOrThrow(set, detailId);

        String oldValue = detail.getProposedPrcSellWTax() == null ? null : detail.getProposedPrcSellWTax().toPlainString();
        String newValue = proposedPrice == null ? null : proposedPrice.toPlainString();

        OffsetDateTime now = OffsetDateTime.now();
        detail.setProposedPrcSellWTax(proposedPrice);
        detail.setUpdatedAt(now);
        touchSet(set, performedBy, now);

        audit(set.getId(), AuditEvent.PRICE_CHANGE_PROPOSED_PRICE_CHANGED, detail.getItemCd(), oldValue, newValue,
                performedBy, now);

        return toDetailResponse(set);
    }

    @Transactional(transactionManager = "prototypeTransactionManager")
    public PriceChangeSetDetailResponse removeDetail(Long id, Long detailId, String performedBy) {
        PriceChangeSet set = findEditableSetOrThrow(id);
        PriceChangeSetDetail detail = findDetailOrThrow(set, detailId);

        OffsetDateTime now = OffsetDateTime.now();
        set.getDetails().remove(detail);
        touchSet(set, performedBy, now);

        audit(set.getId(), AuditEvent.PRICE_CHANGE_DETAIL_REMOVED, detail.getItemCd(), detail.getItemCd(), null,
                performedBy, now);

        return toDetailResponse(set);
    }

    @Transactional(transactionManager = "prototypeTransactionManager")
    public PriceChangeSetDetailResponse updateNote(Long id, String note, String performedBy) {
        PriceChangeSet set = findEditableSetOrThrow(id);
        String oldValue = set.getNote();

        OffsetDateTime now = OffsetDateTime.now();
        set.setNote(note);
        touchSet(set, performedBy, now);

        audit(set.getId(), AuditEvent.PRICE_CHANGE_NOTE_CHANGED, null, oldValue, note, performedBy, now);

        return toDetailResponse(set);
    }

    // ------------------------------------------------------------------

    private void addDetailFromRow(PriceChangeSet set, LegacyPriceRow row, String performedBy, OffsetDateTime now) {
        if (existingSkus(set).contains(row.itemCd())) {
            throw new DuplicateSkuInChangeSetException(row.itemCd());
        }
        PriceChangeSetDetail detail = new PriceChangeSetDetail();
        detail.setPriceChangeSet(set);
        detail.setItemCd(row.itemCd());
        detail.setItemNameSnapshot(row.itemName());
        detail.setBrandCodeSnapshot(row.brandCd());
        detail.setItemGrpCdSnapshot(row.itemGrpCd());
        detail.setBaselinePrcSellWTax(row.prcSellWTax());
        detail.setBaselineCostThisMonthAvg(row.costThisMonthAvg());
        detail.setBaselineFreeShipFlg(row.freeShipFlg());
        detail.setBaselineShipFee(row.shipFee());
        detail.setBaselineCapturedAt(now);
        detail.setCreatedAt(now);
        detail.setUpdatedAt(now);
        // Explicit save (not just cascade-on-flush-at-commit): callers such
        // as removeDetail/updateProposedPrice need detail.getId() populated
        // in the SAME transaction as this add (IDENTITY generation only
        // assigns an id once the INSERT actually runs).
        detail = priceChangeSetDetailRepository.save(detail);
        set.getDetails().add(detail);
        touchSet(set, performedBy, now);

        audit(set.getId(), AuditEvent.PRICE_CHANGE_DETAIL_ADDED, row.itemCd(), null, row.itemCd(), performedBy, now);
    }

    private Set<String> existingSkus(PriceChangeSet set) {
        Set<String> skus = new LinkedHashSet<>();
        set.getDetails().forEach(d -> skus.add(d.getItemCd()));
        return skus;
    }

    private void touchSet(PriceChangeSet set, String performedBy, OffsetDateTime now) {
        set.setUpdatedBy(performedBy);
        set.setUpdatedAt(now);
    }

    private void audit(Long priceChangeSetId, String eventType, String fieldName, String oldValue, String newValue,
                        String performedBy, OffsetDateTime performedAt) {
        auditEventRepository.save(AuditEvent.forPriceChangeSet(
                priceChangeSetId, eventType, fieldName, oldValue, newValue, performedBy, performedAt));
    }

    private PriceChangeSet findSetOrThrow(Long id) {
        return priceChangeSetRepository.findById(id).orElseThrow(() -> new PriceChangeSetNotFoundException(id));
    }

    private PriceChangeSet findEditableSetOrThrow(Long id) {
        PriceChangeSet set = findSetOrThrow(id);
        if (!PriceChangeSet.STATUS_DRAFT.equals(set.getStatus())) {
            throw new PriceChangeSetNotEditableException(id, set.getStatus());
        }
        return set;
    }

    private PriceChangeSetDetail findDetailOrThrow(PriceChangeSet set, Long detailId) {
        return set.getDetails().stream()
                .filter(d -> d.getId().equals(detailId))
                .findFirst()
                .orElseThrow(() -> new PriceChangeSetDetailNotFoundException(detailId));
    }

    /** Re-reads every Detail SKU's CURRENT Legacy value (7章's Source of
     * Truth principle: Current Price is never read from Portal storage) and
     * combines it with the stored Baseline for Concurrency comparison, plus
     * a live Margin Preview for both the current and proposed price. */
    private PriceChangeSetDetailResponse toDetailResponse(PriceChangeSet set) {
        List<PriceChangeSetDetail> details = set.getDetails();
        List<String> skus = details.stream().map(PriceChangeSetDetail::getItemCd).toList();
        Map<String, LegacyPriceRow> liveBySku = new HashMap<>();
        if (!skus.isEmpty()) {
            legacyPriceReadRepository.findBySkus(skus).forEach(row -> liveBySku.put(row.itemCd(), row));
        }

        List<PriceChangeSetLineResponse> lines = details.stream()
                .map(d -> toLineResponse(d, liveBySku.get(d.getItemCd())))
                .toList();

        List<AuditEvent> events = auditEventRepository.findByPriceChangeSetIdOrderByPerformedAtAsc(set.getId());
        Map<String, String> displayNames = resolveDisplayNames(events.stream().map(AuditEvent::getPerformedBy).toList());
        List<PriceChangeAuditEventView> auditTrail = events.stream()
                .map(e -> new PriceChangeAuditEventView(e.getEventType(), e.getFieldName(), e.getOldValue(),
                        e.getNewValue(), displayNames.get(e.getPerformedBy()), e.getPerformedAt(), e.getNote()))
                .toList();

        Map<String, String> creatorNames = resolveDisplayNames(List.of(set.getCreatedBy(), set.getUpdatedBy()));
        return new PriceChangeSetDetailResponse(set.getId(), set.getStatus(), set.getNote(),
                creatorNames.get(set.getCreatedBy()), set.getCreatedAt(),
                creatorNames.get(set.getUpdatedBy()), set.getUpdatedAt(), lines, auditTrail);
    }

    private PriceChangeSetLineResponse toLineResponse(PriceChangeSetDetail detail, LegacyPriceRow live) {
        BigDecimal currentPrcSellWTax = live == null ? null : live.prcSellWTax();
        BigDecimal costWTax = live == null ? null : live.costThisMonthAvg();
        Boolean freeShipFlg = live == null ? null : live.freeShipFlg();
        BigDecimal shipFee = live == null ? null : live.shipFee();

        MarginCalculator.MarginBreakdown current = MarginCalculator.compute(currentPrcSellWTax, costWTax, freeShipFlg, shipFee);
        MarginCalculator.MarginBreakdown proposed = MarginCalculator.compute(
                detail.getProposedPrcSellWTax(), costWTax, freeShipFlg, shipFee);

        BigDecimal priceDifference = (detail.getProposedPrcSellWTax() != null && currentPrcSellWTax != null)
                ? detail.getProposedPrcSellWTax().subtract(currentPrcSellWTax)
                : null;
        BigDecimal percentageChange = (priceDifference != null && currentPrcSellWTax != null
                && currentPrcSellWTax.compareTo(BigDecimal.ZERO) != 0)
                ? priceDifference.multiply(BigDecimal.valueOf(100)).divide(currentPrcSellWTax, 2, RoundingMode.HALF_UP)
                : null;

        String concurrencyStatus;
        if (live == null) {
            concurrencyStatus = PriceChangeSetLineResponse.CONCURRENCY_NOT_AVAILABLE;
        } else if (bigDecimalEquals(detail.getBaselinePrcSellWTax(), currentPrcSellWTax)) {
            concurrencyStatus = PriceChangeSetLineResponse.CONCURRENCY_UNCHANGED;
        } else {
            concurrencyStatus = PriceChangeSetLineResponse.CONCURRENCY_CHANGED;
        }

        return new PriceChangeSetLineResponse(detail.getId(), detail.getItemCd(), detail.getItemNameSnapshot(),
                detail.getBrandCodeSnapshot(), detail.getItemGrpCdSnapshot(), detail.getBaselinePrcSellWTax(),
                currentPrcSellWTax, costWTax, current.marginAmount(), current.marginRate(),
                detail.getProposedPrcSellWTax(), proposed.marginAmount(), proposed.marginRate(),
                priceDifference, percentageChange, concurrencyStatus);
    }

    private static boolean bigDecimalEquals(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }

    /** Same i18n-audit resolution idiom as {@code OrderHistoryService}: one
     * lookup per distinct Login ID, missing accounts resolve to {@code null}
     * rather than throwing. */
    private Map<String, String> resolveDisplayNames(List<String> usernames) {
        Map<String, String> displayNames = new HashMap<>();
        usernames.stream().filter(java.util.Objects::nonNull).distinct().forEach(username ->
                displayNames.put(username, portalUserRepository.findByUsername(username)
                        .map(PortalUser::getDisplayName)
                        .orElse(null)));
        return displayNames;
    }
}
