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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level tests for POST/GET/PUT /api/orders/drafts (implementation
 * instructions 16章), on top of the direct-service tests in
 * {@link com.glv.gsysportal.service.OrderDraftServiceIntegrationTest}.
 * Covers authentication enforcement and the JSON error-code contract
 * ({@link com.glv.gsysportal.exception.GlobalExceptionHandler}).
 *
 * NOT_SUPPORTED: each test manages its own Draft's lifetime independently
 * (created and left in the DB, or - where a test asserts nothing should be
 * created - genuinely nothing is written), consistent with the rollback test
 * in OrderDraftServiceIntegrationTest; the local Prototype Postgres is
 * disposable Docker-only data, not shared/production, per the Step 2 Safety
 * Rules.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager", propagation = Propagation.NOT_SUPPORTED)
class OrderDraftControllerApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createDraft_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/orders/drafts")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("skus", java.util.List.of("OD-TENT-001")))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("NOT_AUTHENTICATED"));
    }

    @Test
    @WithUserDetails("purchase01")
    void createDraft_authenticated_returns201WithDraftNo() throws Exception {
        mockMvc.perform(post("/api/orders/drafts")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "skus", java.util.List.of("OD-TENT-001", "OD-TENT-002"),
                                "orderDate", "2026-08-27",
                                "requestedDelivery", "2026-09-10",
                                "remark", "api test"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.draftNo").exists())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.supplierCode").value("SUP_ALPHA"))
                .andExpect(jsonPath("$.details.length()").value(2));
    }

    @Test
    @WithUserDetails("purchase01")
    void createDraft_mixedSupplier_returns400() throws Exception {
        mockMvc.perform(post("/api/orders/drafts")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "skus", java.util.List.of("OD-TENT-001", "HM-RUG-001")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MIXED_SUPPLIER_NOT_ALLOWED"));
    }

    @Test
    @WithUserDetails("purchase01")
    void createDraft_emptySkuList_returns400() throws Exception {
        mockMvc.perform(post("/api/orders/drafts")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("skus", java.util.List.of()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getDraft_nonExistent_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/orders/drafts/999999"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithUserDetails("purchase01")
    void getDraft_nonExistent_authenticated_returns404() throws Exception {
        mockMvc.perform(get("/api/orders/drafts/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DRAFT_NOT_FOUND"));
    }

    @Test
    @WithUserDetails("purchase01")
    void createThenGetThenPut_roundTripMatches() throws Exception {
        String createBody = objectMapper.writeValueAsString(Map.of(
                "skus", java.util.List.of("OD-TENT-001"),
                "remark", "round trip"
        ));
        String createResponse = mockMvc.perform(post("/api/orders/drafts")
                        .contentType("application/json").content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Map<?, ?> created = objectMapper.readValue(createResponse, Map.class);
        Object id = created.get("id");
        java.util.List<?> details = (java.util.List<?>) created.get("details");
        Object detailId = ((Map<?, ?>) details.get(0)).get("id");

        mockMvc.perform(get("/api/orders/drafts/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remark").value("round trip"));

        String putBody = objectMapper.writeValueAsString(Map.of(
                "remark", "round trip updated",
                "details", java.util.List.of(Map.of("detailId", detailId, "orderQty", 1))
        ));
        mockMvc.perform(put("/api/orders/drafts/" + id)
                        .contentType("application/json").content(putBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remark").value("round trip updated"))
                .andExpect(jsonPath("$.details[0].orderQty").value(1))
                .andExpect(jsonPath("$.details[0].warningCodes[0]").value("ORDER_QTY_DIFFERS_SIGNIFICANTLY"));

        mockMvc.perform(get("/api/orders/drafts/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remark").value("round trip updated"))
                .andExpect(jsonPath("$.details[0].orderQty").value(1));
    }

    @Test
    @WithUserDetails("purchase01")
    void putDraft_negativeOrderQty_returns400() throws Exception {
        String createResponse = mockMvc.perform(post("/api/orders/drafts")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("skus", java.util.List.of("OD-TENT-001")))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Map<?, ?> created = objectMapper.readValue(createResponse, Map.class);
        Object id = created.get("id");
        Object detailId = ((Map<?, ?>) ((java.util.List<?>) created.get("details")).get(0)).get("id");

        mockMvc.perform(put("/api/orders/drafts/" + id)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "details", java.util.List.of(Map.of("detailId", detailId, "orderQty", -1))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ORDER_QTY"));
    }

    // ---- Delete Draft (G-OPS Operational Workflow Realignment Phase F
    // §16) ----

    @Test
    void deleteDraft_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(delete("/api/orders/drafts/999999"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithUserDetails("purchase01")
    void deleteDraft_nonExistent_returns404() throws Exception {
        mockMvc.perform(delete("/api/orders/drafts/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DRAFT_NOT_FOUND"));
    }

    @Test
    @WithUserDetails("purchase01")
    void deleteDraft_thenGet_returns404() throws Exception {
        String createResponse = mockMvc.perform(post("/api/orders/drafts")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("skus", java.util.List.of("OD-TENT-001")))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Object id = objectMapper.readValue(createResponse, Map.class).get("id");

        mockMvc.perform(delete("/api/orders/drafts/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/orders/drafts/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DRAFT_NOT_FOUND"));
    }

    @Test
    @WithUserDetails("admin01")
    void deleteDraft_onceApproved_returns409() throws Exception {
        String createResponse = mockMvc.perform(post("/api/orders/drafts")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("skus", java.util.List.of("OD-TENT-001")))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Object id = objectMapper.readValue(createResponse, Map.class).get("id");

        mockMvc.perform(post("/api/orders/" + id + "/submit-for-approval"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + id + "/approve"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/orders/drafts/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DRAFT_DELETION_NOT_ALLOWED"));
    }
}
