package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.SupplierResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupplierResponseRepository extends JpaRepository<SupplierResponse, Long> {
    Optional<SupplierResponse> findByPortalOrderId(Long portalOrderId);
}
