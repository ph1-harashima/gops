package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.ManufacturerChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManufacturerChannelRepository extends JpaRepository<ManufacturerChannel, Long> {

    List<ManufacturerChannel> findAllByOrderBySupplierCodeAscBrandCodeAsc();

    /** Resolution's brand-specific tier (9-D, mirrors SupplierContactRepository's
     * own precedent). At most one row per the partial UNIQUE index. */
    Optional<ManufacturerChannel> findFirstBySupplierCodeAndBrandCodeAndActiveTrue(String supplierCode, String brandCode);

    /** Resolution's supplier-only tier (brandCode IS NULL). */
    Optional<ManufacturerChannel> findFirstBySupplierCodeAndBrandCodeIsNullAndActiveTrue(String supplierCode);

    boolean existsBySupplierCodeAndBrandCodeAndActiveTrue(String supplierCode, String brandCode);

    boolean existsBySupplierCodeAndBrandCodeIsNullAndActiveTrue(String supplierCode);
}
