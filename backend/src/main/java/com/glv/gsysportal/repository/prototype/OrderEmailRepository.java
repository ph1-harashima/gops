package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.OrderEmail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderEmailRepository extends JpaRepository<OrderEmail, Long> {

    /** Idempotency lookup (mirrors OfficialPoIntegrationRequestRepository's
     * own precedent) - at most one row can ever match, per the UNIQUE
     * constraint on (portal_order_id, revision_no). */
    Optional<OrderEmail> findByPortalOrderIdAndRevisionNo(Long portalOrderId, int revisionNo);
}
