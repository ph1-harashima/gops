package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.PortalOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortalOrderRepository extends JpaRepository<PortalOrder, Long> {
    boolean existsByDraftNo(String draftNo);
}
