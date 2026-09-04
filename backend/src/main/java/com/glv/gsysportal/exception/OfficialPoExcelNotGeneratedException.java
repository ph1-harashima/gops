package com.glv.gsysportal.exception;

/** Phase 9-A: the Excel download endpoint requires generation to have
 * already happened (status GENERATED or later). */
public class OfficialPoExcelNotGeneratedException extends RuntimeException {
    public OfficialPoExcelNotGeneratedException(Long orderId) {
        super("Order " + orderId + " has no generated Official PO Excel yet");
    }
}
