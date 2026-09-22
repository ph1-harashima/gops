package com.glv.gsysportal.service;

import com.glv.gsysportal.domain.DashboardRefreshRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

/**
 * Stage 5K (docs/real-data-audit/gops-stage5j-production-batch-schedule-addendum.md
 * §6-§9/§13): fires {@link DashboardRefreshService#refresh} on a fixed
 * schedule. Cadence/offset/quiet-hours are all externally configured
 * (Production Batch Schedule Addendum §13/§14's explicit "do NOT
 * hard-code 30 minutes or :07/:37 inside Business Logic") - the Addendum's
 * own recommendation (every 30 min, offset :07/:37, quiet hours
 * ~22:00-04:30) is only this property file's DEFAULT, not a fixed rule,
 * and remains pending Gulliver's freshness confirmation (Addendum §14/§17).
 *
 * <p>No per-tick Source Change Detection scan (Stage 5J §9's original
 * proposal) - the Addendum §9 found Legacy writes to at least one
 * calc4-input table almost continuously during the confirmed Batch
 * window, so a pre-check would almost always proceed to a full refresh
 * anyway; the quiet-hours skip below captures the same benefit (skipping
 * ticks during the one genuinely idle window, Addendum §4) at zero query
 * cost instead.
 */
@Component
public class DashboardRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(DashboardRefreshScheduler.class);

    private final DashboardRefreshService refreshService;
    private final boolean enabled;
    private final LocalTime quietHoursStart;
    private final LocalTime quietHoursEnd;

    public DashboardRefreshScheduler(DashboardRefreshService refreshService,
                                      @Value("${app.dashboard.refresh.enabled:true}") boolean enabled,
                                      @Value("${app.dashboard.refresh.quiet-hours-start:22:00}") String quietHoursStart,
                                      @Value("${app.dashboard.refresh.quiet-hours-end:04:30}") String quietHoursEnd) {
        this.refreshService = refreshService;
        this.enabled = enabled;
        this.quietHoursStart = LocalTime.parse(quietHoursStart);
        this.quietHoursEnd = LocalTime.parse(quietHoursEnd);
    }

    /** Cron format is Spring's own (second minute hour day month weekday) -
     * default "0 7,37 * * * *" is the Addendum §7 recommendation (:07/:37
     * past every hour), overridable via {@code app.dashboard.refresh.cron}
     * without any code/redeploy change. Tests override this (and/or
     * {@code enabled=false}) so a background tick never races a test's own
     * assertions - see application.yml / test profile overrides. */
    @Scheduled(cron = "${app.dashboard.refresh.cron:0 7,37 * * * *}")
    public void scheduledRefresh() {
        if (!enabled) {
            return;
        }
        if (isQuietHours(LocalTime.now())) {
            log.debug("refresh tick skipped - quiet hours ({}-{})", quietHoursStart, quietHoursEnd);
            return;
        }
        refreshService.refresh(DashboardRefreshRun.TRIGGER_SCHEDULED, null);
    }

    /** Handles the overnight wraparound (e.g. 22:00-04:30 crosses
     * midnight) - Addendum §4's confirmed ~4-hour genuinely idle window,
     * §9's basis for skipping ticks here instead of running a per-tick DB
     * scan. */
    boolean isQuietHours(LocalTime now) {
        if (quietHoursStart.equals(quietHoursEnd)) {
            return false; // start == end is configured as "no quiet hours"
        }
        if (quietHoursStart.isBefore(quietHoursEnd)) {
            return !now.isBefore(quietHoursStart) && now.isBefore(quietHoursEnd);
        }
        return !now.isBefore(quietHoursStart) || now.isBefore(quietHoursEnd);
    }
}
