package com.glv.gsysportal.exception;

/** Phase 7-C3 6章: only the 4 candidate types are allowed, even though only
 * PURCHASE_ORDER has real Resolution logic this Phase. */
public class InvalidTemplateTypeException extends RuntimeException {
    public InvalidTemplateTypeException(String templateType) {
        super("Invalid template type: " + templateType);
    }
}
