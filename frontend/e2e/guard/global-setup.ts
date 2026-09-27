import { findProtectedDbViolations } from './protected-db-guard.mjs'
import { fileURLToPath } from 'node:url'

/**
 * Playwright's own `globalSetup` hook - runs once before any test, REGARDLESS
 * of how Playwright was invoked. This is what makes the Protected DB Guard
 * effective even when someone runs `npx playwright test` directly, bypassing
 * backend/e2e-run.sh (which also runs the exact same
 * protected-db-guard.mjs module - see that script - never a second,
 * drifting copy of the same check).
 *
 * Throwing here aborts the whole run before a single test/browser starts
 * (Playwright's documented globalSetup contract) - the correct place for a
 * fail-fast guard, not a beforeAll hook that only some tests would hit.
 */
export default function globalSetup() {
    const e2eDir = fileURLToPath(new URL('..', import.meta.url))

    const violations = findProtectedDbViolations(e2eDir)
    if (violations.length > 0) {
        const details = violations.map(v => `  ${v.file}:${v.line}: ${v.reason}\n    ${v.text}`).join('\n')
        throw new Error(
            `Protected DB Guard: refusing to start Playwright - ${violations.length} direct reference(s) to the ` +
            `Protected gsys_portal/legacy_demo database(s) found under frontend/e2e (bypassing E2E_PROTOTYPE_DB/` +
            `E2E_LEGACY_DB isolation):\n${details}\n\n` +
            `Fix the offending file(s) to read process.env.E2E_PROTOTYPE_DB / E2E_LEGACY_DB instead of a ` +
            `hardcoded database name before running Full E2E again.`
        )
    }

    // E2E Connection-Target Guard (test-infrastructure-only - no production
    // application code is touched here): when these variables ARE set (the
    // normal case when launched via backend/e2e-run.sh), they must actually
    // point at an "_e2e" database - catches a misconfiguration (e.g. a typo
    // that sets E2E_PROTOTYPE_DB=gsys_portal) before any fixture-setup code
    // runs. When unset, this only warns - most spec files never read these
    // variables at all, so requiring them unconditionally would break every
    // ordinary single-file `npx playwright test some-other-spec.ts` run
    // that has nothing to do with the 5 specs that use them.
    for (const [envVar, mustContain] of [
        ['E2E_PROTOTYPE_DB', '_e2e'],
        ['E2E_LEGACY_DB', '_e2e'],
    ] as const) {
        const value = process.env[envVar]
        if (value === undefined) {
            console.warn(`Protected DB Guard: ${envVar} is not set - fixture helpers that read it will fall back ` +
                `to the real Demo database name. This is expected for an ad-hoc single-spec run outside ` +
                `backend/e2e-run.sh; it is NOT expected for a Full E2E run.`)
        } else if (!value.includes(mustContain)) {
            throw new Error(
                `Protected DB Guard: ${envVar}="${value}" does not look like an E2E-only database name ` +
                `(expected it to contain "${mustContain}"). Refusing to start Playwright - this would write ` +
                `fixture data into what looks like the Protected Demo database.`
            )
        }
    }
}
