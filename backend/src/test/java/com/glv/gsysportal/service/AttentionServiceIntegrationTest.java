package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.OrderAttention;
import com.glv.gsysportal.domain.PortalOrder;
import com.glv.gsysportal.dto.request.CreateDraftRequest;
import com.glv.gsysportal.dto.request.SaveSupplierResponseRequest;
import com.glv.gsysportal.dto.response.AttentionResponse;
import com.glv.gsysportal.dto.response.OrderDraftResponse;
import com.glv.gsysportal.dto.response.SupplierResponseView;
import com.glv.gsysportal.exception.AttentionAlreadyResolvedException;
import com.glv.gsysportal.exception.AttentionNotFoundException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Implementation instructions Step 5 2章: Attention Acknowledge. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager")
class AttentionServiceIntegrationTest {

    /** Phase 7-C1: DRAFT -> PENDING_APPROVAL -> APPROVED (the pre-7-C1 confirm() equivalent). */
    private com.glv.gsysportal.domain.PortalOrder approveViaWorkflow(Long orderId) {
        statusTransitionService.submitForApproval(orderId, "tester01", true);
        return statusTransitionService.approve(orderId, "tester01");
    }
    private static final String SKU_TENT_1 = "OD-TENT-001"; // recommendedQty = 3

    @Autowired
    private OrderDraftService orderDraftService;
    @Autowired
    private OrderStatusTransitionService statusTransitionService;
    @Autowired
    private SupplierResponseService supplierResponseService;
    @Autowired
    private AttentionService attentionService;
    @Autowired
    private AuditEventRepository auditEventRepository;

    private Long createQuantityChangedAttention() {
        OrderDraftResponse draft = orderDraftService.createDraft(new CreateDraftRequest(List.of(SKU_TENT_1), null, null, null), "tester01");
        PortalOrder ready = approveViaWorkflow(draft.id());
        PortalOrder sent = statusTransitionService.demoSend(ready.getId(), "tester01");
        Long detailId = supplierResponseService.getSupplierResponse(sent.getId()).details().get(0).detailId();
        SupplierResponseView view = supplierResponseService.saveSupplierResponse(sent.getId(),
                new SaveSupplierResponseRequest(null, null, List.of(
                        new SaveSupplierResponseRequest.LineUpdate(detailId, 1, null, null))),
                "tester01");
        return view.details().get(0).attentions().get(0).id();
    }

    @Test
    void acknowledgeDeactivatesAndStampsFields() {
        Long attentionId = createQuantityChangedAttention();

        AttentionResponse response = attentionService.acknowledge(attentionId, "tester02");

        assertFalse(response.isActive());
        assertEquals("tester02", response.acknowledgedBy());
        assertEquals(OrderAttention.QUANTITY_CHANGED, response.attentionType());
        assertTrue(response.acknowledgedAt() != null && response.resolvedAt() != null);
    }

    @Test
    void acknowledgeWritesAttentionResolvedAudit() {
        Long attentionId = createQuantityChangedAttention();

        attentionService.acknowledge(attentionId, "tester02");

        List<AuditEvent> events = auditEventRepository.findAll();
        assertTrue(events.stream().anyMatch(e ->
                e.getEventType().equals(AuditEvent.ATTENTION_RESOLVED) && "tester02".equals(e.getPerformedBy())));
    }

    @Test
    void doubleAcknowledgeIsRejected() {
        Long attentionId = createQuantityChangedAttention();
        attentionService.acknowledge(attentionId, "tester01");

        assertThrows(AttentionAlreadyResolvedException.class, () -> attentionService.acknowledge(attentionId, "tester02"));
    }

    @Test
    void acknowledgeOnUnknownAttentionThrowsNotFound() {
        assertThrows(AttentionNotFoundException.class, () -> attentionService.acknowledge(-1L, "tester01"));
    }
}
