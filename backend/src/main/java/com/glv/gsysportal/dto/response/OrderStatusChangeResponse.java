package com.glv.gsysportal.dto.response;

import java.time.OffsetDateTime;

/** Response for Confirm Order / Return to Draft. Deliberately minimal - the
 * Frontend re-fetches PO Preview (POST .../preview) or the Draft (GET
 * .../drafts/{id}) to refresh the full screen, rather than this response
 * trying to double as either of those larger DTOs. */
public record OrderStatusChangeResponse(
        Long id,
        String draftNo,
        String prototypePoNo,
        String status,
        String updatedBy,
        OffsetDateTime updatedAt
) {
}
