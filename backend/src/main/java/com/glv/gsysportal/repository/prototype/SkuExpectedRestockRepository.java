package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.SkuExpectedRestock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkuExpectedRestockRepository extends JpaRepository<SkuExpectedRestock, Long> {

    Optional<SkuExpectedRestock> findBySkuCode(String skuCode);

    /** Bulk lookup for List screens (Candidate List/Stock-Sales) - avoids
     * one query per row. */
    List<SkuExpectedRestock> findBySkuCodeIn(List<String> skuCodes);
}
