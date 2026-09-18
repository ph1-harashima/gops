package com.glv.gsysportal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.glv.gsysportal.repository.prototype.SupplierRegionClassificationRepository;
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

/** Gap Analysis §12 (docs/gulliver-20260917-phase1-gap-analysis.md 12章)
 * Permission Test: Region Classification Master CRUD is ADMIN-only for both
 * read and write - mirrors ManufacturerChannelApiTest's own shape exactly. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager", propagation = Propagation.NOT_SUPPORTED)
class SupplierRegionClassificationApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SupplierRegionClassificationRepository repository;

    private String body(String supplierCode, String value) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "supplierCode", supplierCode, "brandCode", "BR_KITCHEN", "regionClassification", value, "active", true));
    }

    @Test
    void endpointsWithoutAuthenticationReturn401() throws Exception {
        mockMvc.perform(get("/api/admin/supplier-region-classifications")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithUserDetails("purchase01")
    void operatorCannotListRegionClassifications() throws Exception {
        mockMvc.perform(get("/api/admin/supplier-region-classifications"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @WithUserDetails("purchase01")
    void operatorCannotCreateRegionClassification() throws Exception {
        mockMvc.perform(post("/api/admin/supplier-region-classifications").contentType("application/json").content(body("SUP_GAMMA", "DOMESTIC")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @WithUserDetails("admin01")
    void adminCanCreateListAndUpdateRegionClassification() throws Exception {
        String createResult = mockMvc.perform(post("/api/admin/supplier-region-classifications")
                        .contentType("application/json").content(body("SUP_GAMMA", "OVERSEAS")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.regionClassification").value("OVERSEAS"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(createResult).get("id").asLong();

        mockMvc.perform(get("/api/admin/supplier-region-classifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].regionClassification").value("OVERSEAS"));

        mockMvc.perform(put("/api/admin/supplier-region-classifications/" + id)
                        .contentType("application/json").content(body("SUP_GAMMA", "DOMESTIC")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regionClassification").value("DOMESTIC"));

        // Same NOT_SUPPORTED / explicit-cleanup precedent as
        // ManufacturerChannelApiTest - this row's (supplierCode, brandCode)
        // identity stays committed after the test unless cleaned up here.
        repository.deleteById(id);
    }

    @Test
    @WithUserDetails("admin01")
    void adminCreatingInvalidValueGets400() throws Exception {
        mockMvc.perform(post("/api/admin/supplier-region-classifications")
                        .contentType("application/json").content(body("SUP_GAMMA", "MARS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_SUPPLIER_REGION_CLASSIFICATION"));
    }
}
