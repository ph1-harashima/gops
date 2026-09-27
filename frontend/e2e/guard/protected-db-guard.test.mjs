// Protected DB Guard - self-test (A-H per the governing audit's Part 8).
//
// Standalone Node script, not a vitest suite: vitest.config.ts scopes
// vitest to src/**/*.test.{ts,tsx} only (Playwright owns everything under
// e2e/), so this exercises the guard directly against small throwaway
// fixture files under a temp directory. Run with:
//   node frontend/e2e/guard/protected-db-guard.test.mjs

import { mkdtempSync, writeFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { findProtectedDbViolations } from './protected-db-guard.mjs'

const cases = [
    {
        name: 'A: docker exec ... -d gsys_portal',
        code: `function link(id, po) {\n  execSync(\`docker exec gsys-prototype-postgres psql -U gsys_portal -d gsys_portal -c "UPDATE portal_order SET official_po_no='\${po}' WHERE id=\${id};"\`)\n}\n`,
        expectViolation: true,
    },
    {
        name: 'B: docker exec ... legacy_demo',
        code: `function exec(sql) {\n  execSync(\`docker exec gsys-legacy-demo-mysql mysql -uroot -proot_demo_pw legacy_demo -e "\${sql}"\`)\n}\n`,
        expectViolation: true,
    },
    {
        name: 'C: gsys_portal_e2e',
        code: `const url = 'jdbc:postgresql://localhost:54321/gsys_portal_e2e'\n`,
        expectViolation: false,
    },
    {
        name: 'D: legacy_demo_e2e',
        code: `execSync('docker exec gsys-legacy-demo-mysql mysql -uroot -proot_demo_pw legacy_demo_e2e -e "SELECT 1"')\n`,
        expectViolation: false,
    },
    {
        name: 'E: E2E_PROTOTYPE_DB parameterized fallback',
        code: `const PROTOTYPE_DB_NAME = process.env.E2E_PROTOTYPE_DB || 'gsys_portal'\nexecSync(\`docker exec gsys-prototype-postgres psql -U gsys_portal -d \${PROTOTYPE_DB_NAME} -c "SELECT 1"\`)\n`,
        expectViolation: false,
    },
    {
        name: 'F: E2E_LEGACY_DB parameterized fallback',
        code: `const LEGACY_DB_NAME = process.env.E2E_LEGACY_DB || 'legacy_demo'\nexecSync(\`docker exec gsys-legacy-demo-mysql mysql -uroot -proot_demo_pw \${LEGACY_DB_NAME} -e "SELECT 1"\`)\n`,
        expectViolation: false,
    },
    {
        name: 'G: Protected DB host/port direct specification',
        code: `execSync('psql -h localhost -p 54321 -U gsys_portal -d gsys_portal -c "SELECT 1"')\n`,
        expectViolation: true,
    },
    {
        name: 'H: ordinary E2E spec (no Protected DB reference)',
        code: `import { test, expect } from '@playwright/test'\ntest('normal', async ({ page }) => {\n  await page.goto('/orders/history')\n  await expect(page.getByTestId('order-history-table-container')).toBeVisible()\n})\n`,
        expectViolation: false,
    },
]

const dir = mkdtempSync(join(tmpdir(), 'protected-db-guard-selftest-'))
let failures = 0
try {
    for (const [i, c] of cases.entries()) {
        const file = join(dir, `case-${i}.spec.ts`)
        writeFileSync(file, c.code, 'utf8')
        const violations = findProtectedDbViolations(dir)
            .filter(v => v.file.endsWith(`case-${i}.spec.ts`))
        const got = violations.length > 0
        const ok = got === c.expectViolation
        console.log(`${ok ? 'PASS' : 'FAIL'} - ${c.name} (expected violation=${c.expectViolation}, got=${got})`)
        if (!ok) {
            failures++
            for (const v of violations) console.log(`    ${v.reason}: ${v.text}`)
        }
        rmSync(file)
    }
} finally {
    rmSync(dir, { recursive: true, force: true })
}

if (failures > 0) {
    console.error(`\n${failures} of ${cases.length} guard self-test case(s) FAILED.`)
    process.exit(1)
}
console.log(`\nAll ${cases.length} guard self-test cases passed.`)
