package com.glv.gsysportal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Prototype-only user account backing Spring Security session Form Login.
 * NOT linked to Legacy MS_USER (Technical Design 7章).
 */
@Entity
@Table(name = "portal_user")
@Getter
@Setter
@NoArgsConstructor
public class PortalUser {

    /** Phase 7-C1 production roles (Target Design 4章). Pre-7-C1 demo roles
     * (PURCHASE/SALES_ADMIN/SYS_ADMIN) were mapped by migration V8. */
    public static final String ROLE_OPERATOR = "OPERATOR";
    public static final String ROLE_ADMIN = "ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    /** Phase 7-C1: prepared for the Target "email as login id" (Target
     * Design 17章). Nullable this Phase - login is still username-based;
     * the identifier switch is deliberately split into a later step. */
    @Column(length = 200)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    @Column(nullable = false, length = 30)
    private String role;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
