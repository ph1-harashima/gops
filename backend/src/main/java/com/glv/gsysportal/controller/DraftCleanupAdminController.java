package com.glv.gsysportal.controller;

import com.glv.gsysportal.service.DraftCleanupScheduler;
import com.glv.gsysportal.service.DraftCleanupService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Draft Lifecycle Cleanup Audit §9/§14: ADMIN-only dry-run (read-only, zero
 * side effects) and manual trigger - mirrors {@link DashboardAdminController}'s
 * own class-level {@code @PreAuthorize("hasRole('ADMIN')")} convention and
 * its "manual trigger calls the exact same code path as the scheduled tick"
 * precedent ({@link DraftCleanupScheduler#runCleanup} is that one shared
 * path here). Never returns SKU/Supplier/Brand/remark/any business-data
 * field - Order IDs and counts only, same logging-safety boundary as the
 * Scheduler's own log line.
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class DraftCleanupAdminController {

    private final DraftCleanupService draftCleanupService;
    private final DraftCleanupScheduler draftCleanupScheduler;

    public DraftCleanupAdminController(DraftCleanupService draftCleanupService,
                                        DraftCleanupScheduler draftCleanupScheduler) {
        this.draftCleanupService = draftCleanupService;
        this.draftCleanupScheduler = draftCleanupScheduler;
    }

    /** Read-only - never deletes anything. Lets an ADMIN check "how many
     * Drafts would this affect right now" independently of whether the
     * scheduler itself is enabled. */
    @GetMapping("/api/admin/draft-cleanup/dry-run")
    public DryRunResponse dryRun() {
        OffsetDateTime cutoff = draftCleanupService.currentCutoff();
        List<Long> candidateIds = draftCleanupService.findCandidateIds(cutoff);
        return new DryRunResponse(candidateIds.size(), candidateIds, cutoff);
    }

    /** Runs the exact same code the scheduled tick runs, on demand -
     * usable regardless of {@code app.draft-cleanup.enabled}. */
    @PostMapping("/api/admin/draft-cleanup/run")
    public RunResponse triggerCleanup() {
        DraftCleanupScheduler.CleanupRunSummary summary = draftCleanupScheduler.runCleanup();
        return new RunResponse(summary.candidateCount(), summary.succeeded(), summary.skipped(),
                summary.failed(), summary.failedIds());
    }

    public record DryRunResponse(int candidateCount, List<Long> candidateIds, OffsetDateTime cutoff) {
    }

    public record RunResponse(int candidateCount, int succeeded, int skipped, int failed, List<Long> failedIds) {
    }
}
