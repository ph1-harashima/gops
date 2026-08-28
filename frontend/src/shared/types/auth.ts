// Mirrors backend com.glv.gsysportal.security.PortalUserPrincipal /
// AuthController responses (Technical Design 7章). role is an internal code
// (e.g. "PURCHASE") - never Japanese text (Requirements MD 30.12).
export interface CurrentUser {
  username: string
  displayName: string
  role: string
}

/** Phase 7-C1: the two production roles (Target Design 4章). Pre-7-C1 demo
 * roles (PURCHASE/SALES_ADMIN/SYS_ADMIN) no longer exist - migration V8
 * mapped every account onto one of these two. */
export const ROLE_OPERATOR = 'OPERATOR'
export const ROLE_ADMIN = 'ADMIN'
