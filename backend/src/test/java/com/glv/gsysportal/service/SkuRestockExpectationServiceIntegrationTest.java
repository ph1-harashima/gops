package com.glv.gsysportal.service;

import com.glv.gsysportal.dto.request.SkuExpectedRestockRequest;
import com.glv.gsysportal.dto.response.AuditEventView;
import com.glv.gsysportal.dto.response.SkuRestockExpectationResponse;
import com.glv.gsysportal.exception.InvalidSkuExpectedRestockException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Post-Freeze Business Refinement - Scenario C/D/E/F from the
 * implementation instructions: Legacy Expected Arrival wins over Manual
 * data when present (Scenario C); Manual date entry for a SKU Legacy has no
 * open Arrival for (Scenario D); explicit "未定" (Scenario E); Audit Trail
 * records Before/After (Scenario F). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class SkuRestockExpectationServiceIntegrationTest {

    // OD-TENT-002 has an open (stk_in_date IS NULL) PO/Arrival with a real
    // eta in the Demo seed - matches Scenario C ("欠品 -> Legacy Expected
    // Arrivalあり"). OD-TENT-001's own Arrival (PO-OUTDOOR-01) is already
    // stocked in (stk_in_date set), so it does NOT qualify - verified
    // directly against the Demo DB before picking this SKU.
    private static final String SKU_WITH_LEGACY_ARRIVAL = "OD-TENT-002";
    // A synthetic SKU code Legacy has never heard of - Legacy lookup always
    // returns empty for it, matching Scenario D/E ("Legacy Arrivalなし").
    private static final String SKU_WITHOUT_LEGACY_ARRIVAL = "SKU-NO-LEGACY-ARRIVAL-TEST";

    @Autowired
    private SkuRestockExpectationService service;
    @Autowired
    private AuditEventRepository auditEventRepository;

    @Test
    void legacyExpectedArrivalWinsOverManualData_scenarioC() {
        // Even if a Manual record exists, Legacy (when present) always wins
        // for display - re-audit doc §8's explicit rule.
        LocalDate manualDate = LocalDate.now().plusDays(30);
        service.update(SKU_WITH_LEGACY_ARRIVAL,
                new SkuExpectedRestockRequest(manualDate, false, "manual guess"), "tester01");

        SkuRestockExpectationResponse result = service.get(SKU_WITH_LEGACY_ARRIVAL);

        assertEquals(SkuRestockExpectationResponse.SOURCE_LEGACY, result.source());
        assertTrue(result.date() != null, "Legacy-sourced date should be present");
        // The Manual record's own audit fields still ride along underneath,
        // per the class Javadoc, even though Legacy is winning display.
        assertEquals("tester01", result.manualUpdatedBy());
        // manualDate/manualUnknown must reflect the real underlying Manual
        // record even while Legacy wins for `date`/`source` - the Edit form
        // relies on this to prefill correctly instead of showing Legacy's
        // own date as if it were the Manual value.
        assertEquals(manualDate, result.manualDate());
        assertEquals(false, result.manualUnknown());
    }

    @Test
    void manualDateIsUsedWhenLegacyHasNoOpenArrival_scenarioD() {
        SkuRestockExpectationResponse before = service.get(SKU_WITHOUT_LEGACY_ARRIVAL);
        assertEquals(SkuRestockExpectationResponse.SOURCE_NONE, before.source());

        LocalDate restockDate = LocalDate.now().plusDays(14);
        service.update(SKU_WITHOUT_LEGACY_ARRIVAL,
                new SkuExpectedRestockRequest(restockDate, false, "supplier promised"), "tester01");

        SkuRestockExpectationResponse after = service.get(SKU_WITHOUT_LEGACY_ARRIVAL);
        assertEquals(SkuRestockExpectationResponse.SOURCE_PORTAL_MANUAL, after.source());
        assertEquals(restockDate, after.date());
        assertEquals("supplier promised", after.manualMemo());
    }

    @Test
    void unknownIsRecordedExplicitly_scenarioE() {
        service.update(SKU_WITHOUT_LEGACY_ARRIVAL, new SkuExpectedRestockRequest(null, true, null), "tester01");

        SkuRestockExpectationResponse result = service.get(SKU_WITHOUT_LEGACY_ARRIVAL);
        assertEquals(SkuRestockExpectationResponse.SOURCE_PORTAL_UNKNOWN, result.source());
        assertNull(result.date());
    }

    @Test
    void unknownTrueWithADateIsRejected() {
        assertThrows(InvalidSkuExpectedRestockException.class, () ->
                service.update(SKU_WITHOUT_LEGACY_ARRIVAL,
                        new SkuExpectedRestockRequest(LocalDate.now(), true, null), "tester01"));
    }

    @Test
    void changeIsAudited_scenarioF() {
        service.update(SKU_WITHOUT_LEGACY_ARRIVAL,
                new SkuExpectedRestockRequest(LocalDate.now().plusDays(7), false, "first"), "tester01");
        service.update(SKU_WITHOUT_LEGACY_ARRIVAL,
                new SkuExpectedRestockRequest(LocalDate.now().plusDays(21), false, "revised"), "tester02");

        List<AuditEventView> events = auditEventRepository.findBySkuCodeOrderByPerformedAtAsc(SKU_WITHOUT_LEGACY_ARRIVAL)
                .stream().map(e -> new AuditEventView(e.getEventType(), null, e.getFieldName(), e.getOldValue(),
                        e.getNewValue(), e.getPerformedBy(), null, e.getPerformedAt()))
                .toList();

        assertEquals(2, events.size());
        assertEquals("tester01", events.get(0).performedBy());
        assertEquals("(none)", events.get(0).oldValue());
        assertTrue(events.get(0).newValue().contains("first"));
        assertEquals("tester02", events.get(1).performedBy());
        assertTrue(events.get(1).oldValue().contains("first"));
        assertTrue(events.get(1).newValue().contains("revised"));
    }

    @Test
    void bulkLookupMatchesSingleLookup() {
        service.update(SKU_WITHOUT_LEGACY_ARRIVAL,
                new SkuExpectedRestockRequest(LocalDate.now().plusDays(5), false, null), "tester01");

        Map<String, SkuRestockExpectationResponse> bulk =
                service.getBulk(List.of(SKU_WITH_LEGACY_ARRIVAL, SKU_WITHOUT_LEGACY_ARRIVAL));

        assertEquals(SkuRestockExpectationResponse.SOURCE_LEGACY, bulk.get(SKU_WITH_LEGACY_ARRIVAL).source());
        assertEquals(SkuRestockExpectationResponse.SOURCE_PORTAL_MANUAL, bulk.get(SKU_WITHOUT_LEGACY_ARRIVAL).source());
    }
}
