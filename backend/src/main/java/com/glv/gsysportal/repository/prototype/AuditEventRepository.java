package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findByPortalOrderIdOrderByPerformedAtAsc(Long portalOrderId);

    /** Phase 8-B: Price Change's own aggregate-root Audit query, mirroring
     * {@link #findByPortalOrderIdOrderByPerformedAtAsc} exactly. */
    List<AuditEvent> findByPriceChangeSetIdOrderByPerformedAtAsc(Long priceChangeSetId);

    /** Post-Freeze Business Refinement: SKU Expected Restock's own
     * aggregate-root Audit query, same shape as the two above. */
    List<AuditEvent> findBySkuCodeOrderByPerformedAtAsc(String skuCode);

    /**
     * Draft Lifecycle Cleanup Audit §2/§3: the exact, corrected "was this
     * Draft ever touched beyond its own creation" signal - {@code true} if
     * ANY event type other than {@code ORDER_DRAFT_CREATED} exists for this
     * Order, not a hand-picked subset of field-change event types (the
     * audit's own §2 found a narrower "4 field-change types only" filter
     * missed 36 real Orders with FOLLOW_UP_* activity). Never narrow this
     * back down to specific event types.
     */
    boolean existsByPortalOrderIdAndEventTypeNot(Long portalOrderId, String eventType);
}
