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

/** HTTP-level tests for Phase 7-C7A's Fulfillment / Follow-up endpoints
 * (Permission - 27章's Browser Scenario F equivalent: OPERATOR hitting an
 * ADMIN-only endpoint directly must get 403). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager", propagation = Propagation.NOT_SUPPORTED)
class FollowUpCaseApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private static SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor asAdmin() {
        return SecurityMockMvcRequestPostProcessors.user("admin01").roles("ADMIN");
    }

    private Long createDraft(String sku) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("skus", List.of(sku)));
        String response = mockMvc.perform(post("/api/orders/drafts").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) objectMapper.readValue(response, Map.class).get("id")).longValue();
    }

    @Test
    void fulfillmentGetWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/api/orders/1/fulfillment")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithUserDetails("purchase01")
    void fulfillmentNotLinkedForAFreshDraft() throws Exception {
        Long id = createDraft("OD-TENT-001");

        mockMvc.perform(get("/api/orders/" + id + "/fulfillment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkState").value("NOT_LINKED"))
                .andExpect(jsonPath("$.lines").isEmpty());
    }

    @Test
    @WithUserDetails("purchase01")
    void operatorCanCreateAndUpdateNoteButNotCloseOrReorder() throws Exception {
        Long orderId = createDraft("OD-TENT-001");

        String createBody = objectMapper.writeValueAsString(Map.of("reason", "OTHER", "note", "checking"));
        String createResponse = mockMvc.perform(post("/api/orders/" + orderId + "/follow-up-cases")
                        .contentType("application/json").content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        long caseId = ((Number) objectMapper.readValue(createResponse, Map.class).get("id")).longValue();

        String updateBody = objectMapper.writeValueAsString(Map.of("note", "updated by operator"));
        mockMvc.perform(put("/api/follow-up-cases/" + caseId).contentType("application/json").content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value("updated by operator"));

        mockMvc.perform(post("/api/follow-up-cases/" + caseId + "/close").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());

        String reorderBody = objectMapper.writeValueAsString(Map.of("skus", List.of("OD-TENT-001")));
        mockMvc.perform(post("/api/follow-up-cases/" + caseId + "/reorder-draft")
                        .contentType("application/json").content(reorderBody))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("purchase01")
    void adminCanCloseAndCreateReorderDraft() throws Exception {
        Long orderId = createDraft("OD-TENT-001");
        String createBody = objectMapper.writeValueAsString(Map.of("reason", "NO_ARRIVAL"));
        String createResponse = mockMvc.perform(post("/api/orders/" + orderId + "/follow-up-cases")
                        .contentType("application/json").content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long caseId = ((Number) objectMapper.readValue(createResponse, Map.class).get("id")).longValue();

        mockMvc.perform(post("/api/follow-up-cases/" + caseId + "/close").with(asAdmin())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        // A second, independent Case for the Reorder Draft check (the first is now CLOSED).
        String createResponse2 = mockMvc.perform(post("/api/orders/" + orderId + "/follow-up-cases")
                        .contentType("application/json").content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long caseId2 = ((Number) objectMapper.readValue(createResponse2, Map.class).get("id")).longValue();

        String reorderBody = objectMapper.writeValueAsString(Map.of("skus", List.of("OD-TENT-001"), "reorderReason", "r"));
        mockMvc.perform(post("/api/follow-up-cases/" + caseId2 + "/reorder-draft").with(asAdmin())
                        .contentType("application/json").content(reorderBody))
                .andExpect(status().isCreated());
    }

    @Test
    void createFollowUpCaseAndReorderDraftWithoutAuthenticationReturn401() throws Exception {
        mockMvc.perform(post("/api/orders/1/follow-up-cases").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/follow-up-cases/1/reorder-draft").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
