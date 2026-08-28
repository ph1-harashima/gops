package com.glv.gsysportal.repository.prototype;

import com.glv.gsysportal.domain.PortalUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PortalUserRepository extends JpaRepository<PortalUser, Long> {
    Optional<PortalUser> findByUsername(String username);

    /** Phase 7-C3 3章's Admin CC Rule default (全Active ADMIN - CUSTOMER
     * REVIEW territory, see AdminCcResolutionService). */
    List<PortalUser> findAllByRoleAndEnabledTrue(String role);
}
