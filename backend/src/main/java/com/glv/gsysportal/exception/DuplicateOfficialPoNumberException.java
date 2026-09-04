package com.glv.gsysportal.exception;

/** Phase 9-A: {@code portal_order.official_po_no} carries a partial UNIQUE
 * index (V9 migration) - this wraps that constraint violation with a
 * meaningful error code instead of a raw DB exception leaking to the API. */
public class DuplicateOfficialPoNumberException extends RuntimeException {
    public DuplicateOfficialPoNumberException(String officialPoNo) {
        super("Official PO No. '" + officialPoNo + "' is already used by another Order");
    }
}
