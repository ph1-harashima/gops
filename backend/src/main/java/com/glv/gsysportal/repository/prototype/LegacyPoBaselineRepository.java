package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.LegacyPoBaseline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LegacyPoBaselineRepository extends JpaRepository<LegacyPoBaseline, Long> {

    /** The active Baseline for a (portalOrderId, revisionNo) - the MOST
     * RECENT capture, since a Baseline may legitimately be re-captured
     * (append-only, 7-C6 4章's Javadoc). Empty means NOT_BASELINED. */
    Optional<LegacyPoBaseline> findFirstByPortalOrderIdAndRevisionNoOrderByCapturedAtDesc(Long portalOrderId, int revisionNo);
}
