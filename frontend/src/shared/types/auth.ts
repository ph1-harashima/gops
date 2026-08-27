// Mirrors backend com.glv.gsysportal.security.PortalUserPrincipal /
// AuthController responses (Technical Design 7章). role is an internal code
// (e.g. "PURCHASE") - never Japanese text (Requirements MD 30.12).
export interface CurrentUser {
  username: string
  displayName: string
  role: string
}
