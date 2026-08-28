package com.glv.gsysportal.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for created_by/updated_by/performed_by across all
 * Services - never a fixed/hardcoded string (implementation instructions
 * 15章: "Audit用のperformed_byを固定文字列にしない").
 */
@Component
public class CurrentUserProvider {

    public String currentUsername() {
        return authenticated().getName();
    }

    /** Phase 7-C1: role-aware ownership checks (Draft editable by its
     * creator OR any ADMIN) need the caller's role, not just the name.
     * Endpoint-level ADMIN gating uses @PreAuthorize instead - this is only
     * for the finer-grained in-service rules. */
    public boolean isAdmin() {
        return authenticated().getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private Authentication authenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new IllegalStateException("No authenticated Portal user in context");
        }
        return authentication;
    }
}
