// Protected DB Direct Reference Guard - single source of truth.
//
// Background: an earlier audit found 5 Playwright spec files whose
// test-fixture setup called `docker exec ... psql/mysql` DIRECTLY, with the
// real gsys_portal/legacy_demo database names hardcoded - completely
// bypassing the application's own SafetyGuard/datasource isolation (which
// can only ever protect connections the app itself makes). Those 5 files
// were fixed to read E2E_PROTOTYPE_DB/E2E_LEGACY_DB instead, but nothing
// stopped a FUTURE spec file from reintroducing the same hardcoded pattern.
//
// This module is that "something": a static scan of every frontend/e2e
// spec/helper file for a literal reference to the real Demo database names
// (or the ports they listen on) that is NOT going through the
// E2E_PROTOTYPE_DB/E2E_LEGACY_DB parameterization. It is used from TWO
// places, both importing this exact module (never a second, drifting copy
// of the same regex):
//   1. backend/e2e-run.sh runs it directly via `node` before starting
//      Playwright.
//   2. frontend/e2e/guard/global-setup.ts runs it as Playwright's own
//      globalSetup, so a direct `npx playwright test` (bypassing
//      e2e-run.sh entirely) is covered too.
//
// Detection rule (deliberately simple and auditable - a security guard
// nobody can understand is a guard nobody will trust):
//   - "gsys_portal" not immediately followed by "_e2e" is unsafe UNLESS the
//     same line also mentions E2E_PROTOTYPE_DB (the parameterized-fallback
//     idiom `process.env.E2E_PROTOTYPE_DB || 'gsys_portal'` legitimately
//     contains the literal name as a default value, not a hardcoded target).
//   - "legacy_demo" not immediately followed by "_e2e" is unsafe under the
//     same exemption, keyed on E2E_LEGACY_DB.
//   - The Protected DB's ports (54321 Postgres, 33061 MySQL) are also
//     flagged when they appear on a line that looks like a raw connection
//     (psql/mysql/jdbc/docker exec) and does not mention _e2e or either
//     E2E_*_DB variable - defense in depth for a host/port-only bypass that
//     never spells out the database name at all.

import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const PROTECTED_PROTOTYPE_DB = 'gsys_portal'
const PROTECTED_LEGACY_DB = 'legacy_demo'
const PROTECTED_PROTOTYPE_PORT = '54321'
const PROTECTED_LEGACY_PORT = '33061'

// Matches "gsys_portal"/"legacy_demo" NOT followed by "_e2e" - this is what
// keeps gsys_portal_e2e/legacy_demo_e2e themselves from ever being flagged.
// The negative lookbehind for "-U " additionally exempts the Postgres
// username flag (`psql -U gsys_portal -d ...`) - "gsys_portal" is also this
// project's fixed DB username (demo-data/03-readonly-user.sql,
// docker-compose.yml), identical for the Demo and E2E-only databases alike,
// so its appearance there is never itself a database-selection bypass.
const UNSAFE_PROTOTYPE_RE = /(?<!-U )gsys_portal(?!_e2e)/
const UNSAFE_LEGACY_RE = /legacy_demo(?!_e2e)/
const CONNECTION_LOOKING_RE = /psql|mysql|jdbc:|docker exec/i

/** Strips slash-star block comments (this codebase writes most of its
 * explanatory prose as JSDoc-style block comments, which legitimately
 * discuss the Protected DB names by name - as this very file's own header
 * does) while preserving every newline, so reported line numbers stay
 * correct. Does not attempt to understand a block-comment marker inside a
 * string literal; given this codebase's actual style, that tradeoff
 * produces zero observed false positives while keeping this small. */
function stripBlockComments(fileContent) {
    return fileContent.replace(/\/\*[\s\S]*?\*\//g, (match) => match.replace(/[^\n]/g, ' '))
}

/** Strips a trailing `//` line comment so explanatory prose that merely
 * MENTIONS the Protected DB names never itself trips the guard - only
 * executable code is scanned. Does not attempt to understand `//` inside a
 * string literal; same tradeoff as {@link stripBlockComments}. */
function stripLineComment(line) {
    const idx = line.indexOf('//')
    return idx === -1 ? line : line.slice(0, idx)
}

/**
 * @param {string} rootDir absolute path to the frontend/e2e directory to scan.
 * @returns {{file: string, line: number, text: string, reason: string}[]}
 */
export function findProtectedDbViolations(rootDir) {
    const violations = []
    for (const filePath of listTsFiles(rootDir)) {
        const rawContent = readFileSync(filePath, 'utf8')
        const lines = stripBlockComments(rawContent).split(/\r?\n/)
        const originalLines = rawContent.split(/\r?\n/)
        lines.forEach((lineTextNoBlockComments, idx) => {
            const lineText = originalLines[idx]
            const code = stripLineComment(lineTextNoBlockComments)
            const hasPrototypeEnvVar = code.includes('E2E_PROTOTYPE_DB')
            const hasLegacyEnvVar = code.includes('E2E_LEGACY_DB')

            if (UNSAFE_PROTOTYPE_RE.test(code) && !hasPrototypeEnvVar) {
                violations.push(violation(rootDir, filePath, idx, lineText,
                    `Hardcoded reference to the Protected "${PROTECTED_PROTOTYPE_DB}" database without going through E2E_PROTOTYPE_DB`))
            }
            if (UNSAFE_LEGACY_RE.test(code) && !hasLegacyEnvVar) {
                violations.push(violation(rootDir, filePath, idx, lineText,
                    `Hardcoded reference to the Protected "${PROTECTED_LEGACY_DB}" database without going through E2E_LEGACY_DB`))
            }
            const mentionsProtectedPort = code.includes(PROTECTED_PROTOTYPE_PORT) || code.includes(PROTECTED_LEGACY_PORT)
            if (mentionsProtectedPort && CONNECTION_LOOKING_RE.test(code)
                    && !code.includes('_e2e') && !hasPrototypeEnvVar && !hasLegacyEnvVar) {
                violations.push(violation(rootDir, filePath, idx, lineText,
                    'Raw connection-looking line references a Protected DB host/port without an _e2e database name or E2E_*_DB variable'))
            }
        })
    }
    return violations
}

function violation(rootDir, filePath, zeroBasedLine, text, reason) {
    return {
        file: relative(rootDir, filePath).replace(/\\/g, '/'),
        line: zeroBasedLine + 1,
        text: text.trim(),
        reason,
    }
}

function listTsFiles(dir) {
    const results = []
    for (const entry of readdirSync(dir)) {
        // The guard's own scaffolding never itself constitutes an E2E DB
        // access path - excluded so its own comments/tests (which quote the
        // unsafe patterns verbatim, on purpose) never self-trigger.
        if (entry === 'guard') continue
        const full = join(dir, entry)
        const stats = statSync(full)
        if (stats.isDirectory()) {
            results.push(...listTsFiles(full))
        } else if (entry.endsWith('.ts')) {
            results.push(full)
        }
    }
    return results
}

/** CLI entry point - `node protected-db-guard.mjs [e2eDir]`. Exit 0 = clean, 1 = violations found. */
function runCli() {
    const e2eDir = process.argv[2] ?? fileURLToPath(new URL('..', import.meta.url))
    const violations = findProtectedDbViolations(e2eDir)
    if (violations.length === 0) {
        console.log(`Protected DB Guard: no direct references found under ${e2eDir}.`)
        return 0
    }
    console.error(`Protected DB Guard: ${violations.length} violation(s) found under ${e2eDir}:`)
    for (const v of violations) {
        console.error(`  ${v.file}:${v.line}: ${v.reason}`)
        console.error(`    ${v.text}`)
    }
    return 1
}

const isMain = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href
if (isMain) {
    process.exit(runCli())
}
