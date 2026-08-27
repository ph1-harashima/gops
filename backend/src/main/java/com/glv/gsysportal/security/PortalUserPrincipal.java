package com.glv.gsysportal.security;

import com.glv.gsysportal.domain.PortalUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

/**
 * Wraps {@link PortalUser} as a Spring Security {@link UserDetails}, so
 * {@code Authentication.getName()} yields the username used as
 * created_by/updated_by/performed_by throughout Audit Trail records
 * (Technical Design 7章; implementation instructions 15章).
 */
public class PortalUserPrincipal implements UserDetails {

    private final PortalUser portalUser;

    public PortalUserPrincipal(PortalUser portalUser) {
        this.portalUser = portalUser;
    }

    public String displayName() {
        return portalUser.getDisplayName();
    }

    public String role() {
        return portalUser.getRole();
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + portalUser.getRole()));
    }

    @Override
    public String getPassword() {
        return portalUser.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return portalUser.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return portalUser.isEnabled();
    }
}
