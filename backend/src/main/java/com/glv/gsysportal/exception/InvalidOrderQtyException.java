package com.glv.gsysportal.exception;

/** Order Qty must be an integer >= 0 (Requirements MD 13章 Order Qty Validation). */
public class InvalidOrderQtyException extends RuntimeException {
    public InvalidOrderQtyException(Integer value) {
        super("Order Qty must be >= 0, got: " + value);
    }
}
