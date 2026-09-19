package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.ManufacturerChannel;
import com.glv.gsysportal.domain.MailTemplate;
import com.glv.gsysportal.domain.OfficialPoIntegrationRequest;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.SupplierContact;
import com.glv.gsysportal.dto.request.ConfirmOfficialPoNumberRequest;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.request.CreateRevisionRequest;
import com.glv.gsysportal.dto.request.MailTemplateRequest;
import com.glv.gsysportal.dto.request.ManufacturerChannelRequest;
import com.glv.gsysportal.dto.request.SaveSupplierResponseRequest;
import com.glv.gsysportal.dto.request.SupplierContactRequest;
import com.glv.gsysportal.dto.response.OfficialPoIntegrationResponse;
import com.glv.gsysportal.dto.response.OfficialPoRevisionHistoryEntry;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.dto.response.OrderEmailResponse;
import com.glv.gsysportal.exception.OfficialPoCancelNotAllowedException;
import com.glv.gsysportal.repository.prototype.OfficialPoIntegrationRequestRepository;
import com.glv.gsysportal.service.excel.OfficialPoExcelDownload;
import com.glv.gsysportal.service.excel.OfficialPoPdfDownload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Revision Consistency Audit (docs/gulliver-phase1-revision-consistency-audit.md):
 * order-dependent regression coverage for the Official PO Revision
 * resolution fix. Every scenario here is one the instruction explicitly
 * named (A-F) - each one drives the real Services in an ordering that,
 * before this Audit, would have silently resolved the WRONG Revision for
 * an operation on an already-existing Official PO Document (the same class
 * of bug the Acceptance Fix found and fixed for Email Send, C-2).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class OfficialPoRevisionConsistencyIntegrationTest {

    private static final String SKU = "OD-TENT-001"; // SUP_ALPHA/BR_OUTDOOR
    private static final String OPERATOR = "tester01";
    // A real seeded portal_user (not an arbitrary String) - MailPreviewService
    // (reached via EmailSendService.send in Scenarios B/D) resolves the
    // "From" identity by looking this username up, unlike the other
    // Services this test also drives.
    private static final String ADMIN = "admin01";

    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private OrderStatusTransitionService statusTransitionService;
    @Autowired
    private OfficialPoIntegrationService integrationService;
    @Autowired
    private EmailSendService emailSendService;
    @Autowired
    private SupplierResponseService supplierResponseService;
    @Autowired
    private OrderRevisionService orderRevisionService;
    @Autowired
    private SupplierContactService contactService;
    @Autowired
    private MailTemplateService templateService;
    @Autowired
    private ManufacturerChannelService channelService;
    @Autowired
    private OfficialPoIntegrationRequestRepository integrationRequestRepository;

    private PortalOrder createApprovedOrder() {
        OrderDraftResponse draft = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU), null, null, null), OPERATOR);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);
        return statusTransitionService.approve(draft.id(), ADMIN);
    }

    private void configureEmailChannelContactAndTemplate() {
        channelService.create(new ManufacturerChannelRequest("SUP_ALPHA", "BR_OUTDOOR", ManufacturerChannel.CHANNEL_EMAIL, true), ADMIN);
        contactService.create(new SupplierContactRequest("SUP_ALPHA", "BR_OUTDOOR", "Taro Yamada", "taro@example.com",
                SupplierContact.CONTACT_TYPE_TO, SupplierContact.LANGUAGE_JA, null, null, true, true), ADMIN);
        templateService.create(new MailTemplateRequest("PO Template", MailTemplate.TEMPLATE_TYPE_PURCHASE_ORDER,
                "SUP_ALPHA", "BR_OUTDOOR", SupplierContact.LANGUAGE_JA,
                "PO {{poNo}}", "Dear {{contactName}}, PO No: {{poNo}}", "OFFICIAL_PO_EXCEL", true), ADMIN);
    }

    /** Drives Revision 1 to GENERATED (Official PO No. auto-numbered by
     * requestIntegration itself - BR-08 - Excel generated) - the shared
     * precondition every Scenario below starts from. */
    private PortalOrder issueRevision1() {
        PortalOrder order = createApprovedOrder();
        integrationService.requestIntegration(order.getId(), ADMIN);
        integrationService.confirmOfficialPoNumber(order.getId(),
                new ConfirmOfficialPoNumberRequest("WK36", "2026-09-05", null, null, null), ADMIN);
        integrationService.generateExcel(order.getId(), ADMIN);
        return order;
    }

    /** Full correction -> re-approve -> Reissue cycle (same shape as
     * OfficialPoReissueIntegrationTest's own fullReissueCycle test) -
     * returns the Order after Reissue has created the real Revision 2. */
    private PortalOrder correctAndReissue(Long orderId) {
        PortalOrder sent = statusTransitionService.demoSend(orderId, ADMIN);
        Long detailId = supplierResponseService.getSupplierResponse(sent.getId()).details().get(0).detailId();
        supplierResponseService.saveSupplierResponse(sent.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(new SaveSupplierResponseRequest.LineUpdate(detailId, 2, null, null, null))),
                ADMIN);
        supplierResponseService.confirmSupplierResponse(sent.getId(), ADMIN);
        orderRevisionService.createCorrection(sent.getId(),
                new CreateRevisionRequest("Supplier can only supply 2", true), ADMIN);
        statusTransitionService.submitForApproval(sent.getId(), OPERATOR, false);
        PortalOrder reapproved = statusTransitionService.approve(sent.getId(), ADMIN);
        integrationService.reissue(reapproved.getId(), ADMIN);
        return reapproved;
    }

    // --- Scenario A: 001生成 -> Demo Send -> Excel Download -> PDF Download ---

    @Test
    void scenarioA_demoSendThenDownloads_bothStayOnRevision1() {
        PortalOrder order = issueRevision1();
        String poNo = order.getOfficialPoNo();
        integrationService.generatePdf(order.getId(), ADMIN);

        statusTransitionService.demoSend(order.getId(), ADMIN);

        OfficialPoExcelDownload excel = integrationService.downloadExcel(order.getId());
        OfficialPoPdfDownload pdf = integrationService.downloadPdf(order.getId());
        assertTrue(excel.fileName().contains(poNo) && excel.fileName().endsWith("_001.xlsx"),
                "Excel Download must stay on Revision 1: " + excel.fileName());
        assertTrue(pdf.fileName().contains(poNo) && pdf.fileName().endsWith("_001.pdf"),
                "PDF Download must stay on Revision 1: " + pdf.fileName());
    }

    // --- Scenario B: 001生成 -> Manufacturer Send -> PDF再Download -> Excel再Download ---

    @Test
    void scenarioB_manufacturerSendThenDownloads_staysOnRevision1() {
        PortalOrder order = issueRevision1();
        integrationService.generatePdf(order.getId(), ADMIN);
        configureEmailChannelContactAndTemplate();

        OrderEmailResponse sent = emailSendService.send(order.getId(), ADMIN);
        assertEquals("SENT", sent.status());

        OfficialPoExcelDownload excel = integrationService.downloadExcel(order.getId());
        OfficialPoPdfDownload pdf = integrationService.downloadPdf(order.getId());
        assertTrue(excel.fileName().endsWith("_001.xlsx"), "Excel re-Download must stay on Revision 1: " + excel.fileName());
        assertTrue(pdf.fileName().endsWith("_001.pdf"), "PDF re-Download must stay on Revision 1: " + pdf.fileName());

        // Re-generating Excel/PDF after the Send must also still target
        // Revision 1 (not throw IntegrationRequestRequired, not silently
        // create/target a phantom Revision 2).
        OfficialPoIntegrationResponse excelAgain = integrationService.generateExcel(order.getId(), ADMIN);
        OfficialPoIntegrationResponse pdfAgain = integrationService.generatePdf(order.getId(), ADMIN);
        assertEquals(1, excelAgain.revisionNo());
        assertEquals(1, pdfAgain.revisionNo());
    }

    // --- Scenario C: 001 -> Reissue -> 002 -> 001/002 それぞれ正しいArtifact ---

    @Test
    void scenarioC_reissue_eachRevisionKeepsItsOwnArtifact() {
        PortalOrder order = issueRevision1();
        String poNo = order.getOfficialPoNo();
        integrationService.generatePdf(order.getId(), ADMIN);
        OfficialPoIntegrationRequest rev1Before = integrationRequestRepository
                .findByPortalOrderIdAndRevisionNo(order.getId(), 1).orElseThrow();
        String rev1ExcelKey = rev1Before.getGeneratedFileKey();
        String rev1PdfKey = rev1Before.getPdfFileKey();

        // BR-08: reissue() already carries the SAME Official PO No. forward
        // onto the new Revision - there is no number left to (re)confirm.
        PortalOrder reapproved = correctAndReissue(order.getId());
        integrationService.generateExcel(reapproved.getId(), ADMIN);
        integrationService.generatePdf(reapproved.getId(), ADMIN);

        // Current API only ever downloads the LATEST Revision (there is no
        // per-Revision Download endpoint yet - a new capability, out of
        // this Audit's "Revision consistency only" scope per the
        // instruction's own §9 "変更禁止" list) - confirm it now correctly
        // returns Revision 2's own Artifact, distinct from Revision 1's.
        OfficialPoExcelDownload latestExcel = integrationService.downloadExcel(reapproved.getId());
        OfficialPoPdfDownload latestPdf = integrationService.downloadPdf(reapproved.getId());
        assertTrue(latestExcel.fileName().contains(poNo) && latestExcel.fileName().endsWith("_002.xlsx"));
        assertTrue(latestPdf.fileName().contains(poNo) && latestPdf.fileName().endsWith("_002.pdf"));

        // Revision 1's OWN row (and Artifact reference) is untouched by the
        // Reissue - it is exactly what it was before, at the model level
        // (Revision History §3-C: a past Revision's own Artifact is never
        // silently re-pointed at the new one).
        OfficialPoIntegrationRequest rev1After = integrationRequestRepository
                .findByPortalOrderIdAndRevisionNo(order.getId(), 1).orElseThrow();
        assertEquals(poNo, rev1After.getOfficialPoNo());
        assertEquals(rev1ExcelKey, rev1After.getGeneratedFileKey());
        assertEquals(rev1PdfKey, rev1After.getPdfFileKey());
        assertEquals(OfficialPoIntegrationRequest.LIFECYCLE_SUPERSEDED, rev1After.getLifecycleStatus());

        OfficialPoIntegrationRequest rev2 = integrationRequestRepository
                .findByPortalOrderIdAndRevisionNo(reapproved.getId(), 2).orElseThrow();
        // BR-08 Scenario 7: Revision advances, Official PO No. does not.
        assertEquals(poNo, rev2.getOfficialPoNo(), "Reissue must carry forward the SAME Official PO No.");
        assertNotEquals(rev1ExcelKey, rev2.getGeneratedFileKey());
        assertNotEquals(rev1PdfKey, rev2.getPdfFileKey());
        assertEquals(OfficialPoIntegrationRequest.LIFECYCLE_ACTIVE, rev2.getLifecycleStatus());
    }

    // --- Scenario D: 001 -> Reissue 002 -> 001 SUPERSEDED/002 ACTIVE -> Manufacturer Send targets 002 ---

    @Test
    void scenarioD_manufacturerSendAfterReissue_targetsTheNewActiveRevision() {
        PortalOrder order = issueRevision1();
        configureEmailChannelContactAndTemplate();

        // BR-08: reissue() already carries the Official PO No. forward -
        // nothing left to (re)confirm before generating the new Excel.
        PortalOrder reapproved = correctAndReissue(order.getId());
        integrationService.generateExcel(reapproved.getId(), ADMIN);
        // BR-01: Manufacturer Send now requires BOTH Excel and PDF.
        integrationService.generatePdf(reapproved.getId(), ADMIN);

        OrderEmailResponse sent = emailSendService.send(reapproved.getId(), ADMIN);

        assertEquals("SENT", sent.status());
        assertEquals(2, sent.revisionNo(), "Manufacturer Send after Reissue must target the new ACTIVE Revision 2, not the SUPERSEDED Revision 1");
    }

    // --- Scenario E: 001 -> Demo Send -> Reissue -> 002 (never 003) ---

    @Test
    void scenarioE_demoSendBeforeReissue_revisionNeverOverAdvancesPast2() {
        PortalOrder order = issueRevision1();
        // demoSend already happens inside correctAndReissue - captured here
        // explicitly to name the Scenario precisely: a Demo Send occurred
        // before the correction/Reissue cycle, and must not cause the
        // Reissue to land on Revision 3+ (e.g. if some stray "+1" were
        // applied on top of an already-current+1 computation).
        PortalOrder reapproved = correctAndReissue(order.getId());

        OfficialPoIntegrationResponse current = integrationService.getIntegration(reapproved.getId());
        assertEquals(2, current.revisionNo(), "Reissue after a Demo Send must land exactly on Revision 2, never 3+");

        List<OfficialPoRevisionHistoryEntry> history = integrationService.getRevisionHistory(reapproved.getId());
        assertEquals(2, history.size(), "exactly Revision 1 and 2 must exist - no phantom Revision was created");
    }

    // --- Scenario F: 001 -> 002 -> Cancel 002 -> Revision History保持 ---

    @Test
    void scenarioF_cancelRevision2_bothRevisionsKeepCorrectStateAndHistory() {
        PortalOrder order = issueRevision1();
        String poNo = order.getOfficialPoNo();
        // BR-08: reissue() already carries the Official PO No. forward -
        // nothing left to (re)confirm before generating the new Excel.
        PortalOrder reapproved = correctAndReissue(order.getId());
        integrationService.generateExcel(reapproved.getId(), ADMIN);

        integrationService.requestCancel(reapproved.getId(), "Order cancelled by customer", ADMIN);
        integrationService.approveCancel(reapproved.getId(), ADMIN);

        List<OfficialPoRevisionHistoryEntry> history = integrationService.getRevisionHistory(reapproved.getId());
        assertEquals(2, history.size());
        OfficialPoRevisionHistoryEntry rev1Entry = history.stream().filter(e -> e.revisionNo() == 1).findFirst().orElseThrow();
        OfficialPoRevisionHistoryEntry rev2Entry = history.stream().filter(e -> e.revisionNo() == 2).findFirst().orElseThrow();
        assertEquals(OfficialPoIntegrationRequest.LIFECYCLE_SUPERSEDED, rev1Entry.lifecycleStatus(),
                "Revision 1 keeps its own SUPERSEDED state - Cancel on Revision 2 must not touch it");
        assertEquals(OfficialPoIntegrationRequest.LIFECYCLE_CANCELLED, rev2Entry.lifecycleStatus());
        assertEquals(poNo, rev1Entry.officialPoNo());
        assertEquals(poNo, rev2Entry.officialPoNo(), "BR-08: Reissue never changes the Official PO No., even across Cancel");
        assertTrue(rev2Entry.excelGenerated());

        // A CANCELLED Document is terminal - requesting Cancel again must be
        // rejected, and Revision 1 (already SUPERSEDED, also terminal) was
        // never a valid Cancel target to begin with.
        assertThrows(OfficialPoCancelNotAllowedException.class,
                () -> integrationService.requestCancel(reapproved.getId(), "second attempt", ADMIN));
    }
}
