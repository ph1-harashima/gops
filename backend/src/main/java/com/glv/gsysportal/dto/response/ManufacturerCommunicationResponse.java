package com.glv.gsysportal.dto.response;

/**
 * Requirements MD 14章 / implementation instructions 4章/5章: 9/17 Prototype
 * uses fixed Demo configuration values only - never a real Supplier mail
 * address fetched from Legacy, and never wired to any outbound mail system
 * (implementation instructions 0章 external network prohibition). See
 * {@link com.glv.gsysportal.service.DemoManufacturerCommunicationFactory}.
 */
public record ManufacturerCommunicationResponse(
        String to,
        String cc,
        String subject,
        String body,
        String attachment
) {
}
