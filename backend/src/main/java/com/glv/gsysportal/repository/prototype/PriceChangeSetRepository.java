package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.PriceChangeSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PriceChangeSetRepository extends JpaRepository<PriceChangeSet, Long> {

    /** Price Change List (target-price-change-workflow.md 15章): newest
     * first, matching {@code PortalOrderRepository}'s own List ordering
     * convention. Status filter is optional (null = all). */
    @Query("SELECT s FROM PriceChangeSet s WHERE (:status IS NULL OR s.status = :status) ORDER BY s.createdAt DESC")
    List<PriceChangeSet> findAllForList(@Param("status") String status);
}
