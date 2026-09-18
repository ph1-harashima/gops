package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.SupplierRegionClassification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierRegionClassificationRepository extends JpaRepository<SupplierRegionClassification, Long> {

    List<SupplierRegionClassification> findAllByOrderBySupplierCodeAscBrandCodeAsc();

    Optional<SupplierRegionClassification> findFirstBySupplierCodeAndBrandCodeAndActiveTrue(String supplierCode, String brandCode);

    Optional<SupplierRegionClassification> findFirstBySupplierCodeAndBrandCodeIsNullAndActiveTrue(String supplierCode);

    boolean existsBySupplierCodeAndBrandCodeAndActiveTrue(String supplierCode, String brandCode);

    boolean existsBySupplierCodeAndBrandCodeIsNullAndActiveTrue(String supplierCode);
}
