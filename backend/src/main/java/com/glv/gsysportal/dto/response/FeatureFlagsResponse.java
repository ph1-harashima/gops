package com.glv.gsysportal.dto.response;

/** BR-06 (docs/gulliver-20260917-confirmed-business-rules.md): read-only,
 * no side effects, safe for any authenticated user (nothing sensitive - a
 * single environment-driven boolean). */
public record FeatureFlagsResponse(boolean demoSendEnabled) {
}
