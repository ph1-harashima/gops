package com.glv.gsysportal.exception;

/** Phase 9-A: Excel generation refuses to run while the most recent
 * Preflight result is BLOCKED (Supplier/Brand/Item Master missing in
 * Legacy) - generating an Excel that Legacy will certainly reject provides
 * no value and would confuse the Integration Status trail. */
public class OfficialPoPreflightBlockedException extends RuntimeException {
    public OfficialPoPreflightBlockedException(Long orderId) {
        super("Order " + orderId + "'s most recent Preflight result is BLOCKED - resolve the issues and re-request first");
    }
}
