package com.glv.gsysportal.safety;

/**
 * Thrown when {@link SafetyGuardEnvironmentPostProcessor} detects a connection
 * target, database name, or profile that is not on the local-Prototype
 * allowlist. Thrown during environment post-processing (before any DataSource
 * bean is created), which aborts {@code SpringApplication.run()} - the
 * application never reaches a state where it could open a network connection.
 */
public class SafetyGuardViolationException extends RuntimeException {

    public SafetyGuardViolationException(String message) {
        super(message);
    }
}
