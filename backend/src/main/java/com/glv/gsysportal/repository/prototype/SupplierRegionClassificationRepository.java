package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.SupplierRegionClassification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierRegionClassificationRepository extends JpaRepository<SupplierRegionClassification, Long> {

    List<SupplierRegionClassification> findAllByOrderBySupplierCodeAscBrandCodeAsc();

    // Stage 5E Targeted Remediation (RC-A/docs/real-data-audit/
    // gops-stage5e-targeted-remediation.md): bulk-fetch for
    // RegionClassificationLookup - one query for an entire List/Dashboard
    // request instead of resolve() being called per row (the confirmed
    // N+1 root cause, Stage 5D RC-A).
    List<SupplierRegionClassification> findAllByActiveTrue();

    Optional<SupplierRegionClassification> findFirstBySupplierCodeAndBrandCodeAndActiveTrue(String supplierCode, String brandCode);

    Optional<SupplierRegionClassification> findFirstBySupplierCodeAndBrandCodeIsNullAndActiveTrue(String supplierCode);

    boolean existsBySupplierCodeAndBrandCodeAndActiveTrue(String supplierCode, String brandCode);

    boolean existsBySupplierCodeAndBrandCodeIsNullAndActiveTrue(String supplierCode);
}
