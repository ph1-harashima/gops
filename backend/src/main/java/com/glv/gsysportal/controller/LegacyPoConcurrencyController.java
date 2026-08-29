package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.response.LegacyPoBaselineResponse;
import com.glv.gsysportal.dto.response.LegacyPoConcurrencyResponse;
import com.glv.gsysportal.security.CurrentUserProvider;
import com.glv.gsysportal.service.LegacyPoConcurrencyService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 7-C6 9章/10章/22章: Excel / Legacy Concurrency Control Foundation
 * Business Actions. Baseline Capture is ADMIN only (7-C6 9章/22章); Compare
 * is open to any authenticated user, matching every other Order Detail
 * read-only Section in this codebase (Fulfillment/Official PO Integration).
 */
@RestController
public class LegacyPoConcurrencyController {

    private final LegacyPoConcurrencyService concurrencyService;
    private final CurrentUserProvider currentUserProvider;

    public LegacyPoConcurrencyController(LegacyPoConcurrencyService concurrencyService,
                                          CurrentUserProvider currentUserProvider) {
        this.concurrencyService = concurrencyService;
        this.currentUserProvider = currentUserProvider;
    }

    /** "G-SYS現在状態を基準として記録" - ADMIN only. Never writes to Legacy in
     * any way (Legacy READ ONLY throughout). */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/orders/{id}/official-po/baseline")
    public LegacyPoBaselineResponse captureBaseline(@PathVariable Long id) {
        return concurrencyService.captureBaseline(id, currentUserProvider.currentUsername());
    }

    /** "G-SYSとの差異を確認" - any authenticated user. */
    @GetMapping("/api/orders/{id}/official-po/concurrency")
    public LegacyPoConcurrencyResponse compare(@PathVariable Long id) {
        return concurrencyService.compare(id);
    }
}
