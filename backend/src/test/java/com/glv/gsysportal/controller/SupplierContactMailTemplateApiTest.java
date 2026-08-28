package com.glv.gsysportal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.glv.gsysportal.repository.prototype.MailTemplateRepository;
import com.glv.gsysportal.repository.prototype.SupplierContactRepository;
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

/**
 * Phase 7-C3 13章/21章 Permission Test: Master CRUD is ADMIN-only for both
 * read and write; Mail Preview is open to any authenticated user. NOT_SUPPORTED
 * propagation for the same reason as ApprovalWorkflowApiTest/
 * OfficialPoIntegrationApiTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager", propagation = Propagation.NOT_SUPPORTED)
class SupplierContactMailTemplateApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SupplierContactRepository supplierContactRepository;
    @Autowired
    private MailTemplateRepository mailTemplateRepository;

    private String contactBody(String email) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "supplierCode", "SUP_ALPHA", "brandCode", "BR_OUTDOOR", "contactName", "Contact",
                "email", email, "contactType", "TO", "language", "ja", "primary", true, "active", true));
    }

    private String templateBody() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "templateName", "Default", "templateType", "PURCHASE_ORDER", "language", "ja",
                "subjectTemplate", "PO {{poNo}}", "bodyTemplate", "Dear {{contactName}}", "active", true));
    }

    // ---- Authentication ------------------------------------------------

    @Test
    void endpointsWithoutAuthenticationReturn401() throws Exception {
        mockMvc.perform(get("/api/admin/supplier-contacts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/mail-templates")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/orders/1/mail-preview")).andExpect(status().isUnauthorized());
    }

    // ---- Authorization: OPERATOR must NOT read or write Masters (7-C3 13章) ----

    @Test
    @WithUserDetails("purchase01")
    void operatorCannotListSupplierContacts() throws Exception {
        mockMvc.perform(get("/api/admin/supplier-contacts"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @WithUserDetails("purchase01")
    void operatorCannotCreateSupplierContact() throws Exception {
        mockMvc.perform(post("/api/admin/supplier-contacts").contentType("application/json").content(contactBody("x@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @WithUserDetails("purchase01")
    void operatorCannotListMailTemplates() throws Exception {
        mockMvc.perform(get("/api/admin/mail-templates"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @WithUserDetails("purchase01")
    void operatorCannotCreateMailTemplate() throws Exception {
        mockMvc.perform(post("/api/admin/mail-templates").contentType("application/json").content(templateBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    // ---- ADMIN CRUD full flow -------------------------------------------

    @Test
    @WithUserDetails("admin01")
    void adminCanCreateListAndUpdateASupplierContact() throws Exception {
        String createResponse = mockMvc.perform(post("/api/admin/supplier-contacts")
                        .contentType("application/json").content(contactBody("flow@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("flow@example.com"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) objectMapper.readValue(createResponse, Map.class).get("id")).longValue();

        mockMvc.perform(get("/api/admin/supplier-contacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").exists());

        String updateBody = objectMapper.writeValueAsString(Map.of(
                "supplierCode", "SUP_ALPHA", "brandCode", "BR_OUTDOOR", "contactName", "Updated",
                "email", "flow@example.com", "contactType", "TO", "language", "ja", "primary", true, "active", false));
        mockMvc.perform(put("/api/admin/supplier-contacts/" + id).contentType("application/json").content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactName").value("Updated"))
                .andExpect(jsonPath("$.active").value(false));

        // This test's transaction is NOT rolled back (NOT_SUPPORTED,
        // required for MockMvc's sequential create/list/update calls to see
        // each other's committed state) - clean up explicitly so a repeat
        // `mvn test` run against the same local Postgres doesn't collide
        // with this row's (supplier, brand, email) identity.
        supplierContactRepository.deleteById(id);
    }

    @Test
    @WithUserDetails("admin01")
    void adminCreatingAContactWithAnInvalidEmailGets400() throws Exception {
        mockMvc.perform(post("/api/admin/supplier-contacts").contentType("application/json").content(contactBody("not-an-email")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_EMAIL_FORMAT"));
    }

    @Test
    @WithUserDetails("admin01")
    void adminCreatingAContactForAnUnknownSupplierGets400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "supplierCode", "SUP_DOES_NOT_EXIST", "contactName", "C", "email", "x@example.com",
                "contactType", "TO", "language", "ja", "primary", false, "active", true));
        mockMvc.perform(post("/api/admin/supplier-contacts").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SUPPLIER_CODE_NOT_FOUND"));
    }

    @Test
    @WithUserDetails("admin01")
    void adminCanCreateAndListMailTemplates() throws Exception {
        String createResponse = mockMvc.perform(post("/api/admin/mail-templates").contentType("application/json").content(templateBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateType").value("PURCHASE_ORDER"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) objectMapper.readValue(createResponse, Map.class).get("id")).longValue();

        mockMvc.perform(get("/api/admin/mail-templates"))
                .andExpect(status().isOk());

        // Same rationale as adminCanCreateListAndUpdateASupplierContact's
        // cleanup - this row's (type, supplier=null, brand=null, language)
        // identity must not linger for a repeat `mvn test` run.
        mailTemplateRepository.deleteById(id);
    }

    // ---- Mail Preview: open to any authenticated user, never sends real mail ----

    @Test
    @WithUserDetails("purchase01")
    void operatorCanCallMailPreview_andItIsBlockedWithoutContactOrTemplate() throws Exception {
        String createResponse = mockMvc.perform(post("/api/orders/drafts")
                        .contentType("application/json").content(objectMapper.writeValueAsString(Map.of("skus", java.util.List.of("OD-TENT-001")))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) objectMapper.readValue(createResponse, Map.class).get("id")).longValue();
        mockMvc.perform(post("/api/orders/" + id + "/submit-for-approval")).andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/" + id + "/approve").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin01").roles("ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders/" + id + "/mail-preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").doesNotExist())
                .andExpect(jsonPath("$.issues").isArray())
                .andExpect(jsonPath("$.issues[?(@.code == 'OFFICIAL_PO_NO_NOT_ASSIGNED')]").exists());
    }

    @Test
    @WithUserDetails("purchase01")
    void mailPreviewOnNonExistentOrderReturns404() throws Exception {
        mockMvc.perform(post("/api/orders/999999/mail-preview"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DRAFT_NOT_FOUND"));
    }
}
