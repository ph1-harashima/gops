package com.glv.gsysportal.exception;

/** Thrown when no TR_ARR row matches the requested (supplierCode, poNumber,
 * invoiceNumber) composite key (Phase 8-G 6章). */
public class ArrivalNotFoundException extends RuntimeException {

    public ArrivalNotFoundException(String supplierCode, String poNumber, String invoiceNumber) {
        super("Arrival not found for supplierCode=" + supplierCode + " poNumber=" + poNumber + " invoiceNumber=" + invoiceNumber);
    }
}
