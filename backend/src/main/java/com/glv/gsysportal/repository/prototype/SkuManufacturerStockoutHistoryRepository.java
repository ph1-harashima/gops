package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.SkuManufacturerStockoutHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkuManufacturerStockoutHistoryRepository extends JpaRepository<SkuManufacturerStockoutHistory, Long> {

    List<SkuManufacturerStockoutHistory> findBySkuCodeOrderByRecordedAtAsc(String skuCode);
}
