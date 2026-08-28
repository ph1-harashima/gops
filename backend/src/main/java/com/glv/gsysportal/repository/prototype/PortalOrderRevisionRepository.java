package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.PortalOrderRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortalOrderRevisionRepository extends JpaRepository<PortalOrderRevision, Long> {

    Optional<PortalOrderRevision> findByPortalOrderIdAndRevisionNo(Long portalOrderId, int revisionNo);

    List<PortalOrderRevision> findByPortalOrderIdOrderByRevisionNoAsc(Long portalOrderId);

    Optional<PortalOrderRevision> findFirstByPortalOrderIdOrderByRevisionNoDesc(Long portalOrderId);
}
