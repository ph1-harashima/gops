package com.glv.gsysportal.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/** Gap Analysis §11 (docs/gulliver-20260917-phase1-gap-analysis.md 11章). */
public record PortalMailSettingsResponse(
        List<String> defaultCc,
        String updatedBy,
        OffsetDateTime updatedAt
) {
}
