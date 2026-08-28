package com.glv.gsysportal.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phase 7-C2A 16章/17章/24章: State Transition Test. Proves the
 * PENDING -> GENERATED -> SUBMITTED -> CONFIRMED / FAILED State Model is
 * well-formed at the entity level, even though NO Controller/Service path in
 * this Phase ever calls these transition methods (7-C2A 16章: "UI/通常API
 * から偽のCONFIRMEDを作れないこと" - verified structurally by there being no
 * caller of markConfirmed/markSubmitted/markGenerated anywhere outside this
 * test and OfficialPoExcelGeneratorContractTest's neighbors). Pure unit
 * test, no Spring context.
 */
class OfficialPoIntegrationRequestTest {

    private static OfficialPoIntegrationRequest pending() {
        OfficialPoIntegrationRequest r = new OfficialPoIntegrationRequest();
        r.setStatus(OfficialPoIntegrationRequest.STATUS_PENDING);
        return r;
    }

    @Test
    void validForwardPathPendingToGeneratedToSubmittedToConfirmed() {
        OfficialPoIntegrationRequest r = pending();
        OffsetDateTime now = OffsetDateTime.now();

        r.markGenerated("s3://test/key", now);
        assertEquals(OfficialPoIntegrationRequest.STATUS_GENERATED, r.getStatus());

        r.markSubmitted(now);
        assertEquals(OfficialPoIntegrationRequest.STATUS_SUBMITTED, r.getStatus());

        r.markConfirmed(now);
        assertEquals(OfficialPoIntegrationRequest.STATUS_CONFIRMED, r.getStatus());
    }

    @Test
    void generatedCanFail() {
        OfficialPoIntegrationRequest r = pending();
        OffsetDateTime now = OffsetDateTime.now();
        r.markGenerated("s3://test/key", now);

        r.markFailed("SUBMIT_ERROR", "boom", now);

        assertEquals(OfficialPoIntegrationRequest.STATUS_FAILED, r.getStatus());
        assertEquals(1, r.getRetryCount());
    }

    @Test
    void submittedCanFail() {
        OfficialPoIntegrationRequest r = pending();
        OffsetDateTime now = OffsetDateTime.now();
        r.markGenerated("s3://test/key", now);
        r.markSubmitted(now);

        r.markFailed("CONFIRM_TIMEOUT", "no TR_PO match within window", now);

        assertEquals(OfficialPoIntegrationRequest.STATUS_FAILED, r.getStatus());
    }

    @Test
    void cannotSkipGeneratedAndGoStraightToSubmitted() {
        OfficialPoIntegrationRequest r = pending();
        assertThrows(IllegalStateException.class, () -> r.markSubmitted(OffsetDateTime.now()));
    }

    @Test
    void cannotSkipSubmittedAndGoStraightToConfirmed() {
        OfficialPoIntegrationRequest r = pending();
        r.markGenerated("s3://test/key", OffsetDateTime.now());
        assertThrows(IllegalStateException.class, () -> r.markConfirmed(OffsetDateTime.now()));
    }

    @Test
    void cannotFailDirectlyFromPending_preflightBlockedIsNotAnIntegrationFailure() {
        // 7-C2A 18章: Preflight BLOCKED never touches this State Model at all
        // (the Request just stays PENDING with preflightResult=BLOCKED) - it
        // is not modeled as FAILED, because no Legacy submission was ever
        // attempted.
        OfficialPoIntegrationRequest r = pending();
        assertThrows(IllegalStateException.class, () -> r.markFailed("X", "Y", OffsetDateTime.now()));
    }

    @Test
    void cannotGenerateTwice() {
        OfficialPoIntegrationRequest r = pending();
        r.markGenerated("s3://test/key", OffsetDateTime.now());
        assertThrows(IllegalStateException.class, () -> r.markGenerated("s3://test/key2", OffsetDateTime.now()));
    }
}
