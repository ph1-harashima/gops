package com.glv.gsysportal.exception;

/** BR-08: Official PO No. auto-numbering requires both the Supplier's and
 * the Brand's 3-character abbreviation to already be registered
 * ({@code official_po_short_code}) - G-OPS never invents one on the fly. */
public class OfficialPoShortCodeNotConfiguredException extends RuntimeException {
    public OfficialPoShortCodeNotConfiguredException(String codeType, String businessCode) {
        super("No active Official PO Short Code registered for " + codeType + " " + businessCode
                + " - an ADMIN must register it (docs/gulliver-20260917-confirmed-business-rules.md BR-08) before an Official PO No. can be auto-numbered");
    }
}
