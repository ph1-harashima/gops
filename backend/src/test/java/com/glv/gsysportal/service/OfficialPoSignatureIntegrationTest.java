package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.OfficialPoIntegrationRequest;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.response.OfficialPoIntegrationResponse;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.exception.OfficialPoSignaturePendingRequiredException;
import com.glv.gsysportal.exception.SignedOfficialPoNotAvailableException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.service.excel.OfficialPoPdfDownload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-OPS Operational Workflow Realignment Phase C (docs/ux-audit/
 * gops-operational-workflow-realignment-implementation.md §7-2): the
 * Signature axis wired to a real Order/PDF, exercising {@code
 * OfficialPoIntegrationService#registerSignedPdf}/{@code
 * #downloadSignedPdf} against the real {@code LocalFilesystemSignedOfficialPoAdapter}
 * (test profile) rather than mocking storage. Mirrors {@code
 * OfficialPoNumberAndExcelGenerationIntegrationTest}'s own fixture shape.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class OfficialPoSignatureIntegrationTest {

    private static final String SKU_TENT_1 = "OD-TENT-001"; // SUP_ALPHA/BR_OUTDOOR
    private static final String OPERATOR = "tester01";
    private static final String ADMIN = "admin-tester";
    private static final byte[] FAKE_SIGNED_PDF_BYTES = "%PDF-1.4 fake signed content for test".getBytes();

    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private OrderStatusTransitionService statusTransitionService;
    @Autowired
    private OfficialPoIntegrationService integrationService;
    @Autowired
    private AuditEventRepository auditEventRepository;

    private PortalOrder approvedOrderWithGeneratedPdf() {
        OrderDraftResponse draft = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);
        PortalOrder order = statusTransitionService.approve(draft.id(), ADMIN);
        integrationService.requestIntegration(order.getId(), ADMIN);
        integrationService.generatePdf(order.getId(), ADMIN);
        return order;
    }

    @Test
    void newIntegrationRequestDefaultsToSignatureNotRequired() {
        OrderDraftResponse draft = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);
        PortalOrder order = statusTransitionService.approve(draft.id(), ADMIN);
        OfficialPoIntegrationResponse response = integrationService.requestIntegration(order.getId(), ADMIN);

        assertEquals(OfficialPoIntegrationRequest.SIGNATURE_NOT_REQUIRED, response.signatureStatus());
        assertFalse(response.signedPdfAvailable());
        assertFalse(response.readyToSend(), "Admin approval + G-SYS連携準備 alone must never imply ready-to-send");
    }

    @Test
    void generatingThePdfMovesSignatureToPendingAndRecordsAuditEvent() {
        PortalOrder order = approvedOrderWithGeneratedPdf();

        OfficialPoIntegrationResponse response = integrationService.getIntegration(order.getId());
        assertEquals(OfficialPoIntegrationRequest.SIGNATURE_PENDING, response.signatureStatus());
        assertFalse(response.readyToSend(), "a generated-but-unsigned PDF is never ready to send");
        assertTrue(auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId()).stream()
                .anyMatch(e -> AuditEvent.OFFICIAL_PO_SIGNATURE_PENDING.equals(e.getEventType())));
    }

    @Test
    void cannotRegisterASignedPdfBeforeTheFormalPdfIsGenerated() {
        OrderDraftResponse draft = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);
        PortalOrder order = statusTransitionService.approve(draft.id(), ADMIN);
        integrationService.requestIntegration(order.getId(), ADMIN); // no generatePdf call

        assertThrows(OfficialPoSignaturePendingRequiredException.class,
                () -> integrationService.registerSignedPdf(order.getId(), FAKE_SIGNED_PDF_BYTES, ADMIN));
    }

    @Test
    void registeringASignedPdfMovesToSignedAndIsReadyToSend() {
        PortalOrder order = approvedOrderWithGeneratedPdf();

        OfficialPoIntegrationResponse response = integrationService.registerSignedPdf(order.getId(), FAKE_SIGNED_PDF_BYTES, ADMIN);

        assertEquals(OfficialPoIntegrationRequest.SIGNATURE_SIGNED, response.signatureStatus());
        assertTrue(response.signedPdfAvailable());
        assertTrue(response.readyToSend());
        assertTrue(auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId()).stream()
                .anyMatch(e -> AuditEvent.OFFICIAL_PO_SIGNED.equals(e.getEventType())));
    }

    @Test
    void downloadedSignedPdfMatchesWhatWasUploaded() {
        PortalOrder order = approvedOrderWithGeneratedPdf();
        integrationService.registerSignedPdf(order.getId(), FAKE_SIGNED_PDF_BYTES, ADMIN);

        OfficialPoPdfDownload download = integrationService.downloadSignedPdf(order.getId());

        assertArrayEquals(FAKE_SIGNED_PDF_BYTES, download.bytes());
        assertTrue(download.fileName().contains("signed"));
    }

    @Test
    void downloadingSignedPdfBeforeRegistrationThrows() {
        PortalOrder order = approvedOrderWithGeneratedPdf();

        assertThrows(SignedOfficialPoNotAvailableException.class,
                () -> integrationService.downloadSignedPdf(order.getId()));
    }

    @Test
    void cannotRegisterASignedPdfTwiceWithoutRegeneratingThePdfFirst() {
        PortalOrder order = approvedOrderWithGeneratedPdf();
        integrationService.registerSignedPdf(order.getId(), FAKE_SIGNED_PDF_BYTES, ADMIN);

        assertThrows(OfficialPoSignaturePendingRequiredException.class,
                () -> integrationService.registerSignedPdf(order.getId(), FAKE_SIGNED_PDF_BYTES, ADMIN),
                "an already-SIGNED Request must not be silently re-signed");
    }

    /** Critical Design Principle, end-to-end: a correction that regenerates
     * the formal PDF invalidates the prior signature - the old signed
     * artifact must no longer be treated as current. */
    @Test
    void regeneratingThePdfAfterSigningRequiresANewSignature() {
        PortalOrder order = approvedOrderWithGeneratedPdf();
        integrationService.registerSignedPdf(order.getId(), FAKE_SIGNED_PDF_BYTES, ADMIN);
        assertTrue(integrationService.getIntegration(order.getId()).readyToSend());

        integrationService.generatePdf(order.getId(), ADMIN); // correction re-generate

        OfficialPoIntegrationResponse afterRegen = integrationService.getIntegration(order.getId());
        assertEquals(OfficialPoIntegrationRequest.SIGNATURE_PENDING, afterRegen.signatureStatus());
        assertFalse(afterRegen.signedPdfAvailable());
        assertFalse(afterRegen.readyToSend());
        assertThrows(SignedOfficialPoNotAvailableException.class,
                () -> integrationService.downloadSignedPdf(order.getId()),
                "the old signed PDF must no longer be downloadable as if it were current");
    }
}
