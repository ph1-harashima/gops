package com.glv.gsysportal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Stage 5K: enables Spring's {@code @Scheduled} support for {@code
 * DashboardRefreshScheduler}. This is the first {@code @Scheduled} use
 * anywhere in this Portal codebase (checked) - Legacy G-SYS itself has
 * none at all (Stage 5J/Addendum, re-confirmed repeatedly), so there was
 * no precedent to follow until now.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
