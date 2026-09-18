package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.OfficialPoIntegrationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OfficialPoIntegrationRequestRepository extends JpaRepository<OfficialPoIntegrationRequest, Long> {

    /** Idempotency lookup (7-C2A 4章): the UNIQUE constraint on
     * (portal_order_id, revision_no) backs this - at most one row can ever
     * match. */
    Optional<OfficialPoIntegrationRequest> findByPortalOrderIdAndRevisionNo(Long portalOrderId, int revisionNo);

    /** Latest Request for an Order regardless of revision (7-C2A 13章's
     * Order Detail display - only revision 1 exists this Phase, but the
     * lookup itself doesn't assume that). */
    Optional<OfficialPoIntegrationRequest> findFirstByPortalOrderIdOrderByRevisionNoDesc(Long portalOrderId);

    /** Gap Analysis C-2 (docs/gulliver-20260917-phase1-gap-analysis.md 7章):
     * Revision History - every Request ever created for this Order (old
     * Revisions are never deleted, only marked SUPERSEDED/CANCELLED). */
    List<OfficialPoIntegrationRequest> findByPortalOrderIdOrderByRevisionNoAsc(Long portalOrderId);
}
