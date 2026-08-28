package com.glv.gsysportal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 7-C1 HTTP-level approval Workflow + Authorization tests (7-C1
 * 17章/25章): the full submit -> approve / return cycle over the real
 * endpoints, and - critically - direct API calls by the WRONG role being
 * rejected with 403 (Backend enforcement, not Frontend button-hiding).
 * Replaces the pre-7-C1 PoPreviewConfirmApiTest (the /confirm endpoint it
 * covered no longer exists). NOT_SUPPORTED propagation for the same reason
 * as OrderDraftControllerApiTest.
 *
 * Role fixtures: purchase01 is a real OPERATOR row (V8), admin01 a real
 * ADMIN row - loaded via @WithUserDetails so the tests exercise the actual
 * PortalUserDetailsService role mapping, not synthetic authorities.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager", propagation = Propagation.NOT_SUPPORTED)
class ApprovalWorkflowApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    /** Request-scoped ADMIN principal override on a purchase01 session test. */
    private static RequestPostProcessor asAdmin() {
        return user("admin01").roles("ADMIN");
    }

    private Long createDraft(String... skus) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("skus", java.util.List.of(skus)));
        String response = mockMvc.perform(post("/api/orders/drafts").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) objectMapper.readValue(response, Map.class).get("id")).longValue();
    }

    // ---- Authentication --------------------------------------------------

    @Test
    void approvalEndpointsWithoutAuthenticationReturn401() throws Exception {
        mockMvc.perform(post("/api/orders/1/submit-for-approval")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/orders/1/approve")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/orders/1/return-for-correction")
                        .contentType("application/json").content("{\"reason\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/orders/drafts/1/preview")).andExpect(status().isUnauthorized());
    }

    // ---- Authorization: OPERATOR must NOT approve/return (7-C1 17章) ------

    @Test
    @WithUserDetails("purchase01")
    void operatorCallingApproveDirectlyGets403() throws Exception {
        Long id = createDraft("OD-TENT-001");
        mockMvc.perform(post("/api/orders/" + id + "/submit-for-approval")).andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/" + id + "/approve"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @WithUserDetails("purchase01")
    void operatorCallingReturnForCorrectionDirectlyGets403() throws Exception {
        Long id = createDraft("OD-TENT-001");
        mockMvc.perform(post("/api/orders/" + id + "/submit-for-approval")).andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/" + id + "/return-for-correction")
                        .contentType("application/json").content("{\"reason\":\"op tries\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @WithUserDetails("purchase01")
    void operatorCallingReturnToDraftDirectlyGets403() throws Exception {
        Long id = createDraft("OD-TENT-001");
        mockMvc.perform(post("/api/orders/" + id + "/submit-for-approval")).andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + id + "/approve").with(asAdmin())).andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/" + id + "/return-to-draft"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    // ---- Full flows ------------------------------------------------------

    @Test
    @WithUserDetails("purchase01")
    void fullSubmitApproveFlowAssignsPoNoAtApproval() throws Exception {
        Long id = createDraft("OD-TENT-001", "OD-TENT-002");

        mockMvc.perform(post("/api/orders/drafts/" + id + "/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.prototypePoNo").doesNotExist());

        mockMvc.perform(post("/api/orders/" + id + "/submit-for-approval"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.prototypePoNo").doesNotExist());

        // OPERATOR cannot edit the queued Draft; ADMIN can (edit-and-approve).
        mockMvc.perform(put("/api/orders/drafts/" + id)
                        .contentType("application/json").content("{\"remark\":\"op edit\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_EDITABLE"));
        mockMvc.perform(put("/api/orders/drafts/" + id).with(asAdmin())
                        .contentType("application/json").content("{\"remark\":\"admin edit\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remark").value("admin edit"));

        mockMvc.perform(post("/api/orders/" + id + "/approve").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.prototypePoNo").exists());

        // Duplicate approve is rejected (409), not double-processed.
        mockMvc.perform(post("/api/orders/" + id + "/approve").with(asAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));

        // Preview after approval reflects APPROVED + the assigned PO No.
        mockMvc.perform(post("/api/orders/drafts/" + id + "/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.prototypePoNo").exists());
    }

    @Test
    @WithUserDetails("purchase01")
    void returnForCorrectionFlowSurfacesReasonOnDraft() throws Exception {
        Long id = createDraft("OD-TENT-001");
        mockMvc.perform(post("/api/orders/" + id + "/submit-for-approval")).andExpect(status().isOk());

        // Reason is mandatory.
        mockMvc.perform(post("/api/orders/" + id + "/return-for-correction").with(asAdmin())
                        .contentType("application/json").content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("RETURN_REASON_REQUIRED"));

        mockMvc.perform(post("/api/orders/" + id + "/return-for-correction").with(asAdmin())
                        .contentType("application/json").content("{\"reason\":\"数量を見直してください\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        // The returned Draft surfaces the reason to the OPERATOR...
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/orders/drafts/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnReason").value("数量を見直してください"));

        // ...who can fix and resubmit.
        mockMvc.perform(put("/api/orders/drafts/" + id)
                        .contentType("application/json").content("{\"remark\":\"fixed\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + id + "/submit-for-approval"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    @WithUserDetails("purchase01")
    void submitWithNoOrderableItemsReturns400() throws Exception {
        Long id = createDraft("OD-CHAIR-001"); // recommendedQty=0 -> orderQty=0 initially

        mockMvc.perform(post("/api/orders/" + id + "/submit-for-approval"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("NO_ORDERABLE_ITEMS"));
    }

    @Test
    @WithUserDetails("purchase01")
    void previewOnNonExistentDraftReturns404() throws Exception {
        mockMvc.perform(post("/api/orders/drafts/999999/preview"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DRAFT_NOT_FOUND"));
    }
}
