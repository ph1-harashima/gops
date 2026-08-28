package com.glv.gsysportal.exception;

public class MailTemplateNotFoundException extends RuntimeException {
    public MailTemplateNotFoundException(Long id) {
        super("Mail Template not found: " + id);
    }
}
