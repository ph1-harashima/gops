package com.glv.gsysportal.exception;

/** Post-Freeze Business Refinement: {@code unknown=true} and a non-null
 * {@code expectedRestockDate} are mutually exclusive - "未定と確認済み" and
 * "a real date is known" are two distinct states, never both at once
 * (re-audit doc §6, DB-enforced by {@code ck_sku_expected_restock_unknown_xor_date}). */
public class InvalidSkuExpectedRestockException extends RuntimeException {
    public InvalidSkuExpectedRestockException() {
        super("expectedRestockDate must be null when unknown=true");
    }
}
