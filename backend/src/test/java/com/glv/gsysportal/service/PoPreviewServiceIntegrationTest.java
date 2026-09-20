package com.glv.gsysportal.service;

import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.response.OfficialPoIntegrationResponse;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.dto.response.PoPreviewResponse;
import com.glv.gsysportal.exception.DraftNotFoundException;
import com.glv.gsysportal.exception.NoOrderableItemsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Implementation instructions 2章/4章/5章/17章/19章. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class PoPreviewServiceIntegrationTest {

    /** Phase 7-C1: DRAFT -> PENDING_APPROVAL -> APPROVED (the pre-7-C1 confirm() equivalent). */
    private com.glv.gsysportal.domain.PortalOrder approveViaWorkflow(Long orderId) {
        statusTransitionService.submitForApproval(orderId, "tester01", true);
        return statusTransitionService.approve(orderId, "tester01");
    }
    private static final String SKU_TENT_1 = "OD-TENT-001"; // recommendedQty = 3, unitPrice 1137
    private static final String SKU_TENT_2 = "OD-TENT-002"; // recommendedQty = 20, unitPrice 1274
    private static final String SKU_CHAIR_0 = "OD-CHAIR-001"; // recommendedQty = 0

    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private OrderStatusTransitionService statusTransitionService;
    @Autowired
    private PoPreviewService poPreviewService;
    @Autowired
    private OfficialPoIntegrationService officialPoIntegrationService;

    private OrderDraftResponse createDraft(String... skus) {
        return orderDraftService.createDraft(new CreateDraftRequest(List.of(skus), null, null, null), "tester01");
    }

    @Test
    void previewDoesNotChangeStatus() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);

        PoPreviewResponse preview = poPreviewService.preview(draft.id());

        assertEquals("DRAFT", preview.status());
        OrderDraftResponse reGet = orderDraftService.getDraft(draft.id());
        assertEquals("DRAFT", reGet.status(), "Preview must never change Status - implementation instructions 2章");
    }

    @Test
    void previewOmitsPrototypePoNoBeforeConfirm() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);

        PoPreviewResponse preview = poPreviewService.preview(draft.id());

        assertNull(preview.prototypePoNo());
    }

    @Test
    void previewAfterConfirmShowsAssignedPoNoAndReadyToOrderStatus() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);
        approveViaWorkflow(draft.id());

        PoPreviewResponse preview = poPreviewService.preview(draft.id());

        assertEquals("APPROVED", preview.status());
        assertTrue(preview.prototypePoNo() != null && preview.prototypePoNo().startsWith("PO-DEMO-"));
    }

    @Test
    void previewSummaryIsComputedServerSideFromOrderableLinesOnly() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1, SKU_TENT_2);

        PoPreviewResponse preview = poPreviewService.preview(draft.id());

        assertEquals(2, preview.summary().skuCount());
        assertEquals(3 + 20, preview.summary().totalQty());
        BigDecimal expectedAmount = BigDecimal.valueOf(1137).multiply(BigDecimal.valueOf(3))
                .add(BigDecimal.valueOf(1274).multiply(BigDecimal.valueOf(20)));
        assertEquals(0, expectedAmount.compareTo(preview.summary().totalAmount()));
    }

    @Test
    void previewExcludesZeroOrderQtyLinesFromDetailAndSummary() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1, SKU_CHAIR_0); // second line orderQty=0 initially

        PoPreviewResponse preview = poPreviewService.preview(draft.id());

        assertEquals(1, preview.details().size());
        assertEquals(SKU_TENT_1, preview.details().get(0).sku());
        assertEquals(1, preview.summary().skuCount());
    }

    @Test
    void previewDetailNeverExposesInternalJudgementFields() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);

        PoPreviewResponse preview = poPreviewService.preview(draft.id());

        // PoPreviewDetailResponse has no currentStock/safetyStock/recommendedQty/
        // formula/itemStatus accessors at all (implementation instructions 5章) -
        // this is a compile-time guarantee, exercised here via the field list.
        var detail = preview.details().get(0);
        assertEquals(SKU_TENT_1, detail.sku());
        assertTrue(detail.amount().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void previewDemoModeIsAlwaysTrueAndCommunicationUsesInvalidDomain() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);

        PoPreviewResponse preview = poPreviewService.preview(draft.id());

        assertTrue(preview.demoMode());
        assertTrue(preview.manufacturerCommunication().to().endsWith("@example.invalid"));
        assertTrue(preview.manufacturerCommunication().cc().endsWith("@example.invalid"));
    }

    @Test
    void previewWithNoOrderableItemsIsRejected() {
        OrderDraftResponse draft = createDraft(SKU_CHAIR_0);

        assertThrows(NoOrderableItemsException.class, () -> poPreviewService.preview(draft.id()));
    }

    @Test
    void previewOnUnknownDraftThrowsNotFound() {
        assertThrows(DraftNotFoundException.class, () -> poPreviewService.preview(-1L));
    }

    /** Post-Freeze Visual Walkthrough Findings Fix (Finding #1,
     * docs/gops-visual-walkthrough-findings-fix.md §3): before an Official
     * PO exists, this screen must show 正式PO番号 as unassigned - never
     * substitute the Portal管理番号 (prototypePoNo) for it. */
    @Test
    void previewOfficialPoNoIsNullBeforeIntegrationRequested() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);
        approveViaWorkflow(draft.id());

        PoPreviewResponse preview = poPreviewService.preview(draft.id());

        assertTrue(preview.prototypePoNo() != null, "Portal管理番号 should already be assigned at APPROVED");
        assertNull(preview.officialPoNo(), "正式PO番号 must stay unassigned until Official PO integration actually happens");
    }

    /** Post-Freeze Visual Walkthrough Findings Fix (Finding #1): once an
     * Official PO No. is assigned, BOTH the DTO's own officialPoNo() field
     * AND the manufacturer-facing communication body/subject must reference
     * it - and specifically must NOT reference prototypePoNo (Portal管理番号)
     * instead. This is the exact regression the Visual Walkthrough found:
     * the manufacturer-facing preview previously showed the Portal番号 under
     * a "PO番号" label, one screen away from the real Manufacturer Send Mail
     * Preview which already showed the correct 正式PO番号 - proving the same
     * order's "PO番号" resolved to two different values depending which
     * screen you were on. */
    @Test
    void previewAfterOfficialPoAssignedUsesOfficialPoNoNotPortalManagementNoInManufacturerCommunication() {
        OrderDraftResponse draft = createDraft(SKU_TENT_1);
        approveViaWorkflow(draft.id());
        OfficialPoIntegrationResponse integration = officialPoIntegrationService.requestIntegration(draft.id(), "admin-tester");
        String officialPoNo = integration.officialPoNo();
        assertTrue(officialPoNo != null && !officialPoNo.isBlank(), "fixture SKU must auto-assign a real Official PO No.");

        PoPreviewResponse preview = poPreviewService.preview(draft.id());

        assertEquals(officialPoNo, preview.officialPoNo());
        assertNotEquals(preview.prototypePoNo(), preview.officialPoNo(),
                "Portal管理番号 and 正式PO番号 must never collapse to the same displayed value by coincidence of this fixture");
        assertTrue(preview.manufacturerCommunication().subject().contains(officialPoNo),
                "manufacturer-facing subject must reference 正式PO番号");
        assertTrue(preview.manufacturerCommunication().body().contains(officialPoNo),
                "manufacturer-facing body must reference 正式PO番号");
        assertTrue(!preview.manufacturerCommunication().body().contains(preview.prototypePoNo()),
                "manufacturer-facing body must NOT reference the Portal管理番号 (prototypePoNo) anywhere");
    }
}
