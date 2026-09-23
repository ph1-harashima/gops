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

    // --- BR-03 (docs/gulliver-20260917-confirmed-business-rules.md):
    // Cancel is now a two-step Workflow - ACTIVE -> CANCEL_REQUESTED ->
    // CANCELLED, never a direct ACTIVE -> CANCELLED shortcut. ---

    @Test
    void markCancelRequestedRecordsReasonAndRequester() {
        OfficialPoIntegrationRequest r = pending();
        OffsetDateTime now = OffsetDateTime.now();
        r.markCancelRequested("Order cancelled by customer", "operator-tester", now);

        assertEquals(OfficialPoIntegrationRequest.LIFECYCLE_CANCEL_REQUESTED, r.getLifecycleStatus());
        assertEquals("Order cancelled by customer", r.getLifecycleReason());
        assertEquals("operator-tester", r.getLifecycleChangedBy());
        assertEquals("operator-tester", r.getCancelRequestedBy());
        assertEquals(now, r.getCancelRequestedAt());
    }

    @Test
    void markCancelledRequiresAPendingCancelRequestFirst() {
        OfficialPoIntegrationRequest r = pending();
        OffsetDateTime now = OffsetDateTime.now();

        assertThrows(IllegalStateException.class, () -> r.markCancelled("admin-tester", now),
                "Cancel Approval must never be reachable directly from ACTIVE");
    }

    @Test
    void markCancelledRecordsApproverDistinctlyFromRequester() {
        OfficialPoIntegrationRequest r = pending();
        OffsetDateTime requestedAt = OffsetDateTime.now();
        r.markCancelRequested("Order cancelled by customer", "operator-tester", requestedAt);

        OffsetDateTime approvedAt = requestedAt.plusMinutes(5);
        r.markCancelled("admin-tester", approvedAt);

        assertEquals(OfficialPoIntegrationRequest.LIFECYCLE_CANCELLED, r.getLifecycleStatus());
        assertEquals("Order cancelled by customer", r.getLifecycleReason(), "the original Request reason carries through to CANCELLED");
        assertEquals("admin-tester", r.getLifecycleChangedBy(), "lifecycleChangedBy now reflects the APPROVER");
        assertEquals(approvedAt, r.getLifecycleChangedAt());
        assertEquals("operator-tester", r.getCancelRequestedBy(), "the original Requester remains distinctly attributable");
        assertEquals(requestedAt, r.getCancelRequestedAt());
    }

    @Test
    void supersededDocumentCannotBeSupersededOrHaveCancelRequested() {
        OfficialPoIntegrationRequest r = pending();
        OffsetDateTime now = OffsetDateTime.now();
        r.markSuperseded("first", "admin-tester", now);

        assertThrows(IllegalStateException.class, () -> r.markSuperseded("second", "admin-tester", now));
        assertThrows(IllegalStateException.class, () -> r.markCancelRequested("cancel after supersede", "admin-tester", now));
    }

    @Test
    void cancelledDocumentCannotBeSupersededOrHaveCancelRequestedAgain() {
        OfficialPoIntegrationRequest r = pending();
        OffsetDateTime now = OffsetDateTime.now();
        r.markCancelRequested("first", "operator-tester", now);
        r.markCancelled("admin-tester", now);

        assertThrows(IllegalStateException.class, () -> r.markCancelRequested("second", "operator-tester", now));
        assertThrows(IllegalStateException.class, () -> r.markCancelled("admin-tester", now));
        assertThrows(IllegalStateException.class, () -> r.markSuperseded("supersede after cancel", "admin-tester", now));
    }

    // --- G-OPS Operational Workflow Realignment, Phase A (docs/ux-audit/
    // gops-operational-workflow-realignment-implementation.md): the
    // Signature axis - a fourth, independent axis alongside Integration
    // Status/Lifecycle Status above. Critical Design Principle: "Admin
    // approved" must never be conflated with "signed PDF confirmed". ---

    @Test
    void newRequestDefaultsToSignatureNotRequired() {
        OfficialPoIntegrationRequest r = pending();
        assertEquals(OfficialPoIntegrationRequest.SIGNATURE_NOT_REQUIRED, r.getSignatureStatus());
    }

    @Test
    void generatingAPdfMovesSignatureToPending() {
        OfficialPoIntegrationRequest r = pending();
        OffsetDateTime now = OffsetDateTime.now();
        r.resetSignatureForNewPdf(now);
        assertEquals(OfficialPoIntegrationRequest.SIGNATURE_PENDING, r.getSignatureStatus());
    }

    @Test
    void markSignedRecordsFileKeyActorAndTimestamp() {
        OfficialPoIntegrationRequest r = pending();
        OffsetDateTime generatedAt = OffsetDateTime.now();
        r.resetSignatureForNewPdf(generatedAt);

        OffsetDateTime signedAt = generatedAt.plusHours(1);
        r.markSigned("signed/order-1-rev1.pdf", "admin-tester", signedAt);

        assertEquals(OfficialPoIntegrationRequest.SIGNATURE_SIGNED, r.getSignatureStatus());
        assertEquals("signed/order-1-rev1.pdf", r.getSignedPdfFileKey());
        assertEquals("admin-tester", r.getSignedBy());
        assertEquals(signedAt, r.getSignedAt());
    }

    @Test
    void cannotMarkSignedWithoutAPendingPdfFirst() {
        OfficialPoIntegrationRequest r = pending();
        assertThrows(IllegalStateException.class,
                () -> r.markSigned("signed/order-1-rev1.pdf", "admin-tester", OffsetDateTime.now()),
                "NOT_REQUIRED has no unsigned PDF to sign yet");
    }

    @Test
    void cannotMarkSignedTwiceWithoutANewPdfInBetween() {
        OfficialPoIntegrationRequest r = pending();
        OffsetDateTime now = OffsetDateTime.now();
        r.resetSignatureForNewPdf(now);
        r.markSigned("signed/v1.pdf", "admin-tester", now);

        assertThrows(IllegalStateException.class, () -> r.markSigned("signed/v2.pdf", "admin-tester", now),
                "an already-SIGNED Request must not be silently re-signed");
    }

    @Test
    void regeneratingThePdfAfterSigningResetsToUnsignedAndClearsTheOldSignedFile() {
        OfficialPoIntegrationRequest r = pending();
        OffsetDateTime firstGen = OffsetDateTime.now();
        r.resetSignatureForNewPdf(firstGen);
        r.markSigned("signed/v1.pdf", "admin-tester", firstGen.plusMinutes(10));
        assertEquals(OfficialPoIntegrationRequest.SIGNATURE_SIGNED, r.getSignatureStatus());

        // A correction re-generates the formal PDF - the old signature no
        // longer attests to the current document and must not survive.
        OffsetDateTime secondGen = firstGen.plusHours(1);
        r.resetSignatureForNewPdf(secondGen);

        assertEquals(OfficialPoIntegrationRequest.SIGNATURE_PENDING, r.getSignatureStatus());
        assertEquals(null, r.getSignedPdfFileKey());
        assertEquals(null, r.getSignedBy());
        assertEquals(null, r.getSignedAt());
    }

    @Test
    void notReadyToSendUntilSigned() {
        OfficialPoIntegrationRequest r = pending();
        assertEquals(false, r.isReadyToSend());

        r.resetSignatureForNewPdf(OffsetDateTime.now());
        assertEquals(false, r.isReadyToSend(), "a generated-but-unsigned PDF is never sendable");

        r.markSigned("signed/v1.pdf", "admin-tester", OffsetDateTime.now());
        assertEquals(true, r.isReadyToSend());
    }

    @Test
    void aCancelledDocumentIsNeverReadyToSendEvenIfPreviouslySigned() {
        OfficialPoIntegrationRequest r = pending();
        OffsetDateTime now = OffsetDateTime.now();
        r.resetSignatureForNewPdf(now);
        r.markSigned("signed/v1.pdf", "admin-tester", now);
        assertEquals(true, r.isReadyToSend());

        r.markCancelRequested("Order cancelled by customer", "operator-tester", now);
        r.markCancelled("admin-tester", now);

        assertEquals(false, r.isReadyToSend(),
                "a signed PDF for a Document that has since been withdrawn is not sendable");
    }
}
