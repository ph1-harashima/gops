package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.DashboardRefreshRun;
import com.glv.gsysportal.repository.prototype.DashboardAggregateCurrentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Stage 5J §15 "Startup / Empty State", carried into Stage 5K: on
 * application start, if {@code dashboard_aggregate_current} has no row
 * yet (first deploy of this feature in an environment, or a reset Portal
 * DB), triggers one {@code STARTUP}-type refresh before the application
 * finishes starting - a one-time, idempotent check; if a row already
 * exists this does nothing. Runs synchronously (blocks startup, not a
 * Dashboard request) - this is deliberately NOT the "synchronous full
 * calc4 in the request thread" fallback §16 of the Implementation
 * instruction prohibits, since no Dashboard request is ever waiting on
 * it; {@link com.glv.gsysportal.controller.DashboardController} simply
 * has nothing to read until this completes, matching Stage 5J §15's
 * "explicit not-yet-initialized state, never a fabricated zero" design
 * (see {@code DashboardService#getDashboard}).
 *
 * <p>Deliberately NOT gated by {@code app.dashboard.refresh.enabled} -
 * that flag controls the recurring {@code DashboardRefreshScheduler}
 * tick only ("stop refreshing periodically"), not this one-time bootstrap
 * ("never initialize the Dashboard at all"); this runner is idempotent
 * (a no-op whenever a current row already exists) so it is always safe to
 * run regardless of that flag.
 */
@Component
public class DashboardRefreshStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DashboardRefreshStartupRunner.class);

    private final DashboardAggregateCurrentRepository aggregateCurrentRepository;
    private final DashboardRefreshService refreshService;

    public DashboardRefreshStartupRunner(DashboardAggregateCurrentRepository aggregateCurrentRepository,
                                          DashboardRefreshService refreshService) {
        this.aggregateCurrentRepository = aggregateCurrentRepository;
        this.refreshService = refreshService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (aggregateCurrentRepository.findBySingleton(Boolean.TRUE).isPresent()) {
            return;
        }
        log.info("no dashboard_aggregate_current row yet - running one-time STARTUP refresh");
        refreshService.refresh(DashboardRefreshRun.TRIGGER_STARTUP, null);
    }
}
