package com.glv.gsysportal.exception;

/** Gap Analysis C-2/C-3 (docs/gulliver-20260917-phase1-gap-analysis.md 7章/8章):
 * Reissue is a human-confirmed action gated on the same detection Phase 6's
 * "Revision Required" banner uses - it is refused when nothing was ever
 * issued for the current ACTIVE Document, or when no correction (Order
 * Revision) has happened since it was issued. Prevents an ADMIN from
 * accidentally creating an empty extra Revision by clicking Reissue when
 * there is nothing to reissue for. */
public class OfficialPoReissueNotRequiredException extends RuntimeException {
    public OfficialPoReissueNotRequiredException(Long orderId) {
        super("Order " + orderId + " has no Official PO reissue pending - nothing to reissue");
    }
}
