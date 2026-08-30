package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.PriceChangeSet;
import com.glv.gsysportal.domain.PriceChangeSetDetail;
import com.glv.gsysportal.dto.response.PriceChangeSetDetailResponse;
import com.glv.gsysportal.dto.response.PriceChangeSetLineResponse;
import com.glv.gsysportal.dto.response.PriceChangeSetSummary;
import com.glv.gsysportal.exception.DuplicateSkuInChangeSetException;
import com.glv.gsysportal.exception.PriceChangeSetDetailNotFoundException;
import com.glv.gsysportal.exception.PriceChangeSetNotEditableException;
import com.glv.gsysportal.exception.PriceChangeSetNotFoundException;
import com.glv.gsysportal.exception.SkuNotFoundException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.PriceChangeSetDetailRepository;
import com.glv.gsysportal.repository.prototype.PriceChangeSetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 8-B: Price Change Foundation Heavy Regression - Change Set CRUD,
 * Baseline Snapshot, Concurrency Check, Margin Preview, Audit. Same
 * whole-test-method Prototype transaction + rollback pattern as
 * {@code LegacyPoConcurrencyServiceIntegrationTest}. Legacy is READ ONLY
 * throughout (every "CHANGED"/"NOT_AVAILABLE" scenario is produced by
 * mutating the Detail's PROTOTYPE-side stored Baseline directly, never
 * Legacy Demo MySQL - the Concurrency comparison only needs one side to move
 * to be exercised, and the Baseline side is this application's own data).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class PriceChangeSetServiceIntegrationTest {

    private static final String OPERATOR = "purchase01";
    private static final String ADMIN = "admin01";

    @Autowired
    private PriceChangeSetService service;
    @Autowired
    private PriceChangeSetRepository priceChangeSetRepository;
    @Autowired
    private PriceChangeSetDetailRepository priceChangeSetDetailRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;

    @Test
    void createDraftStartsInDraftAndAudits() {
        PriceChangeSetDetailResponse response = service.createDraft("Q4 kitchen adjustment", OPERATOR);

        assertEquals(PriceChangeSet.STATUS_DRAFT, response.status());
        assertEquals("Q4 kitchen adjustment", response.note());
        assertTrue(response.details().isEmpty());

        List<AuditEvent> events = auditEventRepository.findByPriceChangeSetIdOrderByPerformedAtAsc(response.id());
        assertEquals(1, events.size());
        assertEquals(AuditEvent.PRICE_CHANGE_SET_CREATED, events.get(0).getEventType());
        assertEquals(OPERATOR, events.get(0).getPerformedBy());
        assertNull(events.get(0).getPortalOrderId(), "Price Change events must never carry a portal_order_id");
    }

    @Test
    void getDetailForUnknownIdThrows() {
        assertThrows(PriceChangeSetNotFoundException.class, () -> service.getDetail(999_999L));
    }

    @Test
    void addDetailCapturesBaselineAndComputesMarginPreview() {
        PriceChangeSetDetailResponse set = service.createDraft(null, OPERATOR);

        PriceChangeSetDetailResponse updated = service.addDetail(set.id(), "HM-MUG-001", OPERATOR);

        assertEquals(1, updated.details().size());
        PriceChangeSetLineResponse line = updated.details().get(0);
        assertEquals("HM-MUG-001", line.itemCd());
        assertEquals("IG-HM-MUG", line.itemGrpCd());
        assertEquals(0, new BigDecimal("3560.00").compareTo(line.baselinePrcSellWTax()));
        assertEquals(0, new BigDecimal("3560.00").compareTo(line.currentPrcSellWTax()));
        assertEquals(0, new BigDecimal("2096.00").compareTo(line.costWTax()));
        assertEquals(0, new BigDecimal("1464.00").compareTo(line.marginAmount()));
        assertEquals(0, new BigDecimal("0.3522").compareTo(line.marginRate()));
        assertEquals(PriceChangeSetLineResponse.CONCURRENCY_UNCHANGED, line.concurrencyStatus());
        assertNull(line.proposedPrcSellWTax());
        assertNull(line.priceDifference());

        List<AuditEvent> events = auditEventRepository.findByPriceChangeSetIdOrderByPerformedAtAsc(set.id());
        assertTrue(events.stream().anyMatch(e -> AuditEvent.PRICE_CHANGE_DETAIL_ADDED.equals(e.getEventType())
                && "HM-MUG-001".equals(e.getFieldName())));
    }

    @Test
    void addDetailForUnknownSkuThrows() {
        PriceChangeSetDetailResponse set = service.createDraft(null, OPERATOR);

        assertThrows(SkuNotFoundException.class, () -> service.addDetail(set.id(), "NO-SUCH-SKU", OPERATOR));
    }

    @Test
    void addDetailTwiceForSameSkuThrows() {
        PriceChangeSetDetailResponse set = service.createDraft(null, OPERATOR);
        service.addDetail(set.id(), "HM-MUG-001", OPERATOR);

        assertThrows(DuplicateSkuInChangeSetException.class, () -> service.addDetail(set.id(), "HM-MUG-001", OPERATOR));
    }

    @Test
    void addItemGroupDetailsAddsAllSkusAndIsIdempotent() {
        PriceChangeSetDetailResponse set = service.createDraft(null, OPERATOR);

        PriceChangeSetDetailResponse afterFirst = service.addItemGroupDetails(set.id(), "IG-OD-TENT", OPERATOR);
        assertEquals(2, afterFirst.details().size());

        // Re-applying the same Item Group must not duplicate/error.
        PriceChangeSetDetailResponse afterSecond = service.addItemGroupDetails(set.id(), "IG-OD-TENT", OPERATOR);
        assertEquals(2, afterSecond.details().size());
    }

    @Test
    void updateProposedPriceComputesDifferenceAndPercentageAndAudits() {
        PriceChangeSetDetailResponse set = service.createDraft(null, OPERATOR);
        PriceChangeSetDetailResponse withDetail = service.addDetail(set.id(), "HM-MUG-001", OPERATOR);
        Long detailId = withDetail.details().get(0).detailId();

        PriceChangeSetDetailResponse updated = service.updateProposedPrice(
                set.id(), detailId, new BigDecimal("3800.00"), ADMIN);

        PriceChangeSetLineResponse line = updated.details().get(0);
        assertEquals(0, new BigDecimal("3800.00").compareTo(line.proposedPrcSellWTax()));
        assertEquals(0, new BigDecimal("240.00").compareTo(line.priceDifference())); // 3800 - 3560
        // 240 / 3560 * 100 = 6.741573... rounded HALF_UP to 2dp = 6.74
        assertEquals(0, new BigDecimal("6.74").compareTo(line.percentageChange()));

        List<AuditEvent> events = auditEventRepository.findByPriceChangeSetIdOrderByPerformedAtAsc(set.id());
        AuditEvent priceChanged = events.stream()
                .filter(e -> AuditEvent.PRICE_CHANGE_PROPOSED_PRICE_CHANGED.equals(e.getEventType()))
                .findFirst().orElseThrow();
        assertNull(priceChanged.getOldValue());
        assertEquals("3800.00", priceChanged.getNewValue());
        assertEquals(ADMIN, priceChanged.getPerformedBy());
    }

    @Test
    void negativeMarginIsComputedAndExposedWithoutError() {
        PriceChangeSetDetailResponse set = service.createDraft(null, OPERATOR);
        PriceChangeSetDetailResponse updated = service.addDetail(set.id(), "KT-BOWL-002", OPERATOR);

        PriceChangeSetLineResponse line = updated.details().get(0);
        assertEquals(0, new BigDecimal("-103.00").compareTo(line.marginAmount()));
        assertEquals(0, new BigDecimal("-0.1323").compareTo(line.marginRate()));
    }

    @Test
    void concurrencyIsChangedWhenStoredBaselineDivergesFromLiveLegacyValue() {
        PriceChangeSetDetailResponse set = service.createDraft(null, OPERATOR);
        PriceChangeSetDetailResponse withDetail = service.addDetail(set.id(), "HM-MUG-001", OPERATOR);
        Long detailId = withDetail.details().get(0).detailId();

        // Simulate "Legacy moved since Baseline was captured" by mutating
        // the stored Baseline itself (Prototype-side, this application's own
        // writable data) rather than Legacy Demo MySQL (READ ONLY).
        PriceChangeSetDetail detail = priceChangeSetDetailRepository.findById(detailId).orElseThrow();
        detail.setBaselinePrcSellWTax(new BigDecimal("9999.00"));
        priceChangeSetDetailRepository.save(detail);

        PriceChangeSetDetailResponse refreshed = service.getDetail(set.id());
        assertEquals(PriceChangeSetLineResponse.CONCURRENCY_CHANGED, refreshed.details().get(0).concurrencyStatus());
        // currentPrcSellWTax must still reflect the LIVE Legacy value, not the stale Baseline.
        assertEquals(0, new BigDecimal("3560.00").compareTo(refreshed.details().get(0).currentPrcSellWTax()));
    }

    @Test
    void concurrencyIsNotAvailableWhenSkuNoLongerResolvesInLegacy() {
        PriceChangeSetDetailResponse set = service.createDraft(null, OPERATOR);
        PriceChangeSet entity = priceChangeSetRepository.findById(set.id()).orElseThrow();

        // Bypass addDetail's Legacy validation to simulate a SKU that existed
        // at Baseline time but can no longer be resolved (deleted in Legacy).
        PriceChangeSetDetail detail = new PriceChangeSetDetail();
        detail.setPriceChangeSet(entity);
        detail.setItemCd("NO-LONGER-EXISTS");
        detail.setBaselinePrcSellWTax(new BigDecimal("1000.00"));
        detail.setBaselineCapturedAt(OffsetDateTime.now());
        detail.setCreatedAt(OffsetDateTime.now());
        detail.setUpdatedAt(OffsetDateTime.now());
        entity.getDetails().add(detail);
        priceChangeSetRepository.save(entity);

        PriceChangeSetDetailResponse refreshed = service.getDetail(set.id());
        assertEquals(PriceChangeSetLineResponse.CONCURRENCY_NOT_AVAILABLE, refreshed.details().get(0).concurrencyStatus());
        assertNull(refreshed.details().get(0).currentPrcSellWTax());
    }

    @Test
    void removeDetailDeletesRowAndAudits() {
        PriceChangeSetDetailResponse set = service.createDraft(null, OPERATOR);
        PriceChangeSetDetailResponse withDetail = service.addDetail(set.id(), "HM-MUG-001", OPERATOR);
        Long detailId = withDetail.details().get(0).detailId();

        PriceChangeSetDetailResponse afterRemoval = service.removeDetail(set.id(), detailId, OPERATOR);

        assertTrue(afterRemoval.details().isEmpty());
        List<AuditEvent> events = auditEventRepository.findByPriceChangeSetIdOrderByPerformedAtAsc(set.id());
        assertTrue(events.stream().anyMatch(e -> AuditEvent.PRICE_CHANGE_DETAIL_REMOVED.equals(e.getEventType())));
    }

    @Test
    void removeDetailForUnknownDetailIdThrows() {
        PriceChangeSetDetailResponse set = service.createDraft(null, OPERATOR);

        assertThrows(PriceChangeSetDetailNotFoundException.class, () -> service.removeDetail(set.id(), 999_999L, OPERATOR));
    }

    @Test
    void updateNoteChangesNoteAndAudits() {
        PriceChangeSetDetailResponse set = service.createDraft("original note", OPERATOR);

        PriceChangeSetDetailResponse updated = service.updateNote(set.id(), "revised note", ADMIN);

        assertEquals("revised note", updated.note());
        List<AuditEvent> events = auditEventRepository.findByPriceChangeSetIdOrderByPerformedAtAsc(set.id());
        AuditEvent noteChanged = events.stream()
                .filter(e -> AuditEvent.PRICE_CHANGE_NOTE_CHANGED.equals(e.getEventType()))
                .findFirst().orElseThrow();
        assertEquals("original note", noteChanged.getOldValue());
        assertEquals("revised note", noteChanged.getNewValue());
    }

    @Test
    void nonDraftChangeSetRejectsEveryEdit() {
        PriceChangeSetDetailResponse set = service.createDraft(null, OPERATOR);
        PriceChangeSet entity = priceChangeSetRepository.findById(set.id()).orElseThrow();
        entity.setStatus(PriceChangeSet.STATUS_CANCELLED);
        priceChangeSetRepository.save(entity);

        assertThrows(PriceChangeSetNotEditableException.class, () -> service.addDetail(set.id(), "HM-MUG-001", OPERATOR));
        assertThrows(PriceChangeSetNotEditableException.class, () -> service.updateNote(set.id(), "x", OPERATOR));
        assertThrows(PriceChangeSetNotEditableException.class,
                () -> service.addItemGroupDetails(set.id(), "IG-OD-TENT", OPERATOR));
    }

    @Test
    void listFiltersByStatus() {
        PriceChangeSetDetailResponse draft = service.createDraft("draft one", OPERATOR);
        PriceChangeSetDetailResponse toCancel = service.createDraft("to be cancelled", OPERATOR);
        PriceChangeSet cancelledEntity = priceChangeSetRepository.findById(toCancel.id()).orElseThrow();
        cancelledEntity.setStatus(PriceChangeSet.STATUS_CANCELLED);
        priceChangeSetRepository.save(cancelledEntity);

        List<PriceChangeSetSummary> draftsOnly = service.list(PriceChangeSet.STATUS_DRAFT);
        assertTrue(draftsOnly.stream().anyMatch(s -> s.id().equals(draft.id())));
        assertTrue(draftsOnly.stream().noneMatch(s -> s.id().equals(toCancel.id())));

        List<PriceChangeSetSummary> all = service.list(null);
        assertTrue(all.stream().anyMatch(s -> s.id().equals(draft.id())));
        assertTrue(all.stream().anyMatch(s -> s.id().equals(toCancel.id())));
    }

    @Test
    void auditTrailResolvesDisplayNamesNotRawUsernames() {
        PriceChangeSetDetailResponse set = service.createDraft(null, OPERATOR);

        PriceChangeSetDetailResponse detail = service.getDetail(set.id());

        assertEquals(1, detail.auditTrail().size());
        // purchase01's seeded display_name (Phase 7-H demo-data-anonymization) -
        // whatever it is, it must NOT be the raw username itself.
        assertTrue(detail.auditTrail().get(0).performedByDisplayName() == null
                || !OPERATOR.equals(detail.auditTrail().get(0).performedByDisplayName()));
    }
}
