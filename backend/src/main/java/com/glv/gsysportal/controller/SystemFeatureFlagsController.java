package com.glv.gsysportal.controller;

import com.glv.gsysportal.dto.response.FeatureFlagsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** BR-06 (docs/gulliver-20260917-confirmed-business-rules.md): Demo Send is
 * a Local/Demo/Test-only Test Helper Flow, never a Production Business
 * Function - the Frontend asks this endpoint whether to show it at all,
 * rather than hardcoding a client-side environment guess. Any authenticated
 * user may read it (matches every other read-only endpoint's visibility) -
 * no side effects, nothing sensitive. */
@RestController
public class SystemFeatureFlagsController {

    private final boolean demoSendEnabled;

    public SystemFeatureFlagsController(@Value("${app.demo-features.enabled:true}") boolean demoSendEnabled) {
        this.demoSendEnabled = demoSendEnabled;
    }

    @GetMapping("/api/system/feature-flags")
    public FeatureFlagsResponse get() {
        return new FeatureFlagsResponse(demoSendEnabled);
    }
}
