package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findByPortalOrderIdOrderByPerformedAtAsc(Long portalOrderId);
}
