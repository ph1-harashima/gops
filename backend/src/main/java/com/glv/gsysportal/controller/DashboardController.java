package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.response.DashboardResponse;
import com.glv.gsysportal.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Action / Operation Cockpit (implementation instructions Step 5 3章) -
 * READ ONLY, no Analytics (no Sales Trend / margin / turnover rate). */
@RestController
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/api/dashboard")
    public DashboardResponse dashboard() {
        return dashboardService.getDashboard();
    }
}
