package com.glv.gsysportal.dto.request;

/** Gap Analysis C-4 (docs/gulliver-20260917-phase1-gap-analysis.md 9章):
 * "Official POをCancel" - reason is mandatory (validated in
 * {@code OfficialPoIntegrationService.cancel}). */
public record CancelOfficialPoRequest(String reason) {
}
