package com.glv.gsysportal.exception;

/** Phase 9-A: Official PO No. must be non-blank and no more than 30
 * characters (Legacy TR_PO.PO_NO / Const.LEN_TR_PO_PO_NO - the ONLY
 * confirmed length rule; no ID Code/separator structure is enforced, per
 * the Working Assumption that Portal must not hardcode an unconfirmed
 * numbering Business Rule). */
public class InvalidOfficialPoNumberException extends RuntimeException {
    public InvalidOfficialPoNumberException(String officialPoNo) {
        super("Official PO No. must be 1-30 characters: '" + officialPoNo + "'");
    }
}
