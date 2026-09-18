package com.glv.gsysportal.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.glv.gsysportal.repository.prototype.ManufacturerChannelRepository;
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

/** Phase 9-D Permission Test: Manufacturer Channel Master CRUD is ADMIN-only
 * for both read and write - mirrors SupplierContactMailTemplateApiTest's
 * own shape exactly. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager", propagation = Propagation.NOT_SUPPORTED)
class ManufacturerChannelApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ManufacturerChannelRepository manufacturerChannelRepository;

    private String body(String supplierCode, String channel) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "supplierCode", supplierCode, "brandCode", "BR_KITCHEN", "channel", channel, "active", true));
    }

    @Test
    void endpointsWithoutAuthenticationReturn401() throws Exception {
        mockMvc.perform(get("/api/admin/manufacturer-channels")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithUserDetails("purchase01")
    void operatorCannotListManufacturerChannels() throws Exception {
        mockMvc.perform(get("/api/admin/manufacturer-channels"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @WithUserDetails("purchase01")
    void operatorCannotCreateManufacturerChannel() throws Exception {
        mockMvc.perform(post("/api/admin/manufacturer-channels").contentType("application/json").content(body("SUP_GAMMA", "EMAIL")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @WithUserDetails("admin01")
    void adminCanCreateListAndUpdateManufacturerChannel() throws Exception {
        String createResult = mockMvc.perform(post("/api/admin/manufacturer-channels")
                        .contentType("application/json").content(body("SUP_GAMMA", "EDI")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.channel").value("EDI"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(createResult).get("id").asLong();

        mockMvc.perform(get("/api/admin/manufacturer-channels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].channel").value("EDI"));

        mockMvc.perform(put("/api/admin/manufacturer-channels/" + id)
                        .contentType("application/json").content(body("SUP_GAMMA", "EMAIL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("EMAIL"));

        // Gulliver UI最終安定化 #3: this test's transaction is NOT rolled back
        // (NOT_SUPPORTED, required for MockMvc's sequential create/list/update
        // calls to see each other's committed state - same reason as
        // SupplierContactMailTemplateApiTest, which this class already
        // mirrors). Without an explicit cleanup, this row's (supplierCode,
        // brandCode) identity (SUP_GAMMA/BR_KITCHEN) stayed committed after
        // the test finished, so a second `mvn test` run against the same
        // local Postgres collided with a 409 on the same POST. Clean up
        // explicitly, matching the sibling test's own precedent exactly.
        manufacturerChannelRepository.deleteById(id);
    }

    @Test
    @WithUserDetails("admin01")
    void adminCreatingInvalidChannelGets400() throws Exception {
        mockMvc.perform(post("/api/admin/manufacturer-channels")
                        .contentType("application/json").content(body("SUP_GAMMA", "FAX")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_MANUFACTURER_CHANNEL"));
    }
}
