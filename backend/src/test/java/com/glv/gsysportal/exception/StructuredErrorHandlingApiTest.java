package com.glv.gsysportal.exception;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 8-L (Production Reliability Foundation, Production Readiness Audit
 * §5-8/§23). Covers the Structured Error Response's common fields
 * (timestamp/status/errorCode/path/correlationId), the new VALIDATION_ERROR
 * handler, and Correlation ID generation/propagation/echo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(transactionManager = "prototypeTransactionManager", propagation = Propagation.NOT_SUPPORTED)
class StructuredErrorHandlingApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    /** NOT_FOUND case (pre-existing errorCode, now carrying the new common
     * fields additively - Frontend Contract unchanged, errorCode/shape of
     * existing fields identical to before this Phase). */
    @Test
    @WithUserDetails("purchase01")
    void notFoundResponseCarriesCommonStructuredFields() throws Exception {
        mockMvc.perform(get("/api/orders/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DRAFT_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/orders/999999"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    /** First real exercise of the new VALIDATION_ERROR handler - previously
     * @Valid failures fell through to Spring Boot's default ProblemDetail
     * body, not this project's errorCode convention (see
     * GlobalExceptionHandler's Javadoc). */
    @Test
    @WithUserDetails("admin01")
    void validationFailureReturnsStructuredValidationError() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "supplierCode", "",
                "contactName", "Test",
                "email", "test@example.invalid",
                "contactType", "TO",
                "language", "ja",
                "primary", false,
                "active", true
        ));
        mockMvc.perform(post("/api/admin/supplier-contacts").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields").isArray())
                .andExpect(jsonPath("$.fields[0]").value("supplierCode"))
                // Never a human-readable violation message.
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void correlationIdIsGeneratedWhenNotSupplied() throws Exception {
        mockMvc.perform(get("/api/order-candidates"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void correlationIdSuppliedByClientIsEchoedBack() throws Exception {
        mockMvc.perform(get("/api/order-candidates").header("X-Correlation-Id", "test-corr-8l-001"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "test-corr-8l-001"));
    }

    /** An unsafe/oversized client-supplied id must never be reflected back
     * verbatim - a fresh one is generated instead. */
    @Test
    void unsafeCorrelationIdIsReplacedWithAGeneratedOne() throws Exception {
        String unsafe = "not safe! <script>" + "x".repeat(200);
        var result = mockMvc.perform(get("/api/order-candidates").header("X-Correlation-Id", unsafe))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"))
                .andReturn();
        String echoed = result.getResponse().getHeader("X-Correlation-Id");
        org.junit.jupiter.api.Assertions.assertNotEquals(unsafe, echoed);
    }
}
