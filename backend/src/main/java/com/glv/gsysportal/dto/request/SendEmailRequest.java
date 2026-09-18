package com.glv.gsysportal.dto.request;

import java.util.List;

/** Gap Analysis C-5 (docs/gulliver-20260917-phase1-gap-analysis.md 10章):
 * optional per-Send To/CC Override - null/empty means "use the
 * Master-resolved addresses as-is" (existing Phase 9-E behavior, unchanged).
 * Never persisted to any Master (Supplier Contact) - a one-time override of
 * what is actually sent THIS call only. */
public record SendEmailRequest(List<String> to, List<String> cc) {

    public static final SendEmailRequest NONE = new SendEmailRequest(null, null);
}
