package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.SupplierContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierContactRepository extends JpaRepository<SupplierContact, Long> {

    List<SupplierContact> findAllByOrderBySupplierCodeAscBrandCodeAsc();

    /** Resolution's brand-specific tier (7-C3 5章). */
    List<SupplierContact> findAllBySupplierCodeAndBrandCodeAndContactTypeAndActiveTrue(
            String supplierCode, String brandCode, String contactType);

    /** Resolution's supplier-only tier (brandCode IS NULL). */
    List<SupplierContact> findAllBySupplierCodeAndBrandCodeIsNullAndContactTypeAndActiveTrue(
            String supplierCode, String contactType);

    boolean existsBySupplierCodeAndBrandCodeAndEmailIgnoreCaseAndActiveTrue(
            String supplierCode, String brandCode, String email);

    boolean existsBySupplierCodeAndBrandCodeIsNullAndEmailIgnoreCaseAndActiveTrue(
            String supplierCode, String email);
}
