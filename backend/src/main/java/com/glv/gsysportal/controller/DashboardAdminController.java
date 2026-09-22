package com.glv.gsysportal.controller;

import com.glv.gsysportal.domain.DashboardRefreshRun;
import com.glv.gsysportal.dto.response.DashboardRefreshRunResponse;
import com.glv.gsysportal.repository.prototype.DashboardRefreshRunRepository;
import com.glv.gsysportal.security.CurrentUserProvider;
import com.glv.gsysportal.service.DashboardRefreshService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Stage 5K / Stage 5J §8 Option E, §17-18: ADMIN-only Manual Refresh and
 * Refresh history - mirrors {@code ManufacturerChannelController}'s own
 * class-level {@code @PreAuthorize("hasRole('ADMIN')")} convention. No
 * separate logic from the scheduled path - both call the exact same
 * {@link DashboardRefreshService#refresh}, only the {@code triggerType}/
 * {@code initiatedBy} differ, and single-flight applies identically (a
 * Manual Refresh while one is already running comes back {@code
 * skipped=true}, never a second concurrent refresh).
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class DashboardAdminController {

    private final DashboardRefreshService refreshService;
    private final DashboardRefreshRunRepository refreshRunRepository;
    private final CurrentUserProvider currentUserProvider;

    public DashboardAdminController(DashboardRefreshService refreshService,
                                     DashboardRefreshRunRepository refreshRunRepository,
                                     CurrentUserProvider currentUserProvider) {
        this.refreshService = refreshService;
        this.refreshRunRepository = refreshRunRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/api/admin/dashboard/refresh")
    public ManualRefreshResponse triggerRefresh() {
        DashboardRefreshService.RefreshOutcome outcome = refreshService.refresh(
                DashboardRefreshRun.TRIGGER_MANUAL, currentUserProvider.currentUsername());
        return new ManualRefreshResponse(outcome.skipped(), outcome.refreshRunId(), outcome.errorMessage());
    }

    /** Stage 5J §19: most-recent-first, capped - not unbounded history
     * (Stage 5K §20 Retention). */
    @GetMapping("/api/admin/dashboard/refresh-runs")
    public List<DashboardRefreshRunResponse> recentRuns() {
        return refreshRunRepository.findAllByOrderByCalculationStartedAtDesc(PageRequest.of(0, 50)).stream()
                .map(r -> new DashboardRefreshRunResponse(r.getId(), r.getStatus(), r.getTriggerType(),
                        r.getCalculationStartedAt(), r.getCalculationCompletedAt(), r.getEvaluatedItemCount(),
                        r.getCandidateCount(), r.getFormulaErrorCount(), r.getErrorMessage(), r.getInitiatedBy()))
                .toList();
    }

    public record ManualRefreshResponse(boolean skipped, Long refreshRunId, String errorMessage) {
    }
}
