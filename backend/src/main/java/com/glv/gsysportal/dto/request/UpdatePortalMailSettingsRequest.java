package com.glv.gsysportal.dto.request;

import java.util.List;

/** Gap Analysis §11 (docs/gulliver-20260917-phase1-gap-analysis.md 11章).
 * {@code defaultCc} may be an empty list (clears the setting) - each entry
 * is validated as a non-blank string only, no email-format enforcement. */
public record UpdatePortalMailSettingsRequest(List<String> defaultCc) {
}
