/**
 * Phase 6-A: shared helpers for the `returnTo` navigation chain
 * (docs/production-ux-workflow-redesign.md 6.3節).
 *
 * `returnTo` always carries a full in-app path + query (e.g.
 * "/candidates?brandCode=BR_HOME") so a Detail/Draft/Preview/Supplier
 * Response screen's "back" button can restore the exact List state the user
 * came from - entirely via the URL (no client-side storage), so it keeps
 * working across browser Back/Forward and a bookmarked/typed URL alike.
 *
 * Every screen along a chain (List -> Detail -> Draft -> Preview -> ...)
 * re-attaches the SAME returnTo value onto whatever it links to next, so
 * the original List is always reachable again regardless of how many
 * intermediate screens were visited (implementation instructions Phase 6-A
 * 4章/9章).
 *
 * Security (implementation instructions Phase 6-A 11章): `returnTo` is
 * attacker-influenceable (it round-trips through the URL bar), so it is
 * only ever used as a react-router `navigate()` target, never as a raw
 * `window.location` assignment, and is validated by isSafeInternalPath()
 * before use. http://, https://, // (protocol-relative), and anything not
 * starting with exactly one "/" are rejected in favor of a caller-supplied
 * fallback (Open Redirect prevention).
 */

const RETURN_TO_PARAM = 'returnTo'

/** True only for a same-origin, in-app path: starts with exactly one "/",
 * never "//" (protocol-relative), and contains no "://" (absolute URL). */
export function isSafeInternalPath(path: string): boolean {
  if (typeof path !== 'string' || path.length === 0) return false
  if (!path.startsWith('/')) return false
  if (path.startsWith('//')) return false
  if (path.includes('://')) return false
  // Guard against "/\evil.com"-style paths some browsers normalize to "//".
  if (path.startsWith('/\\')) return false
  return true
}

/** Builds the `returnTo` value for a List screen's OWN current URL - pass
 * this to withReturnTo() when linking from that List to a Detail/Draft/etc. */
export function listReturnTo(pathname: string, searchParams: URLSearchParams): string {
  const query = searchParams.toString()
  return query ? `${pathname}?${query}` : pathname
}

/** Appends a `returnTo` param (if present and safe) onto `path`. Use when
 * building a navigate() target that should carry the current returnTo
 * chain forward (either a brand-new List URL, or one received via
 * resolveReturnTo/useSearchParams on the current screen). A missing or
 * unsafe `returnTo` leaves `path` unchanged - never throws. */
export function withReturnTo(path: string, returnTo: string | null | undefined): string {
  if (!returnTo || !isSafeInternalPath(returnTo)) return path
  const [base, existingQuery] = path.split('?')
  const params = new URLSearchParams(existingQuery)
  params.set(RETURN_TO_PARAM, returnTo)
  return `${base}?${params.toString()}`
}

/** Resolves the safe "back" navigation target: `raw` (usually
 * `searchParams.get('returnTo')`) if present and internal, otherwise
 * `fallback`. Never navigates to an external/unsafe URL. */
export function resolveReturnTo(raw: string | null | undefined, fallback: string): string {
  if (raw && isSafeInternalPath(raw)) return raw
  return fallback
}
