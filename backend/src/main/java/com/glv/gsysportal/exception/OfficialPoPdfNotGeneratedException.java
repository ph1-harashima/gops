package com.glv.gsysportal.exception;

/** Gap Analysis C-1 (docs/gulliver-20260917-phase1-gap-analysis.md 7章): the
 * PDF download endpoint requires generation to have already happened -
 * mirrors {@link OfficialPoExcelNotGeneratedException}'s own shape. */
public class OfficialPoPdfNotGeneratedException extends RuntimeException {
    public OfficialPoPdfNotGeneratedException(Long orderId) {
        super("Order " + orderId + " has no generated Official PO PDF yet");
    }
}
