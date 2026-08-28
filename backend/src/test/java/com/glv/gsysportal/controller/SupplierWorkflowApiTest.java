package com.glv.gsysportal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP-level tests for Demo Send / Supplier Response (implementation
 * instructions 3章/9章/10章/20章), on top of the direct-service tests in
 * OrderStatusTransitionServiceIntegrationTest and
 * SupplierResponseServiceIntegrationTest. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager", propagation = Propagation.NOT_SUPPORTED)
class SupplierWorkflowApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private Long createAwaitingSupplierOrder(String... skus) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("skus", java.util.List.of(skus)));
        String response = mockMvc.perform(post("/api/orders/drafts").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) objectMapper.readValue(response, Map.class).get("id")).longValue();
        // Phase 7-C1: DRAFT -> PENDING_APPROVAL (caller's session) ->
        // APPROVED (ADMIN via request-scoped principal override) -> demo-send.
        mockMvc.perform(post("/api/orders/" + id + "/submit-for-approval")).andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + id + "/approve")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user("admin01").roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + id + "/demo-send")).andExpect(status().isOk());
        return id;
    }

    @Test
    void demoSendWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(post("/api/orders/1/demo-send")).andExpect(status().isUnauthorized());
    }

    @Test
    void supplierResponseGetWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/api/orders/1/supplier-response")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithUserDetails("purchase01")
    void fullDemoSendSupplierResponseConfirmFlow() throws Exception {
        Long id = createAwaitingSupplierOrder("OD-TENT-001");

        String getResponse = mockMvc.perform(get("/api/orders/" + id + "/supplier-response"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.details[0].confirmedQty").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        Map<?, ?> got = objectMapper.readValue(getResponse, Map.class);
        long detailId = ((Number) ((Map<?, ?>) ((java.util.List<?>) got.get("details")).get(0)).get("detailId")).longValue();

        String putBody = objectMapper.writeValueAsString(Map.of(
                "details", java.util.List.of(Map.of("detailId", detailId, "confirmedQty", 3))));
        mockMvc.perform(put("/api/orders/" + id + "/supplier-response").contentType("application/json").content(putBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details[0].confirmedQty").value(3))
                .andExpect(jsonPath("$.summary.answeredCount").value(1));

        mockMvc.perform(post("/api/orders/" + id + "/supplier-response/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUPPLIER_CONFIRMED"));

        // Idempotency: duplicate confirm rejected.
        mockMvc.perform(post("/api/orders/" + id + "/supplier-response/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @WithUserDetails("purchase01")
    void confirmSupplierResponseIncompleteReturns400() throws Exception {
        Long id = createAwaitingSupplierOrder("OD-TENT-001");

        mockMvc.perform(post("/api/orders/" + id + "/supplier-response/confirm"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SUPPLIER_RESPONSE_INCOMPLETE"));
    }

    @Test
    @WithUserDetails("purchase01")
    void duplicateDemoSendReturns409() throws Exception {
        Long id = createAwaitingSupplierOrder("OD-TENT-001");

        mockMvc.perform(post("/api/orders/" + id + "/demo-send"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }
}
