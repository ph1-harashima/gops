package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.OrderAttention;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderAttentionRepository extends JpaRepository<OrderAttention, Long> {

    List<OrderAttention> findByPortalOrderIdAndActiveTrue(Long portalOrderId);

    /** Used by Dashboard (implementation instructions Step 5 3章) to compute
     * 要確認 counts without one query per Order. */
    List<OrderAttention> findByActiveTrue();

    List<OrderAttention> findByPortalOrderIdOrderByDetectedAtAsc(Long portalOrderId);

    Optional<OrderAttention> findByPortalOrderIdAndPortalOrderDetailIdAndAttentionTypeAndActiveTrue(
            Long portalOrderId, Long portalOrderDetailId, String attentionType);

    /** Order-level Attention (e.g. PARTIAL_CONFIRMATION) has no detail id. */
    Optional<OrderAttention> findByPortalOrderIdAndPortalOrderDetailIdIsNullAndAttentionTypeAndActiveTrue(
            Long portalOrderId, String attentionType);
}
