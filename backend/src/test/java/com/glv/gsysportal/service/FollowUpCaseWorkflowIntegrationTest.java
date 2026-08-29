package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.FollowUpCase;
import com.glv.gsysportal.domain.MailTemplate;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.domain.SupplierContact;
import com.glv.gsysportal.dto.request.CloseFollowUpCaseRequest;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.request.CreateFollowUpCaseRequest;
import com.glv.gsysportal.dto.request.CreateReorderDraftRequest;
import com.glv.gsysportal.dto.request.MailTemplateRequest;
import com.glv.gsysportal.dto.request.SupplierContactRequest;
import com.glv.gsysportal.dto.request.UpdateFollowUpCaseRequest;
import com.glv.gsysportal.dto.response.FollowUpCaseResponse;
import com.glv.gsysportal.dto.response.MailPreviewResponse;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.exception.FollowUpCaseAlreadyClosedException;
import com.glv.gsysportal.exception.InvalidFollowUpReasonException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.PortalOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 7-C7A 25章 Heavy Regression: Follow-up Case CRUD/Actions, Follow-up
 * Mail Preview, Reorder Foundation. Same whole-test-method Prototype
 * transaction + rollback pattern as the other Step 2+ integration tests.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class FollowUpCaseWorkflowIntegrationTest {

    private static final String ADMIN = "admin01";

    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private FollowUpCaseService followUpCaseService;
    @Autowired
    private FollowUpMailPreviewService followUpMailPreviewService;
    @Autowired
    private ReorderService reorderService;
    @Autowired
    private SupplierContactService contactService;
    @Autowired
    private MailTemplateService templateService;
    @Autowired
    private PortalOrderRepository portalOrderRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;

    private PortalOrder createDraftOrder(String sku) {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(sku), null, null, null), ADMIN);
        return portalOrderRepository.findById(draft.id()).orElseThrow();
    }

    @Test
    void createWritesAuditAndDefaultsToOpen() {
        PortalOrder order = createDraftOrder("OD-TENT-001");

        FollowUpCaseResponse created = followUpCaseService.create(order.getId(),
                new CreateFollowUpCaseRequest("OD-TENT-001", FollowUpCase.REASON_NO_ARRIVAL, "no shipment yet"), ADMIN);

        assertEquals(FollowUpCase.STATUS_OPEN, created.status());
        assertEquals("OD-TENT-001", created.skuCode());
        assertTrue(auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId()).stream()
                .anyMatch(e -> e.getEventType().equals(AuditEvent.FOLLOW_UP_CREATED)));
    }

    @Test
    void createWithInvalidReasonIsRejected() {
        PortalOrder order = createDraftOrder("OD-TENT-001");

        assertThrows(InvalidFollowUpReasonException.class, () -> followUpCaseService.create(order.getId(),
                new CreateFollowUpCaseRequest(null, "NOT_A_REAL_REASON", null), ADMIN));
    }

    @Test
    void listReturnsCasesOldestFirst() {
        PortalOrder order = createDraftOrder("OD-TENT-001");
        followUpCaseService.create(order.getId(), new CreateFollowUpCaseRequest(null, FollowUpCase.REASON_OTHER, "first"), ADMIN);
        followUpCaseService.create(order.getId(), new CreateFollowUpCaseRequest(null, FollowUpCase.REASON_OTHER, "second"), ADMIN);

        List<FollowUpCaseResponse> cases = followUpCaseService.list(order.getId());

        assertEquals(2, cases.size());
        assertEquals("first", cases.get(0).note());
        assertEquals("second", cases.get(1).note());
    }

    @Test
    void updateNoteWritesAuditOnlyWhenChanged() {
        PortalOrder order = createDraftOrder("OD-TENT-001");
        FollowUpCaseResponse created = followUpCaseService.create(order.getId(),
                new CreateFollowUpCaseRequest(null, FollowUpCase.REASON_OTHER, "original"), ADMIN);

        followUpCaseService.updateNote(created.id(), new UpdateFollowUpCaseRequest("updated"), ADMIN);
        int countAfterRealChange = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId()).size();

        followUpCaseService.updateNote(created.id(), new UpdateFollowUpCaseRequest("updated"), ADMIN);
        int countAfterNoOpChange = auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId()).size();

        assertEquals(countAfterRealChange, countAfterNoOpChange, "re-saving the same note must not add a new audit row");
    }

    @Test
    void closeTransitionsStatusAndWritesAudit() {
        PortalOrder order = createDraftOrder("OD-TENT-001");
        FollowUpCaseResponse created = followUpCaseService.create(order.getId(),
                new CreateFollowUpCaseRequest(null, FollowUpCase.REASON_OTHER, null), ADMIN);

        FollowUpCaseResponse closed = followUpCaseService.close(created.id(), new CloseFollowUpCaseRequest("resolved"), ADMIN);

        assertEquals(FollowUpCase.STATUS_CLOSED, closed.status());
        assertEquals(ADMIN, closed.closedBy());
        assertNotNull(closed.closedAt());
        assertTrue(auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(order.getId()).stream()
                .anyMatch(e -> e.getEventType().equals(AuditEvent.FOLLOW_UP_CLOSED)));
    }

    @Test
    void doubleCloseIsRejected() {
        PortalOrder order = createDraftOrder("OD-TENT-001");
        FollowUpCaseResponse created = followUpCaseService.create(order.getId(),
                new CreateFollowUpCaseRequest(null, FollowUpCase.REASON_OTHER, null), ADMIN);
        followUpCaseService.close(created.id(), new CloseFollowUpCaseRequest(null), ADMIN);

        assertThrows(FollowUpCaseAlreadyClosedException.class,
                () -> followUpCaseService.close(created.id(), new CloseFollowUpCaseRequest(null), ADMIN));
    }

    @Test
    void updateNoteOnClosedCaseIsRejected() {
        PortalOrder order = createDraftOrder("OD-TENT-001");
        FollowUpCaseResponse created = followUpCaseService.create(order.getId(),
                new CreateFollowUpCaseRequest(null, FollowUpCase.REASON_OTHER, null), ADMIN);
        followUpCaseService.close(created.id(), new CloseFollowUpCaseRequest(null), ADMIN);

        assertThrows(FollowUpCaseAlreadyClosedException.class,
                () -> followUpCaseService.updateNote(created.id(), new UpdateFollowUpCaseRequest("too late"), ADMIN));
    }

    // --- Follow-up Mail Preview ---

    @Test
    void previewBlockedWithoutOfficialPoNo() {
        PortalOrder order = createDraftOrder("OD-TENT-001");
        FollowUpCaseResponse created = followUpCaseService.create(order.getId(),
                new CreateFollowUpCaseRequest("OD-TENT-001", FollowUpCase.REASON_NO_ARRIVAL, "note"), ADMIN);

        MailPreviewResponse preview = followUpMailPreviewService.preview(order.getId(), created.id(), ADMIN);

        assertTrue(preview.issues().stream().anyMatch(i -> "OFFICIAL_PO_NO_NOT_ASSIGNED".equals(i.code())));
        assertNull(preview.subject());
        // BLOCKED preview must not advance the Case's Status.
        assertEquals(FollowUpCase.STATUS_OPEN, followUpCaseService.list(order.getId()).get(0).status());
    }

    @Test
    void resolvedPreviewRendersAndTransitionsToInquiryPrepared() {
        PortalOrder order = createDraftOrder("OD-TENT-001");
        contactService.create(new SupplierContactRequest("SUP_ALPHA", "BR_OUTDOOR", "Taro Yamada", "taro@example.com",
                SupplierContact.CONTACT_TYPE_TO, SupplierContact.LANGUAGE_JA, null, null, true, true), ADMIN);
        templateService.create(new MailTemplateRequest("Follow-up Template", MailTemplate.TEMPLATE_TYPE_FOLLOW_UP,
                "SUP_ALPHA", "BR_OUTDOOR", SupplierContact.LANGUAGE_JA,
                "PO {{poNo}} について ({{reason}})",
                "{{contactName}} 様\n\n{{supplierName}} 御中\nSKU: {{skuCode}}\n{{note}}\nFrom: {{senderName}}",
                null, true), ADMIN);
        order.setOfficialPoNo("TSUP-TBR-FOLLOWUP-01");
        portalOrderRepository.save(order);
        FollowUpCaseResponse created = followUpCaseService.create(order.getId(),
                new CreateFollowUpCaseRequest("OD-TENT-001", FollowUpCase.REASON_NO_ARRIVAL, "still no shipment"), ADMIN);

        MailPreviewResponse preview = followUpMailPreviewService.preview(order.getId(), created.id(), ADMIN);

        assertFalse(preview.issues().stream().anyMatch(i -> "BLOCKED".equals(i.severity())));
        assertEquals("PO TSUP-TBR-FOLLOWUP-01 について (NO_ARRIVAL)", preview.subject());
        assertTrue(preview.body().contains("SKU: OD-TENT-001"));
        assertTrue(preview.body().contains("still no shipment"));

        assertEquals(FollowUpCase.STATUS_INQUIRY_PREPARED, followUpCaseService.list(order.getId()).get(0).status());
    }

    @Test
    void previewOnClosedCaseIsRejected() {
        PortalOrder order = createDraftOrder("OD-TENT-001");
        FollowUpCaseResponse created = followUpCaseService.create(order.getId(),
                new CreateFollowUpCaseRequest(null, FollowUpCase.REASON_OTHER, null), ADMIN);
        followUpCaseService.close(created.id(), new CloseFollowUpCaseRequest(null), ADMIN);

        assertThrows(FollowUpCaseAlreadyClosedException.class,
                () -> followUpMailPreviewService.preview(order.getId(), created.id(), ADMIN));
    }

    // --- Reorder Foundation ---

    @Test
    void reorderDraftCreatesNewOrderWithSourceReferencesAndAudit() {
        PortalOrder original = createDraftOrder("OD-TENT-001");
        FollowUpCaseResponse followUpCase = followUpCaseService.create(original.getId(),
                new CreateFollowUpCaseRequest("OD-TENT-001", FollowUpCase.REASON_NO_ARRIVAL, null), ADMIN);

        OrderDraftResponse reorderDraft = reorderService.createReorderDraft(followUpCase.id(),
                new CreateReorderDraftRequest(List.of("OD-TENT-001"), "original never arrived"), ADMIN);

        assertFalse(reorderDraft.id().equals(original.getId()), "must be a genuinely new Order, not the original");
        PortalOrder saved = portalOrderRepository.findById(reorderDraft.id()).orElseThrow();
        assertEquals(original.getId(), saved.getSourceOrderId());
        assertEquals(followUpCase.id(), saved.getSourceFollowUpCaseId());
        assertEquals("original never arrived", saved.getReorderReason());
        assertEquals(PortalOrder.STATUS_DRAFT, saved.getStatus(), "a Reorder Draft is an ordinary fresh Draft, no auto-approval");
        assertTrue(auditEventRepository.findByPortalOrderIdOrderByPerformedAtAsc(reorderDraft.id()).stream()
                .anyMatch(e -> e.getEventType().equals(AuditEvent.REORDER_CREATED)));
    }

    @Test
    void reorderDraftNeverAutoSetsQuantityFromOutstanding() {
        PortalOrder original = createDraftOrder("OD-TENT-001");
        FollowUpCaseResponse followUpCase = followUpCaseService.create(original.getId(),
                new CreateFollowUpCaseRequest("OD-TENT-001", FollowUpCase.REASON_NO_ARRIVAL, null), ADMIN);

        OrderDraftResponse reorderDraft = reorderService.createReorderDraft(followUpCase.id(),
                new CreateReorderDraftRequest(List.of("OD-TENT-001"), "reason"), ADMIN);

        // orderQty must equal whatever createDraft normally computes
        // (Legacy Recommended Qty) - the SAME value the original Draft got,
        // never something derived from an Outstanding Qty calculation.
        assertEquals(original.getDetails().get(0).getOrderQty(), reorderDraft.details().get(0).orderQty());
    }
}
