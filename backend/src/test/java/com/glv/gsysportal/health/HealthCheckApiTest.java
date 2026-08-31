package com.glv.gsysportal.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 8-L (Production Reliability Foundation, Production Readiness Audit
 * §2-4/§24). Both endpoints must be reachable WITHOUT authentication (a load
 * balancer cannot log in) and must never leak connection/Secret detail.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthCheckApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void actuatorHealthIsReachableWithoutAuthenticationAndReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    /** management.endpoint.health.show-details=never - no "components" /
     * "details" breakdown, no DB name, no driver, no host. */
    @Test
    void actuatorHealthNeverLeaksComponentDetailOrConnectionInfo() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsStringIgnoringCase("jdbc"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsStringIgnoringCase("password"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsStringIgnoringCase("gsys_portal"))));
    }

    @Test
    void legacyHealthIsReachableWithoutAuthenticationAndReportsUp() throws Exception {
        // Legacy Demo MySQL is expected to be running for this test suite
        // (same prerequisite every other Legacy-backed integration test has).
        mockMvc.perform(get("/api/health/legacy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void legacyHealthResponseNeverLeaksConnectionInfo() throws Exception {
        mockMvc.perform(get("/api/health/legacy"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsStringIgnoringCase("jdbc"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsStringIgnoringCase("password"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsStringIgnoringCase("legacy_demo"))));
    }

    /** Phase 8-L §3: a healthy Portal DB (this test's own connection is
     * proof the Portal DB is up) must never be dragged DOWN by anything
     * Legacy-related - the two are structurally separate endpoints. */
    @Test
    void applicationHealthIsIndependentOfLegacyEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/api/health/legacy")).andExpect(status().isOk());
    }
}
