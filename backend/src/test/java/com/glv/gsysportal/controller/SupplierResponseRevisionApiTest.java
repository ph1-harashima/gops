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

/** HTTP-level tests for Phase 7-C5's new Business Actions (Permission -
 * 27章's Browser Scenario F equivalent at the API layer: OPERATOR hitting an
 * ADMIN-only endpoint directly must get 403), on top of the direct-service
 * tests in {@code OrderRevisionWorkflowIntegrationTest}. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager", propagation = Propagation.NOT_SUPPORTED)
class SupplierResponseRevisionApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private Long createSupplierConfirmedOrder(int confirmedQty) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("skus", List.of("OD-TENT-001")));
        String response = mockMvc.perform(post("/api/orders/drafts").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) objectMapper.readValue(response, Map.class).get("id")).longValue();
        mockMvc.perform(post("/api/orders/" + id + "/submit-for-approval")).andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + id + "/approve").with(asAdmin())).andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + id + "/demo-send")).andExpect(status().isOk());

        String getResponse = mockMvc.perform(get("/api/orders/" + id + "/supplier-response"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Map<?, ?> got = objectMapper.readValue(getResponse, Map.class);
        long detailId = ((Number) ((Map<?, ?>) ((List<?>) got.get("details")).get(0)).get("detailId")).longValue();

        String putBody = objectMapper.writeValueAsString(Map.of(
                "details", List.of(Map.of("detailId", detailId, "confirmedQty", confirmedQty))));
        mockMvc.perform(put("/api/orders/" + id + "/supplier-response").contentType("application/json").content(putBody))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + id + "/supplier-response/confirm")).andExpect(status().isOk());
        return id;
    }

    private static SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor asAdmin() {
        return SecurityMockMvcRequestPostProcessors.user("admin01").roles("ADMIN");
    }

    @Test
    @WithUserDetails("purchase01")
    void createRevisionAsOperatorReturns403() throws Exception {
        Long id = createSupplierConfirmedOrder(2); // orderedQty=3

        String body = objectMapper.writeValueAsString(Map.of("reason", "correction", "applyConfirmedValues", false));
        mockMvc.perform(post("/api/orders/" + id + "/revisions").contentType("application/json").content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("purchase01")
    void agreeAsOperatorReturns403() throws Exception {
        Long id = createSupplierConfirmedOrder(3); // no difference

        String getResponse = mockMvc.perform(get("/api/orders/" + id + "/supplier-response"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long responseId = ((Number) objectMapper.readValue(getResponse, Map.class).get("responseId")).longValue();

        mockMvc.perform(post("/api/orders/" + id + "/responses/" + responseId + "/agree")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("purchase01")
    void reopenAsOperatorReturns403() throws Exception {
        Long id = createSupplierConfirmedOrder(3);
        String getResponse = mockMvc.perform(get("/api/orders/" + id + "/supplier-response"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long responseId = ((Number) objectMapper.readValue(getResponse, Map.class).get("responseId")).longValue();
        mockMvc.perform(post("/api/orders/" + id + "/responses/" + responseId + "/agree").with(asAdmin())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk());

        String body = objectMapper.writeValueAsString(Map.of("reason", "mistake"));
        mockMvc.perform(post("/api/orders/" + id + "/responses/" + responseId + "/reopen")
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("purchase01")
    void fullAgreementFlowAsAdminSucceedsAndOperatorCanStillRead() throws Exception {
        Long id = createSupplierConfirmedOrder(3); // no difference -> no Attention -> agreeable without force

        String getResponse = mockMvc.perform(get("/api/orders/" + id + "/supplier-response"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long responseId = ((Number) objectMapper.readValue(getResponse, Map.class).get("responseId")).longValue();

        mockMvc.perform(post("/api/orders/" + id + "/responses/" + responseId + "/agree").with(asAdmin())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGREED"));

        // OPERATOR may still read the (now historical-ish, but still current) Response.
        mockMvc.perform(get("/api/orders/" + id + "/supplier-response"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agreedBy").value("admin01"));

        mockMvc.perform(get("/api/orders/" + id + "/responses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isCurrent").value(true));
    }

    @Test
    @WithUserDetails("purchase01")
    void createRevisionAsAdminSucceedsAndReturnsOrderToDraft() throws Exception {
        Long id = createSupplierConfirmedOrder(2); // orderedQty=3, difference

        String body = objectMapper.writeValueAsString(Map.of("reason", "correcting to 2", "applyConfirmedValues", true));
        mockMvc.perform(post("/api/orders/" + id + "/revisions").with(asAdmin())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(get("/api/orders/" + id + "/revisions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].revisionType").value("INITIAL"));
    }

    @Test
    void revisionsAndAgreeWithoutAuthenticationReturn401() throws Exception {
        mockMvc.perform(post("/api/orders/1/revisions").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/orders/1/responses/1/agree").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
