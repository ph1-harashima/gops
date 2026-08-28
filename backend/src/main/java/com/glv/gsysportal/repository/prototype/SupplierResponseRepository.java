package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.SupplierResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierResponseRepository extends JpaRepository<SupplierResponse, Long> {

    /** Phase 7-C5 5章: the CURRENT Response - the one for the Order's
     * currently-sent Revision. Replaces the pre-7-C5
     * {@code findByPortalOrderId}, which assumed exactly one row per Order. */
    Optional<SupplierResponse> findByPortalOrderIdAndOrderRevisionId(Long portalOrderId, Long orderRevisionId);

    /** Full Response history for an Order, oldest first (Supplier Response UI
     * History list, 7-C5 21章). */
    List<SupplierResponse> findByPortalOrderIdOrderByIdAsc(Long portalOrderId);
}
