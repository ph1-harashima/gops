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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP-level tests for Order History (implementation instructions 24章). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager", propagation = Propagation.NOT_SUPPORTED)
class OrderHistoryApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void historyListWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/api/orders/history")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithUserDetails("purchase01")
    void historyListDetailAndEvents() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("skus", java.util.List.of("OD-TENT-001")));
        String createResponse = mockMvc.perform(post("/api/orders/drafts").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) objectMapper.readValue(createResponse, Map.class).get("id")).longValue();

        // Phase 8-J 3章/4章: response is now a PageResponse envelope
        // ({content, page, size, totalElements, totalPages}), not a bare
        // array - same shape as /api/arrivals, /api/warehouse-stock,
        // /api/stock-sales (Phase 8-G/8-H).
        mockMvc.perform(get("/api/orders/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + id + ")]").exists());

        mockMvc.perform(get("/api/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details[0].recommendedQty").value(3))
                .andExpect(jsonPath("$.details[0].orderedQty").value(3))
                .andExpect(jsonPath("$.details[0].confirmedQty").doesNotExist());

        mockMvc.perform(get("/api/orders/" + id + "/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("ORDER_DRAFT_CREATED"));
    }

    @Test
    @WithUserDetails("purchase01")
    void detailOnNonExistentOrderReturns404() throws Exception {
        mockMvc.perform(get("/api/orders/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DRAFT_NOT_FOUND"));
    }
}
