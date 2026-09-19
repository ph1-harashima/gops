package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.request.ConfirmOfficialPoNumberRequest;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.response.OfficialPoIntegrationResponse;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.exception.IntegrationRequestRequiredException;
import com.glv.gsysportal.exception.OfficialPoAlreadySubmittedException;
import com.glv.gsysportal.exception.OfficialPoExcelNotGeneratedException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 9-A / BR-08 (docs/gulliver-20260917-confirmed-business-rules.md):
 * Excel/PDF generation, wired to real Order data (7-C2A only ever exercised
 * {@link com.glv.gsysportal.service.excel.OfficialPoExcelGenerator} from a
 * Contract Test with synthetic input). The Official PO No. itself is now
 * always auto-numbered by {@link #approvedOrderWithRequest} (via
 * {@code requestIntegration} -> {@link OfficialPoNumberGenerator}) - the
 * manual-entry validation this class originally tested (blank/over-long
 * rejection, duplicate-across-Orders rejection, ID-structure non-enforcement)
 * no longer has a reachable code path under BR-08 and was removed; the
 * auto-numbering behavior itself (format, per-Supplier x Brand sequence,
 * Concurrency Control) is covered by {@link OfficialPoAutoNumberingIntegrationTest}
 * instead. Same whole-test-method rollback convention as
 * {@link OfficialPoIntegrationServiceIntegrationTest}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class OfficialPoNumberAndExcelGenerationIntegrationTest {

    private static final String SKU_TENT_1 = "OD-TENT-001"; // SUP_ALPHA/BR_OUTDOOR
    private static final String OPERATOR = "tester01";
    private static final String ADMIN = "admin-tester";

    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private OrderStatusTransitionService statusTransitionService;
    @Autowired
    private OfficialPoIntegrationService integrationService;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private com.glv.gsysportal.repository.prototype.OfficialPoIntegrationRequestRepository integrationRequestRepository;

    private PortalOrder approvedOrderWithRequest() {
        OrderDraftResponse draft = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);
        PortalOrder order = statusTransitionService.approve(draft.id(), ADMIN);
        // BR-08: this already auto-numbers the Official PO No. - no manual
        // entry step exists anymore.
        integrationService.requestIntegration(order.getId(), ADMIN);
        return order;
    }

    private static ConfirmOfficialPoNumberRequest deliveryDetails() {
        return new ConfirmOfficialPoNumberRequest("WK36", "2026-09-05", "AIR", "FOB", "NET30");
    }

    @Test
    void confirmRequiresExistingIntegrationRequest() {
        OrderDraftResponse draft = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);
        statusTransitionService.approve(draft.id(), ADMIN); // approved, but no "G-SYS連携準備" yet

        assertThrows(IntegrationRequestRequiredException.class,
                () -> integrationService.confirmOfficialPoNumber(draft.id(), deliveryDetails(), ADMIN));
    }

    @Test
    void requestIntegrationAutoNumbersImmediately() {
        PortalOrder order = approvedOrderWithRequest();

        assertTrue(order.getOfficialPoNo() != null && !order.getOfficialPoNo().isBlank(),
                "BR-08: Official PO No. must already be assigned as soon as G-SYS連携準備 runs");
    }

    @Test
    void confirmDeliveryDetailsNeverChangesTheAutoNumberedPoNo() {
        PortalOrder order = approvedOrderWithRequest();
        String assignedPoNo = order.getOfficialPoNo();

        OfficialPoIntegrationResponse response = integrationService.confirmOfficialPoNumber(order.getId(), deliveryDetails(), ADMIN);

        assertEquals(assignedPoNo, response.officialPoNo(), "confirming delivery details must never re-number the Official PO No.");
        assertEquals("WK36", response.deliveryWeek());
        assertEquals("PENDING", response.status());
    }

    @Test
    void generateProducesRealExcelMatchingOrderData() throws IOException {
        PortalOrder order = approvedOrderWithRequest();
        String poNo = order.getOfficialPoNo();
        integrationService.confirmOfficialPoNumber(order.getId(), deliveryDetails(), ADMIN);

        OfficialPoIntegrationResponse response = integrationService.generateExcel(order.getId(), ADMIN);

        assertEquals("GENERATED", response.status());
        assertTrue(response.excelGenerated());

        byte[] bytes = integrationService.downloadExcel(order.getId()).bytes();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals(poNo, cellString(sheet, 3, 9), "PO No. cell");
            assertEquals(SKU_TENT_1, cellString(sheet, 17, 2), "first Item Code cell");
        }

        List<AuditEvent> trail = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId());
        assertTrue(trail.stream().anyMatch(e -> AuditEvent.OFFICIAL_PO_EXCEL_GENERATED.equals(e.getEventType())));
    }

    @Test
    void generateIsIdempotent_secondCallReturnsSameStateWithoutRegenerating() {
        PortalOrder order = approvedOrderWithRequest();
        integrationService.confirmOfficialPoNumber(order.getId(), deliveryDetails(), ADMIN);

        OfficialPoIntegrationResponse first = integrationService.generateExcel(order.getId(), ADMIN);
        OfficialPoIntegrationResponse second = integrationService.generateExcel(order.getId(), ADMIN);

        assertEquals(first.status(), second.status());
        assertEquals("GENERATED", second.status());

        long generatedAuditCount = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId()).stream()
                .filter(e -> AuditEvent.OFFICIAL_PO_EXCEL_GENERATED.equals(e.getEventType())).count();
        assertEquals(1, generatedAuditCount, "double-click must not regenerate/re-audit");
    }

    @Test
    void confirmDeliveryDetailsIsLockedOnceSubmitted() {
        PortalOrder order = approvedOrderWithRequest();
        integrationService.confirmOfficialPoNumber(order.getId(), deliveryDetails(), ADMIN);
        integrationService.generateExcel(order.getId(), ADMIN);

        // Simulate a completed Phase 9-B hand-off directly on the entity
        // (Phase 9-B's own placement path is not implemented yet this Phase).
        var request = integrationRequestRepository.findByPortalOrderIdAndRevisionNo(order.getId(), 1).orElseThrow();
        request.markSubmitted(OffsetDateTime.now());
        integrationRequestRepository.saveAndFlush(request);

        assertThrows(OfficialPoAlreadySubmittedException.class,
                () -> integrationService.confirmOfficialPoNumber(order.getId(),
                        new ConfirmOfficialPoNumberRequest("WK40", "2026-10-01", "AIR", "FOB", "NET30"), ADMIN));
    }

    @Test
    void downloadBeforeGenerationThrows() {
        PortalOrder order = approvedOrderWithRequest();

        assertThrows(OfficialPoExcelNotGeneratedException.class,
                () -> integrationService.downloadExcel(order.getId()));
    }

    private static String cellString(Sheet sheet, int rowIdx, int colIdx) {
        Row row = sheet.getRow(rowIdx);
        assertFalse(row == null, "row " + rowIdx + " must exist");
        var cell = row.getCell(colIdx);
        assertFalse(cell == null, "cell " + rowIdx + "," + colIdx + " must exist");
        return cell.getStringCellValue();
    }

    // --- Gap Analysis C-1 (docs/gulliver-20260917-phase1-gap-analysis.md 7章): PDF ---

    @Test
    void generatePdfProducesRealPdfMatchingOrderData() throws IOException {
        PortalOrder order = approvedOrderWithRequest();
        String poNo = order.getOfficialPoNo();
        integrationService.confirmOfficialPoNumber(order.getId(), deliveryDetails(), ADMIN);

        OfficialPoIntegrationResponse response = integrationService.generatePdf(order.getId(), ADMIN);

        assertTrue(response.pdfGenerated());
        // Independent of the Excel/Import-Folder Integration Status axis -
        // no Excel has been generated in this test at all, yet PDF still
        // works (7章's "PDF never drives the Integration Status axis").
        assertEquals("PENDING", response.status());

        var download = integrationService.downloadPdf(order.getId());
        try (org.apache.pdfbox.pdmodel.PDDocument pdf = org.apache.pdfbox.pdmodel.PDDocument.load(
                new ByteArrayInputStream(download.bytes()))) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
            assertTrue(text.contains(poNo));
            assertTrue(text.contains(SKU_TENT_1));
        }
        assertTrue(download.fileName().startsWith("OfficialPO_"));
        assertTrue(download.fileName().endsWith(".pdf"));

        List<AuditEvent> trail = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId());
        assertTrue(trail.stream().anyMatch(e -> AuditEvent.OFFICIAL_PO_PDF_GENERATED.equals(e.getEventType())));
    }

    @Test
    void downloadPdfBeforeGenerationThrows() {
        PortalOrder order = approvedOrderWithRequest();

        assertThrows(com.glv.gsysportal.exception.OfficialPoPdfNotGeneratedException.class,
                () -> integrationService.downloadPdf(order.getId()));
    }

    @Test
    void pdfAndExcelAgreeOnQuantityAndPoInfo_sameBusinessDataSource() throws IOException {
        PortalOrder order = approvedOrderWithRequest();
        String poNo = order.getOfficialPoNo();
        integrationService.confirmOfficialPoNumber(order.getId(), deliveryDetails(), ADMIN);
        integrationService.generateExcel(order.getId(), ADMIN);
        integrationService.generatePdf(order.getId(), ADMIN);

        byte[] excelBytes = integrationService.downloadExcel(order.getId()).bytes();
        byte[] pdfBytes = integrationService.downloadPdf(order.getId()).bytes();

        String excelSku;
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            excelSku = cellString(wb.getSheetAt(0), 17, 2);
        }
        String pdfText;
        try (org.apache.pdfbox.pdmodel.PDDocument pdf = org.apache.pdfbox.pdmodel.PDDocument.load(
                new ByteArrayInputStream(pdfBytes))) {
            pdfText = new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
        }

        assertEquals(SKU_TENT_1, excelSku);
        assertTrue(pdfText.contains(excelSku), "Excel and PDF must show the SAME SKU - one Business Data Source");
        assertTrue(pdfText.contains(poNo));
    }
}
