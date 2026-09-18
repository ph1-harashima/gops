package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.MailTemplate;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.SupplierContact;
import com.glv.gsysportal.dto.request.ConfirmOfficialPoNumberRequest;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.request.MailTemplateRequest;
import com.glv.gsysportal.dto.request.SupplierContactRequest;
import com.glv.gsysportal.dto.response.MailPreviewResponse;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 7-C3 9章/10章: Mail Preview - Blocker/Warning behavior, and the
 * fully-rendered path once every input IS resolved (using a Test-only
 * Official PO No. set directly via the Repository, mirroring
 * OfficialPoExcelGeneratorContractTest's own "Test-only Official PO No." -
 * never a value any real Controller/Service path this Phase can produce). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class MailPreviewServiceIntegrationTest {

    private static final String SKU_TENT_1 = "OD-TENT-001"; // SUP_ALPHA/BR_OUTDOOR
    private static final String OPERATOR = "tester01";
    // Must be a REAL seeded portal_user (V8 migration), not the free-text
    // "admin-tester" convention other Service tests use for performedBy -
    // MailPreviewService looks up the actual PortalUser row for sender
    // displayName/email (senderName/senderEmail template variables).
    private static final String ADMIN = "admin01";

    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private OrderStatusTransitionService statusTransitionService;
    @Autowired
    private MailPreviewService mailPreviewService;
    @Autowired
    private SupplierContactService contactService;
    @Autowired
    private MailTemplateService templateService;
    @Autowired
    private PortalOrderRepository portalOrderRepository;
    @Autowired
    private OfficialPoIntegrationService officialPoIntegrationService;

    private PortalOrder createApprovedOrder() {
        OrderDraftResponse draft = orderDraftService.createDraft(
                new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), OPERATOR);
        statusTransitionService.submitForApproval(draft.id(), OPERATOR, false);
        return statusTransitionService.approve(draft.id(), ADMIN);
    }

    @Test
    void officialPoNoNotAssignedIsAlwaysBlockedThisPhase() {
        PortalOrder order = createApprovedOrder();

        MailPreviewResponse preview = mailPreviewService.preview(order.getId(), ADMIN);

        assertTrue(preview.issues().stream().anyMatch(i -> "OFFICIAL_PO_NO_NOT_ASSIGNED".equals(i.code())
                && "BLOCKED".equals(i.severity())));
        assertNull(preview.subject());
        assertNull(preview.body());
    }

    @Test
    void noContactConfiguredIsBlocked() {
        PortalOrder order = createApprovedOrder();

        MailPreviewResponse preview = mailPreviewService.preview(order.getId(), ADMIN);

        assertTrue(preview.issues().stream().anyMatch(i -> "SUPPLIER_CONTACT_NOT_FOUND".equals(i.code())));
    }

    @Test
    void noTemplateConfiguredIsBlocked() {
        PortalOrder order = createApprovedOrder();
        contactService.create(new SupplierContactRequest("SUP_ALPHA", "BR_OUTDOOR", "Contact", "contact@example.com",
                SupplierContact.CONTACT_TYPE_TO, SupplierContact.LANGUAGE_JA, null, null, true, true), ADMIN);

        MailPreviewResponse preview = mailPreviewService.preview(order.getId(), ADMIN);

        assertTrue(preview.issues().stream().anyMatch(i -> "MAIL_TEMPLATE_NOT_FOUND".equals(i.code())));
    }

    @Test
    void fullyResolvedInputsRenderSubjectAndBodyWithVariablesSubstituted() {
        PortalOrder order = createApprovedOrder();
        contactService.create(new SupplierContactRequest("SUP_ALPHA", "BR_OUTDOOR", "Taro Yamada", "taro@example.com",
                SupplierContact.CONTACT_TYPE_TO, SupplierContact.LANGUAGE_JA, null, null, true, true), ADMIN);
        templateService.create(new MailTemplateRequest("PO Template", MailTemplate.TEMPLATE_TYPE_PURCHASE_ORDER,
                "SUP_ALPHA", "BR_OUTDOOR", SupplierContact.LANGUAGE_JA,
                "PO {{poNo}} / Rev {{revisionNo}}",
                "{{contactName}} 様\n\n{{supplierName}} 御中\nPO No: {{poNo}}\nFrom: {{senderName}} <{{senderEmail}}>",
                "OFFICIAL_PO_EXCEL", true), ADMIN);

        // Test-only Official PO No. - see class Javadoc for why this is safe.
        order.setOfficialPoNo("TSUP-TBR-01");
        portalOrderRepository.save(order);

        MailPreviewResponse preview = mailPreviewService.preview(order.getId(), ADMIN);

        assertFalse(preview.issues().stream().anyMatch(i -> "BLOCKED".equals(i.severity())));
        assertEquals("PO TSUP-TBR-01 / Rev 1", preview.subject());
        assertTrue(preview.body().contains("Taro Yamada 様"));
        assertTrue(preview.body().contains("PO No: TSUP-TBR-01"));
        assertEquals(List.of("taro@example.com"), preview.to());
        assertEquals("OFFICIAL_PO_EXCEL", preview.attachment().type());
        assertEquals("TSUP-TBR-01.xlsx", preview.attachment().fileName());
        // officialPoNo is set directly on the entity here (no
        // official_po_integration_request row exists for this Order), so
        // generated() correctly reflects "no Excel has actually been
        // generated yet" - see attachmentGeneratedReflectsRealExcelGenerationState
        // below for the true/has-been-generated case.
        assertFalse(preview.attachment().generated());
    }

    /** Regression test for the Attachment表示 fix (audit finding: "未生成"
     * stayed hardcoded even after Official PO Excel生成) - drives the real
     * G-SYS連携準備 -> 正式PO番号確定 -> Excel生成 chain (not a direct entity
     * write, unlike the Test-only officialPoNo set above) so
     * attachment().generated() reflects the actual generated_file_key state. */
    @Test
    void attachmentGeneratedReflectsRealExcelGenerationState() {
        PortalOrder order = createApprovedOrder();
        contactService.create(new SupplierContactRequest("SUP_ALPHA", "BR_OUTDOOR", "Taro Yamada", "taro@example.com",
                SupplierContact.CONTACT_TYPE_TO, SupplierContact.LANGUAGE_JA, null, null, true, true), ADMIN);
        templateService.create(new MailTemplateRequest("PO Template 2", MailTemplate.TEMPLATE_TYPE_PURCHASE_ORDER,
                "SUP_ALPHA", "BR_OUTDOOR", SupplierContact.LANGUAGE_JA,
                "PO {{poNo}}", "{{contactName}} 様\nPO No: {{poNo}}", "OFFICIAL_PO_EXCEL", true), ADMIN);

        var integration = officialPoIntegrationService.requestIntegration(order.getId(), ADMIN);
        // Display Name統一 (audit finding: "申請者: admin01" showed the raw
        // Login ID) - requestedByDisplayName resolves the real portal_user
        // row for the "admin01" seeded fixture, same idiom as
        // AuditEventView.performedByDisplayName.
        assertEquals(ADMIN, integration.requestedBy());
        assertTrue(integration.requestedByDisplayName() != null && !integration.requestedByDisplayName().equals(ADMIN));
        officialPoIntegrationService.confirmOfficialPoNumber(order.getId(),
                new ConfirmOfficialPoNumberRequest("TSUP-TBR-02", null, null, null, null, null), ADMIN);

        MailPreviewResponse beforeGenerate = mailPreviewService.preview(order.getId(), ADMIN);
        assertFalse(beforeGenerate.attachment().generated());

        officialPoIntegrationService.generateExcel(order.getId(), ADMIN);

        MailPreviewResponse afterGenerate = mailPreviewService.preview(order.getId(), ADMIN);
        assertTrue(afterGenerate.attachment().generated());
    }

    @Test
    void adminCcIncludesEnabledAdminAccounts() {
        PortalOrder order = createApprovedOrder();

        MailPreviewResponse preview = mailPreviewService.preview(order.getId(), ADMIN);

        // sales_admin/sys_admin/admin01 are seeded ADMIN accounts with email
        // (V8 migration) - at least one must appear in cc regardless of
        // whether the mail is otherwise Blocked (CC resolution runs
        // independently of the officialPoNo/Contact/Template Gate).
        assertTrue(preview.cc().stream().anyMatch(email -> email.endsWith("@portal-demo.invalid")));
    }
}
