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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new IllegalStateException("No authenticated Portal user in context");
        }
        return authentication.getName();
    }
}
