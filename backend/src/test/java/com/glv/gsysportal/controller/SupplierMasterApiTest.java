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
 * Master Maintenance Hub (docs/gops-master-maintenance-hub-implementation.md):
 * Supplier List/Settings is ADMIN-only, both endpoints - same Target
 * Permission Matrix row as every other Master Maintenance screen, mirrors
 * ManufacturerChannelApiTest's own permission-test shape exactly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupplierMasterApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void endpointsWithoutAuthenticationReturn401() throws Exception {
        mockMvc.perform(get("/api/admin/suppliers")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/suppliers/SUP_ALPHA")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithUserDetails("purchase01")
    void operatorCannotListSuppliers() throws Exception {
        mockMvc.perform(get("/api/admin/suppliers"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @WithUserDetails("purchase01")
    void operatorCannotGetSupplier() throws Exception {
        mockMvc.perform(get("/api/admin/suppliers/SUP_ALPHA"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    /** Stage 5H Systematic Performance Remediation (RC-J, docs/real-data-audit/
     * gops-stage5h-systematic-performance-remediation.md): now a
     * {@code PageResponse} envelope, not a plain array - size=1000 keeps
     * this test independent of the Demo fixture's own Supplier count/
     * ordering rather than assuming SUP_ALPHA falls on the default page. */
    @Test
    @WithUserDetails("admin01")
    void adminCanListSuppliers() throws Exception {
        mockMvc.perform(get("/api/admin/suppliers").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.supplierCode == 'SUP_ALPHA')]").exists());
    }

    @Test
    @WithUserDetails("admin01")
    void adminCanGetSupplier() throws Exception {
        mockMvc.perform(get("/api/admin/suppliers/SUP_ALPHA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplierCode").value("SUP_ALPHA"));
    }

    @Test
    @WithUserDetails("admin01")
    void adminGettingUnknownSupplierCode() throws Exception {
        // Reuses the EXISTING SupplierCodeNotFoundException/handler as-is
        // (400 SUPPLIER_CODE_NOT_FOUND, the same response every other Master
        // Maintenance Create already returns for an unknown Supplier Code) -
        // no new exception/handler introduced just for this GET.
        mockMvc.perform(get("/api/admin/suppliers/NO_SUCH_SUPPLIER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SUPPLIER_CODE_NOT_FOUND"));
    }
}
