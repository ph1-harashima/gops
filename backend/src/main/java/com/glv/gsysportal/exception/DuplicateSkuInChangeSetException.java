package com.glv.gsysportal.exception;

/** Thrown when a SKU is added to a Price Change Set that already contains it
 * (uq_price_change_set_detail_sku, V16 migration). */
public class DuplicateSkuInChangeSetException extends RuntimeException {
    public DuplicateSkuInChangeSetException(String itemCd) {
        super("SKU already in this Change Set: " + itemCd);
    }
}
