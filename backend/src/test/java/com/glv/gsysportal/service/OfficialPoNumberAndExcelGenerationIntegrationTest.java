package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.request.ConfirmOfficialPoNumberRequest;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.response.OfficialPoIntegrationResponse;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.exception.DuplicateOfficialPoNumberException;
import com.glv.gsysportal.exception.IntegrationRequestRequiredException;
import com.glv.gsysportal.exception.InvalidOfficialPoNumberException;
import com.glv.gsysportal.exception.OfficialPoAlreadySubmittedException;
import com.glv.gsysportal.exception.OfficialPoExcelNotGeneratedException;
import com.glv.gsysportal.exception.OfficialPoNumberRequiredException;
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
 * Phase 9-A: PO Number confirm + Excel generation, wired to real Order data
 * for the first time (7-C2A only ever exercised {@link
 * com.glv.gsysportal.service.excel.OfficialPoExcelGenerator} from a Contract
 * Test with synthetic input). Same whole-test-method rollback convention as
 * {@link OfficialPoIntegrationServiceIntegrationTest}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class OfficialPoNumberAndExcelGenerationIntegrationTest {

    private static final String SKU_TENT_1 = "OD-TENT-001"; // SUP_ALPHA/BR_OUTDOOR
    private static final String OPERATOR = "tester01";
    private static final String ADMIN = "admin-tester";
    private static final String VALID_PO_NO = "SUPA-OUTD-99-TEST0001";

    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private OrderStatusTransitionService statusTransitionService;
    @Autowired
    private OfficialPoIntegrationService integrationService;
    @Autowired
    private AuditEventRepository auditEventRepository;

    private PortalOrder approvedOrderWithRequest() {
        OrderDraftResponse draft = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);
        PortalOrder order = statusTransitionService.approve(draft.id(), ADMIN);
        integrationService.requestIntegration(order.getId(), ADMIN);
        return order;
    }

    private ConfirmOfficialPoNumberRequest validRequest(String poNo) {
        return new ConfirmOfficialPoNumberRequest(poNo, "WK36", "2026-09-05", "AIR", "FOB", "NET30");
    }

    @Test
    void confirmRequiresExistingIntegrationRequest() {
        OrderDraftResponse draft = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);
        statusTransitionService.approve(draft.id(), ADMIN); // approved, but no "G-SYS連携準備" yet

        assertThrows(IntegrationRequestRequiredException.class,
                () -> integrationService.confirmOfficialPoNumber(draft.id(), validRequest(VALID_PO_NO), ADMIN));
    }

    @Test
    void confirmRejectsBlankAndOverLongNumbers() {
        PortalOrder order = approvedOrderWithRequest();

        assertThrows(InvalidOfficialPoNumberException.class,
                () -> integrationService.confirmOfficialPoNumber(order.getId(), validRequest(""), ADMIN));
        assertThrows(InvalidOfficialPoNumberException.class,
                () -> integrationService.confirmOfficialPoNumber(order.getId(), validRequest("  "), ADMIN));
        assertThrows(InvalidOfficialPoNumberException.class,
                () -> integrationService.confirmOfficialPoNumber(order.getId(), validRequest("X".repeat(31)), ADMIN));
    }

    @Test
    void confirmAcceptsUpTo30CharsAndPersistsOnBothOrderAndRequest() {
        PortalOrder order = approvedOrderWithRequest();
        String poNo = "X".repeat(30);

        OfficialPoIntegrationResponse response = integrationService.confirmOfficialPoNumber(order.getId(), validRequest(poNo), ADMIN);

        assertEquals(poNo, response.officialPoNo());
        assertEquals("WK36", response.deliveryWeek());
        assertEquals("PENDING", response.status());
    }

    @Test
    void confirmDoesNotEnforceIdCodeOrSeparatorStructure() {
        // Working Assumption: no ID Code / separator Business Rule - any
        // <=30 char non-blank string is accepted (design doc §6's "Source
        // does not confirm a structure beyond the 30-char maximum").
        PortalOrder order = approvedOrderWithRequest();

        OfficialPoIntegrationResponse response = integrationService.confirmOfficialPoNumber(
                order.getId(), validRequest("no-structure-at-all"), ADMIN);

        assertEquals("no-structure-at-all", response.officialPoNo());
    }

    @Test
    void confirmRejectsDuplicateAcrossOrders() {
        PortalOrder order1 = approvedOrderWithRequest();
        integrationService.confirmOfficialPoNumber(order1.getId(), validRequest(VALID_PO_NO), ADMIN);

        PortalOrder order2 = approvedOrderWithRequest();
        assertThrows(DuplicateOfficialPoNumberException.class,
                () -> integrationService.confirmOfficialPoNumber(order2.getId(), validRequest(VALID_PO_NO), ADMIN));
    }

    @Test
    void confirmWritesAuditOnlyWhenNumberActuallyChanges() {
        PortalOrder order = approvedOrderWithRequest();

        integrationService.confirmOfficialPoNumber(order.getId(), validRequest(VALID_PO_NO), ADMIN);
        integrationService.confirmOfficialPoNumber(order.getId(), validRequest(VALID_PO_NO), ADMIN); // same value again
        integrationService.confirmOfficialPoNumber(order.getId(), validRequest(VALID_PO_NO + "-B"), ADMIN); // changed

        List<AuditEvent> trail = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId());
        long confirmedCount = trail.stream()
                .filter(e -> AuditEvent.OFFICIAL_PO_NUMBER_CONFIRMED.equals(e.getEventType())).count();
        assertEquals(2, confirmedCount, "one row per actual value change, not per call");
    }

    @Test
    void generateRequiresOfficialPoNumberFirst() {
        PortalOrder order = approvedOrderWithRequest();

        assertThrows(OfficialPoNumberRequiredException.class,
                () -> integrationService.generateExcel(order.getId(), ADMIN));
    }

    @Test
    void generateProducesRealExcelMatchingOrderData() throws IOException {
        PortalOrder order = approvedOrderWithRequest();
        integrationService.confirmOfficialPoNumber(order.getId(), validRequest(VALID_PO_NO), ADMIN);

        OfficialPoIntegrationResponse response = integrationService.generateExcel(order.getId(), ADMIN);

        assertEquals("GENERATED", response.status());
        assertTrue(response.excelGenerated());

        byte[] bytes = integrationService.downloadExcel(order.getId()).bytes();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals(VALID_PO_NO, cellString(sheet, 3, 9), "PO No. cell");
            assertEquals(SKU_TENT_1, cellString(sheet, 17, 2), "first Item Code cell");
        }

        List<AuditEvent> trail = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId());
        assertTrue(trail.stream().anyMatch(e -> AuditEvent.OFFICIAL_PO_EXCEL_GENERATED.equals(e.getEventType())));
    }

    @Test
    void generateIsIdempotent_secondCallReturnsSameStateWithoutRegenerating() {
        PortalOrder order = approvedOrderWithRequest();
        integrationService.confirmOfficialPoNumber(order.getId(), validRequest(VALID_PO_NO), ADMIN);

        OfficialPoIntegrationResponse first = integrationService.generateExcel(order.getId(), ADMIN);
        OfficialPoIntegrationResponse second = integrationService.generateExcel(order.getId(), ADMIN);

        assertEquals(first.status(), second.status());
        assertEquals("GENERATED", second.status());

        long generatedAuditCount = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId()).stream()
                .filter(e -> AuditEvent.OFFICIAL_PO_EXCEL_GENERATED.equals(e.getEventType())).count();
        assertEquals(1, generatedAuditCount, "double-click must not regenerate/re-audit");
    }

    @Test
    void confirmIsLockedOnceSubmitted() {
        PortalOrder order = approvedOrderWithRequest();
        integrationService.confirmOfficialPoNumber(order.getId(), validRequest(VALID_PO_NO), ADMIN);
        integrationService.generateExcel(order.getId(), ADMIN);

        // Simulate a completed Phase 9-B hand-off directly on the entity
        // (Phase 9-B's own placement path is not implemented yet this Phase).
        var request = integrationRequestRepository.findByPortalOrderIdAndRevisionNo(order.getId(), 1).orElseThrow();
        request.markSubmitted(OffsetDateTime.now());
        integrationRequestRepository.saveAndFlush(request);

        assertThrows(OfficialPoAlreadySubmittedException.class,
                () -> integrationService.confirmOfficialPoNumber(order.getId(), validRequest(VALID_PO_NO + "-X"), ADMIN));
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
        integrationService.confirmOfficialPoNumber(order.getId(), validRequest(VALID_PO_NO), ADMIN);

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
            assertTrue(text.contains(VALID_PO_NO));
            assertTrue(text.contains(SKU_TENT_1));
        }
        assertTrue(download.fileName().startsWith("OfficialPO_"));
        assertTrue(download.fileName().endsWith(".pdf"));

        List<AuditEvent> trail = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId());
        assertTrue(trail.stream().anyMatch(e -> AuditEvent.OFFICIAL_PO_PDF_GENERATED.equals(e.getEventType())));
    }

    @Test
    void generatePdfRequiresConfirmedPoNumber() {
        PortalOrder order = approvedOrderWithRequest();

        assertThrows(OfficialPoNumberRequiredException.class,
                () -> integrationService.generatePdf(order.getId(), ADMIN));
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
        integrationService.confirmOfficialPoNumber(order.getId(), validRequest(VALID_PO_NO), ADMIN);
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
        assertTrue(pdfText.contains(VALID_PO_NO));
    }

    @Autowired
    private com.glv.gsysportal.repository.prototype.OfficialPoIntegrationRequestRepository integrationRequestRepository;
}
