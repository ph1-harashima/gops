package com.glv.gsysportal.controller;

import com.glv.gsysportal.security.PortalUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * /api/auth/login and /api/auth/logout are handled directly by Spring
 * Security's formLogin/logout filters (SecurityConfig) - this controller only
 * adds the session-check endpoint the Frontend needs on load.
 */
@RestController
public class AuthController {

    @GetMapping("/api/auth/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof PortalUserPrincipal principal)) {
            return ResponseEntity.status(401).body(Map.of("errorCode", "NOT_AUTHENTICATED"));
        }
        return ResponseEntity.ok(Map.of(
                "username", principal.getUsername(),
                "displayName", principal.displayName(),
                "role", principal.role()
        ));
    }
}
