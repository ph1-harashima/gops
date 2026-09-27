package com.glv.gsysportal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Draft Lifecycle Cleanup Audit §1/§6/§8: fires {@link DraftCleanupService}
 * on a schedule, mirroring {@link DashboardRefreshScheduler}'s own
 * enabled-flag/cron externalization pattern exactly. Disabled by default
 * (unlike Dashboard Refresh's default-enabled) - per this Stage's own
 * explicit instruction, no environment (including a plain local/test run)
 * may silently start deleting existing Drafts without an operator having
 * deliberately opted in via {@code app.draft-cleanup.enabled=true}.
 *
 * <p>The orchestration loop lives here, NOT inside {@link
 * DraftCleanupService} itself, so that each {@link
 * DraftCleanupService#cleanupOneIfStillEligible} call below is a genuine
 * external (proxied) call and its own {@code @Transactional} actually
 * applies per Draft - calling it via {@code this.} from inside the same
 * Spring bean would silently bypass the transaction/lock entirely (a
 * well-known Spring AOP self-invocation limitation). This also directly
 * gives "one failing Draft never stops the whole run" (§6): each iteration
 * is individually try/caught.
 */
@Component
public class DraftCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(DraftCleanupScheduler.class);

    private final DraftCleanupService draftCleanupService;
    private final boolean enabled;

    public DraftCleanupScheduler(DraftCleanupService draftCleanupService,
                                  @Value("${app.draft-cleanup.enabled:false}") boolean enabled) {
        this.draftCleanupService = draftCleanupService;
        this.enabled = enabled;
    }

    /** Default: once daily at 03:00 (low-traffic hour, same spirit as
     * Dashboard Refresh's own quiet-hours concept) - overridable via
     * {@code app.draft-cleanup.cron} without any code change. */
    @Scheduled(cron = "${app.draft-cleanup.cron:0 0 3 * * *}")
    public void scheduledCleanup() {
        if (!enabled) {
            return;
        }
        runCleanup();
    }

    /** Also called directly by {@code DraftCleanupAdminController}'s manual
     * trigger (ADMIN-only) - the exact same code path as the scheduled
     * tick, only the caller differs (mirrors DashboardRefreshService's own
     * scheduled-vs-manual-refresh symmetry). */
    public CleanupRunSummary runCleanup() {
        OffsetDateTime cutoff = draftCleanupService.currentCutoff();
        List<Long> candidateIds = draftCleanupService.findCandidateIds(cutoff);

        int succeeded = 0;
        int skipped = 0;
        int failed = 0;
        List<Long> failedIds = new ArrayList<>();

        for (Long id : candidateIds) {
            try {
                DraftCleanupService.CleanupOutcome outcome = draftCleanupService.cleanupOneIfStillEligible(id, cutoff);
                if (outcome == DraftCleanupService.CleanupOutcome.DELETED) {
                    succeeded++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                // One Draft's own failure must never stop the batch (§6) -
                // logged by ID and exception type only, never any business
                // data (SKU/Supplier/Brand/remark/user input).
                failed++;
                failedIds.add(id);
                log.warn("draft cleanup failed for portal_order id={}: {}", id, e.getClass().getSimpleName());
            }
        }

        log.info("draft cleanup finished: candidates={}, succeeded={}, skipped={}, failed={}, failedIds={}",
                candidateIds.size(), succeeded, skipped, failed, failedIds);

        return new CleanupRunSummary(candidateIds.size(), succeeded, skipped, failed, failedIds);
    }

    public record CleanupRunSummary(int candidateCount, int succeeded, int skipped, int failed, List<Long> failedIds) {
    }
}
