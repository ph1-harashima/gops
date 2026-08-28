package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.AuditEvent;
import com.glv.gsysportal.domain.OrderAttention;
import com.glv.gsysportal.dto.response.AttentionResponse;
import com.glv.gsysportal.exception.AttentionAlreadyResolvedException;
import com.glv.gsysportal.exception.AttentionNotFoundException;
import com.glv.gsysportal.repository.prototype.AuditEventRepository;
import com.glv.gsysportal.repository.prototype.OrderAttentionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * POST /api/attentions/{id}/acknowledge (implementation instructions Step 5
 * 2章). QUANTITY_CHANGED/DELIVERY_CHANGED Attentions (implementation
 * instructions Step 4 23章) stay ACTIVE until a user explicitly
 * acknowledges them via this endpoint - this is that endpoint.
 */
@Service
public class AttentionService {

    private final OrderAttentionRepository orderAttentionRepository;
    private final AuditEventRepository auditEventRepository;

    public AttentionService(OrderAttentionRepository orderAttentionRepository,
                             AuditEventRepository auditEventRepository) {
        this.orderAttentionRepository = orderAttentionRepository;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * Only ACTIVE Attentions may be acknowledged (409
     * ATTENTION_ALREADY_RESOLVED otherwise - a duplicate Acknowledge is
     * rejected outright, before anything else runs, so it never
     * double-writes Audit - the same idempotency pattern as Confirm Order /
     * Demo Send). The row itself is never deleted, only its is_active/
     * resolved_at/acknowledged_by/acknowledged_at fields change, so the
     * Attention's full history remains visible from Order History.
     */
    @Transactional(transactionManager = "prototypeTransactionManager")
    public AttentionResponse acknowledge(Long attentionId, String performedBy) {
        OrderAttention attention = orderAttentionRepository.findById(attentionId)
                .orElseThrow(() -> new AttentionNotFoundException(attentionId));

        if (!attention.isActive()) {
            throw new AttentionAlreadyResolvedException(attentionId);
        }

        OffsetDateTime now = OffsetDateTime.now();
        attention.setActive(false);
        attention.setResolvedAt(now);
        attention.setAcknowledgedBy(performedBy);
        attention.setAcknowledgedAt(now);

        OrderAttention saved = orderAttentionRepository.save(attention);

        auditEventRepository.save(new AuditEvent(
                saved.getPortalOrderId(), saved.getPortalOrderDetailId(), AuditEvent.ATTENTION_RESOLVED,
                "attentionType", saved.getAttentionType(), null, performedBy, now
        ));

        return toResponse(saved);
    }

    private static AttentionResponse toResponse(OrderAttention a) {
        return new AttentionResponse(
                a.getId(), a.getPortalOrderId(), a.getPortalOrderDetailId(), a.getAttentionType(),
                a.isActive(), a.getDetectedAt(), a.getResolvedAt(), a.getAcknowledgedBy(), a.getAcknowledgedAt()
        );
    }
}
