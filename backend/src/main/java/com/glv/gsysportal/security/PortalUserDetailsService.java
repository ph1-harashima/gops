package com.glv.gsysportal.security;

import com.glv.gsysportal.repository.prototype.PortalUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class PortalUserDetailsService implements UserDetailsService {

    private final PortalUserRepository portalUserRepository;

    public PortalUserDetailsService(PortalUserRepository portalUserRepository) {
        this.portalUserRepository = portalUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return portalUserRepository.findByUsername(username)
                .map(PortalUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown username: " + username));
    }
}
