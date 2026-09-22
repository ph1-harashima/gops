package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.DashboardBrandLegacyAggregate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DashboardBrandLegacyAggregateRepository extends JpaRepository<DashboardBrandLegacyAggregate, Long> {

    List<DashboardBrandLegacyAggregate> findByRefreshRunId(Long refreshRunId);
}
