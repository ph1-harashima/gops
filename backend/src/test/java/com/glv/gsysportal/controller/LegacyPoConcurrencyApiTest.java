package com.glv.gsysportal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP-level tests for Phase 7-C6's Legacy PO Concurrency endpoints (27章's
 * Browser Scenario F equivalent: OPERATOR hitting the ADMIN-only Baseline
 * Capture endpoint directly must get 403; Compare stays open to any
 * authenticated user, 7-C6 22章). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager", propagation = Propagation.NOT_SUPPORTED)
class LegacyPoConcurrencyApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private static SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor asAdmin() {
        return SecurityMockMvcRequestPostProcessors.user("admin01").roles("ADMIN");
    }

    private Long createApprovedDraft() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("skus", List.of("OD-TENT-001")));
        String response = mockMvc.perform(post("/api/orders/drafts").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) objectMapper.readValue(response, Map.class).get("id")).longValue();

        mockMvc.perform(post("/api/orders/" + id + "/submit-for-approval").with(asAdmin())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + id + "/approve").with(asAdmin())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk());
        return id;
    }

    @Test
    void baselineCaptureWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(post("/api/orders/1/official-po/baseline").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrencyGetWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/api/orders/1/official-po/concurrency")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithUserDetails("purchase01")
    void operatorCannotCaptureBaseline() throws Exception {
        Long id = createApprovedDraft();

        mockMvc.perform(post("/api/orders/" + id + "/official-po/baseline").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("purchase01")
    void operatorCanViewConcurrency() throws Exception {
        Long id = createApprovedDraft();

        mockMvc.perform(get("/api/orders/" + id + "/official-po/concurrency"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("NOT_LINKED"));
    }

    @Test
    @WithUserDetails("purchase01")
    void operatorCannotSetIntegrationIntent() throws Exception {
        Long id = createApprovedDraft();
        mockMvc.perform(post("/api/orders/" + id + "/official-po/request").with(asAdmin())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk());

        String body = objectMapper.writeValueAsString(Map.of("intent", "NEW"));
        mockMvc.perform(put("/api/orders/" + id + "/official-po/intent").contentType("application/json").content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("purchase01")
    void adminCanSetIntegrationIntent() throws Exception {
        Long id = createApprovedDraft();
        mockMvc.perform(post("/api/orders/" + id + "/official-po/request").with(asAdmin())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk());

        String body = objectMapper.writeValueAsString(Map.of("intent", "UPDATE"));
        mockMvc.perform(put("/api/orders/" + id + "/official-po/intent").with(asAdmin())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.integrationIntent").value("UPDATE"));
    }
}
