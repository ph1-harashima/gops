package com.glv.gsysportal.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phase 7-C2A 16章/17章/24章: State Transition Test. Proves the
 * PENDING -> GENERATED -> SUBMITTED -> CONFIRMED / FAILED State Model is
 * well-formed at the entity level. 7-C2A itself never called these
 * transition methods from any real Controller/Service path (7-C2A 16章:
 * "UI/通常APIから偽のCONFIRMEDを作れないこと") - Phase 9-A/9-B/9-C
 * (Production PO Workflow) added the first real callers
 * (OfficialPoIntegrationService.generateExcel/placeToImportFolder/confirmImport),
 * all still gated by their own Business Rules (a real Excel must actually
 * exist before GENERATED, a real Legacy TR_PO match before CONFIRMED,
 * etc.) - this class stays the entity-level regression proving the State
 * Model itself cannot be driven out of order regardless of caller. Pure
 * unit test, no Spring context.
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

    /** Phase 9-B §7 "Retry時の重複処理防止": a placement attempt that
     * previously FAILED can retry straight to SUBMITTED - the Excel itself
     * is never regenerated, so this Request never needs to revisit
     * GENERATED just to retry. */
    @Test
    void failedCanRetryDirectlyToSubmitted() {
        OfficialPoIntegrationRequest r = pending();
        OffsetDateTime now = OffsetDateTime.now();
        r.markGenerated("s3://test/key", now);
        r.markSubmitted(now);
        r.markFailed("CONFIRM_TIMEOUT", "no TR_PO match within window", now);
        assertEquals(1, r.getRetryCount());

        r.markSubmitted(now);

        assertEquals(OfficialPoIntegrationRequest.STATUS_SUBMITTED, r.getStatus());
    }

    /** A retry itself can fail again - retryCount keeps incrementing, never
     * resets, so it always reflects the true attempt count. */
    @Test
    void retryCountIncrementsOnEachFailure() {
        OfficialPoIntegrationRequest r = pending();
        OffsetDateTime now = OffsetDateTime.now();
        r.markGenerated("s3://test/key", now);
        r.markSubmitted(now);
        r.markFailed("ERR_1", "first failure", now);
        r.markSubmitted(now);
        r.markFailed("ERR_2", "second failure", now);

        assertEquals(2, r.getRetryCount());
        assertEquals("ERR_2", r.getErrorCode());
    }

    // --- Gap Analysis C-2/C-4 (docs/gulliver-20260917-phase1-gap-analysis.md
    // 7章): Document lifecycle - entirely separate axis from the Integration
    // Status transitions above. ---

    @Test
    void newRequestDefaultsToActiveLifecycle() {
        OfficialPoIntegrationRequest r = pending();
        assertEquals(OfficialPoIntegrationRequest.LIFECYCLE_ACTIVE, r.getLifecycleStatus());
    }

    @Test
    void markSupersededRecordsReasonAndActor() {
        OfficialPoIntegrationRequest r = pending();
        OffsetDateTime now = OffsetDateTime.now();
        r.markSuperseded("Reissued for Revision 2", "admin-tester", now);

        assertEquals(OfficialPoIntegrationRequest.LIFECYCLE_SUPERSEDED, r.getLifecycleStatus());
        assertEquals("Reissued for Revision 2", r.getLifecycleReason());
        assertEquals("admin-tester", r.getLifecycleChangedBy());
        assertEquals(now, r.getLifecycleChangedAt());
    }

    @Test
    void markCancelledRecordsReasonAndActor() {
        OfficialPoIntegrationRequest r = pending();
        OffsetDateTime now = OffsetDateTime.now();
        r.markCancelled("Order cancelled by customer", "admin-tester", now);

        assertEquals(OfficialPoIntegrationRequest.LIFECYCLE_CANCELLED, r.getLifecycleStatus());
        assertEquals("Order cancelled by customer", r.getLifecycleReason());
    }

    @Test
    void supersededDocumentCannotBeSupersededOrCancelledAgain() {
        OfficialPoIntegrationRequest r = pending();
        OffsetDateTime now = OffsetDateTime.now();
        r.markSuperseded("first", "admin-tester", now);

        assertThrows(IllegalStateException.class, () -> r.markSuperseded("second", "admin-tester", now));
        assertThrows(IllegalStateException.class, () -> r.markCancelled("cancel after supersede", "admin-tester", now));
    }

    @Test
    void cancelledDocumentCannotBeSupersededOrCancelledAgain() {
        OfficialPoIntegrationRequest r = pending();
        OffsetDateTime now = OffsetDateTime.now();
        r.markCancelled("first", "admin-tester", now);

        assertThrows(IllegalStateException.class, () -> r.markCancelled("second", "admin-tester", now));
        assertThrows(IllegalStateException.class, () -> r.markSuperseded("supersede after cancel", "admin-tester", now));
    }
}
