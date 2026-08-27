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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level tests for the three Step 3 endpoints (implementation
 * instructions 2章/7章/13章), on top of the direct-service tests in
 * {@link com.glv.gsysportal.service.PoPreviewServiceIntegrationTest} and
 * {@link com.glv.gsysportal.service.OrderStatusTransitionServiceIntegrationTest}.
 * NOT_SUPPORTED for the same reason as OrderDraftControllerApiTest: each
 * test manages its own Draft independently on the real local Postgres.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager", propagation = Propagation.NOT_SUPPORTED)
class PoPreviewConfirmApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private Long createDraft(String... skus) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("skus", java.util.List.of(skus)));
        String response = mockMvc.perform(post("/api/orders/drafts").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) objectMapper.readValue(response, Map.class).get("id")).longValue();
    }

    @Test
    void previewWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(post("/api/orders/drafts/1/preview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirmWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(post("/api/orders/drafts/1/confirm"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnToDraftWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(post("/api/orders/1/return-to-draft"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithUserDetails("purchase01")
    void fullPreviewConfirmReturnToDraftFlow() throws Exception {
        Long id = createDraft("OD-TENT-001", "OD-TENT-002");

        mockMvc.perform(post("/api/orders/drafts/" + id + "/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.prototypePoNo").doesNotExist())
                .andExpect(jsonPath("$.demoMode").value(true))
                .andExpect(jsonPath("$.details.length()").value(2));

        String confirmResponse = mockMvc.perform(post("/api/orders/drafts/" + id + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_TO_ORDER"))
                .andExpect(jsonPath("$.prototypePoNo").exists())
                .andReturn().getResponse().getContentAsString();
        String poNo = (String) objectMapper.readValue(confirmResponse, Map.class).get("prototypePoNo");

        // Duplicate Confirm must be rejected (409), idempotency.
        mockMvc.perform(post("/api/orders/drafts/" + id + "/confirm"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        // Draft is not editable while READY_TO_ORDER.
        mockMvc.perform(put("/api/orders/drafts/" + id)
                        .contentType("application/json").content("{\"remark\":\"nope\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_EDITABLE"));

        // Preview after Confirm reflects READY_TO_ORDER + the assigned PO No.
        mockMvc.perform(post("/api/orders/drafts/" + id + "/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_TO_ORDER"))
                .andExpect(jsonPath("$.prototypePoNo").value(poNo));

        mockMvc.perform(post("/api/orders/" + id + "/return-to-draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.prototypePoNo").value(poNo));

        // Editable again.
        mockMvc.perform(put("/api/orders/drafts/" + id)
                        .contentType("application/json").content("{\"remark\":\"editable again\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remark").value("editable again"));

        // Re-Confirm reuses the same PO No.
        String reconfirmResponse = mockMvc.perform(post("/api/orders/drafts/" + id + "/confirm"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String poNo2 = (String) objectMapper.readValue(reconfirmResponse, Map.class).get("prototypePoNo");
        org.junit.jupiter.api.Assertions.assertEquals(poNo, poNo2);
    }

    @Test
    @WithUserDetails("purchase01")
    void confirmWithNoOrderableItemsReturns400() throws Exception {
        Long id = createDraft("OD-CHAIR-001"); // recommendedQty=0 -> orderQty=0 initially

        mockMvc.perform(post("/api/orders/drafts/" + id + "/confirm"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("NO_ORDERABLE_ITEMS"));

        mockMvc.perform(post("/api/orders/drafts/" + id + "/preview"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("NO_ORDERABLE_ITEMS"));
    }

    @Test
    @WithUserDetails("purchase01")
    void returnToDraftOnDraftOrderReturns409() throws Exception {
        Long id = createDraft("OD-TENT-001");

        mockMvc.perform(post("/api/orders/" + id + "/return-to-draft"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @WithUserDetails("purchase01")
    void previewOnNonExistentDraftReturns404() throws Exception {
        mockMvc.perform(post("/api/orders/drafts/999999/preview"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DRAFT_NOT_FOUND"));
    }
}
