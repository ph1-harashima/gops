package com.glv.gsysportal.exception;

/** Phase 7-C3 15章: only ja/en are allowed this Phase. */
public class InvalidLanguageException extends RuntimeException {
    public InvalidLanguageException(String language) {
        super("Invalid language (only ja/en allowed): " + language);
    }
}
