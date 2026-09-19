package com.glv.gsysportal.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BR-06 (docs/gulliver-20260917-confirmed-business-rules.md): Demo Send is a
 * Local/Demo/Test-only Test Helper Flow, never a Production Business
 * Function - {@code app.demo-features.enabled} defaults to {@code true} in
 * every profile {@code SafetyGuardEnvironmentPostProcessor} actually allows
 * to start (local/demo/test), and this endpoint reflects that config value
 * verbatim rather than hardcoding a client-side environment guess. The
 * {@code false} case (application-production.yml) is unreachable in this
 * environment by design - proven separately by
 * {@code SafetyGuardEnvironmentPostProcessorTest}'s own "production profile
 * can never start" coverage, not re-tested here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemFeatureFlagsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithUserDetails("purchase01")
    void demoSendIsEnabledByDefaultInTestProfile() throws Exception {
        mockMvc.perform(get("/api/system/feature-flags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demoSendEnabled").value(true));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/system/feature-flags"))
                .andExpect(status().isUnauthorized());
    }
}
