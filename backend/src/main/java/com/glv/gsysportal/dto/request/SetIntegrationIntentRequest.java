package com.glv.gsysportal.dto.request;

/** Phase 7-C6 12章/13章: {@code intent} must be "NEW" or "UPDATE" -
 * validated in {@code OfficialPoIntegrationService}. */
public record SetIntegrationIntentRequest(String intent) {
}
