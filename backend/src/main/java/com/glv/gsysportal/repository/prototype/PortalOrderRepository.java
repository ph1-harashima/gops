package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.PortalOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Phase 8-J 3章/4章: {@link JpaSpecificationExecutor} added so Order History
 * List can push its Filters + Pageable to the DB (see
 * OrderHistoryService#list) instead of the previous findAll()+Java Stream
 * filter. No new query method was needed for this - Specification composes
 * the existing filter predicates dynamically.
 */
public interface PortalOrderRepository extends JpaRepository<PortalOrder, Long>, JpaSpecificationExecutor<PortalOrder> {
    boolean existsByDraftNo(String draftNo);
}
