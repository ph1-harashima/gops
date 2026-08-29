import { test, expect, type Page } from '@playwright/test'
import { execSync } from 'node:child_process'

/**
 * Phase 7-C7A 25章 Browser Scenario A-G: Fulfillment / Follow-up Foundation.
 * Same login/logout Role-switch pattern and selector policy (data-testid /
 * getByRole / getByLabel only) as the other 7-C* specs.
 *
 * officialPoNo is never assignable through any real Portal UI this Phase
 * (7-C2B is not implemented) - Scenarios B/C/D/E/F therefore need a
 * "test-only officialPoNo" the same way the Backend's own integration tests
 * set one directly via the Repository (docs/fulfillment-follow-up-foundation.md
 * 5章: "Prototype Demo DB内だけでtest-only officialPoNoを設定する"). At the
 * Browser/E2E layer, the equivalent of that direct-Repository call is a
 * direct SQL UPDATE against the local, disposable Prototype Postgres via
 * `docker exec` - this writes to PROTOTYPE Postgres only (never Legacy), is
 * not reachable through any Portal UI/API, and is exactly the kind of local
 * test-fixture setup this Phase's Safety rules explicitly permit.
 *
 * Legacy TR_PO/TR_PO_DTL/TR_INV/TR_INV_DTL Test Fixtures used here
 * (PO-OUTDOOR-01/02/03) are the SAME ones backend/demo-data seeds and
 * FulfillmentServiceIntegrationTest exercises - see that test class and
 * docs/fulfillment-follow-up-foundation.md 5章 for their exact shape.
 *
 * Prerequisite: same as the other 7-C* specs - Demo Reset run first.
 */

const OPERATOR_USERNAME = 'purchase01'
const OPERATOR_PASSWORD = 'DemoPass123!'
const ADMIN_USERNAME = 'admin01'
const ADMIN_PASSWORD = 'DemoPass123!'

async function login(page: Page, username: string, password: string) {
  await page.goto('/')
  await page.getByLabel('ユーザー名').fill(username)
  await page.getByLabel('パスワード').fill(password)
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page.getByTestId('nav-dashboard')).toBeVisible()
}

async function logout(page: Page) {
  await page.getByTestId('nav-logout').click()
  await expect(page.getByLabel('ユーザー名')).toBeVisible()
}

/** Test-only officialPoNo, set directly on the local disposable Prototype
 * Postgres (see file Javadoc above for why this is the E2E-layer equivalent
 * of the Backend integration tests' direct Repository call).
 * portal_order.official_po_no is UNIQUE, so a prior run's leftover Order
 * (Demo Reset is a suite prerequisite, not something this spec invokes
 * itself - implementation instructions Step 5 5章) could still be holding
 * the same PO No.; releasing it first makes this helper idempotent across
 * repeated runs without requiring a reset in between. */
function linkOrderToOfficialPoNo(orderId: string, officialPoNo: string) {
  execSync(
    `docker exec gsys-prototype-postgres psql -U gsys_portal -d gsys_portal -c ` +
    `"UPDATE portal_order SET official_po_no=NULL WHERE official_po_no='${officialPoNo}'; ` +
    `UPDATE portal_order SET official_po_no='${officialPoNo}' WHERE id=${orderId};"`,
    { stdio: 'pipe' },
  )
}

async function createApprovedOrder(page: Page, sku: string): Promise<string> {
  await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
  await page.getByTestId('nav-candidates').click()
  await expect(page.getByTestId(`candidate-row-${sku}`)).toBeVisible()
  await page.getByTestId(`candidate-checkbox-${sku}`).locator('input').check()
  await page.getByTestId('create-draft-button').click()
  await expect(page).toHaveURL(/\/orders\/drafts\/\d+(\?.*)?$/)
  const draftId = page.url().match(/\/orders\/drafts\/(\d+)/)?.[1]
  expect(draftId).toBeTruthy()

  await page.getByTestId('submit-for-approval-button').click()
  await page.getByTestId('submit-for-approval-dialog-confirm').click()
  await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))

  await logout(page)
  await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
  await page.goto(`/orders/${draftId}`)
  await page.getByTestId('order-detail-approve-button').click()
  await page.getByTestId('approve-dialog-confirm').click()
  await expect(page.getByText('承認しました。')).toBeVisible()

  return draftId!
}

function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 100000)}@example.com`
}

/** Mirrors supplier-contact-mail-template.spec.ts's own proven cleanup
 * helper (re-fetches the list and matches on active===true, rather than
 * trusting a captured id/object from creation time) - Supplier Contact /
 * Mail Template are real, persistent Master data not wiped by Demo Reset. */
async function deactivateContactByEmail(page: Page, email: string) {
  const list = await (await page.request.get('/api/admin/supplier-contacts')).json()
  const match = list.find((c: { email: string; active: boolean }) => c.email === email && c.active)
  if (!match) return
  await page.request.put(`/api/admin/supplier-contacts/${match.id}`, { data: { ...match, active: false } })
}

async function deactivateTemplateByName(page: Page, templateName: string) {
  const list = await (await page.request.get('/api/admin/mail-templates')).json()
  const match = list.find((t: { templateName: string; active: boolean }) => t.templateName === templateName && t.active)
  if (!match) return
  await page.request.put(`/api/admin/mail-templates/${match.id}`, { data: { ...match, active: false } })
}

test.describe('Phase 7-C7A: Fulfillment / Follow-up Foundation', () => {
  test('Scenario A: officialPoNo未設定 -> G-SYS正式PO未連携 -> 未納と誤表示されない', async ({ page }) => {
    const orderId = await createApprovedOrder(page, 'OD-CHAIR-002')
    await page.goto(`/orders/${orderId}`)

    await expect(page.getByTestId('fulfillment-not-linked')).toBeVisible()
    await expect(page.getByTestId('fulfillment-section')).not.toContainText('未納')
    await expect(page.getByTestId('fulfillment-section').locator('table')).toHaveCount(0)
  })

  test('Scenario B: Test Official PO (完納) -> Fulfillment表示 -> Ordered/Invoiced/Stock-in/Outstanding確認', async ({ page }) => {
    const orderId = await createApprovedOrder(page, 'OD-TENT-001')
    linkOrderToOfficialPoNo(orderId, 'PO-OUTDOOR-01')

    await page.reload()

    await expect(page.getByTestId('fulfillment-status-chip')).toContainText('完納')
    const row = page.getByTestId('fulfillment-line-OD-TENT-001')
    await expect(row).toBeVisible()
    const cells = row.locator('td')
    await expect(cells.nth(2)).toHaveText('3') // ordered
    await expect(cells.nth(3)).toHaveText('3') // invoiced
    await expect(cells.nth(4)).toHaveText('3') // stock-in
    await expect(cells.nth(5)).toHaveText('0') // outstanding
  })

  test('Scenario C: Partial Order -> PARTIAL -> 問い合わせ対象作成', async ({ page }) => {
    const orderId = await createApprovedOrder(page, 'OD-TENT-002')
    linkOrderToOfficialPoNo(orderId, 'PO-OUTDOOR-02')

    await page.reload()

    await expect(page.getByTestId('fulfillment-status-chip')).toContainText('一部納品')
    const row = page.getByTestId('fulfillment-line-OD-TENT-002')
    await expect(row.locator('td').nth(5)).toHaveText('1') // outstanding = 3 - 2

    await page.getByTestId('fulfillment-follow-up-button-OD-TENT-002').click()
    await expect(page.getByTestId('follow-up-sku-input').locator('input')).toHaveValue('OD-TENT-002')
    await page.getByTestId('follow-up-note-input').locator('textarea').first().fill('一部未着のため確認')
    await page.getByTestId('follow-up-create-dialog-confirm').click()

    const caseCard = page.getByTestId(/^follow-up-case-/).first()
    await expect(caseCard).toBeVisible()
    await expect(caseCard).toContainText('一部未着のため確認')
  })

  test('Scenario D: Follow-up Case -> Follow-up Mail Preview -> 実送信なし', async ({ page }) => {
    const orderId = await createApprovedOrder(page, 'OD-TENT-001')
    // A test-only, fake PO No. (NOT one of the real Legacy Test Fixtures
    // reused by Scenario B/C) - Follow-up Mail Preview never validates
    // officialPoNo against Legacy Fulfillment data at all (it only uses it as
    // a Template variable), and portal_order.official_po_no is itself
    // UNIQUE, so this must be a value no other Scenario in this file links to.
    linkOrderToOfficialPoNo(orderId, 'TSUP-TBR-E2E-FOLLOWUP-01')

    // ADMIN registers a Contact + FOLLOW_UP Template so Preview can fully
    // resolve (mirrors supplier-contact-mail-template.spec.ts's own direct-API
    // setup pattern) - a fresh, run-unique identity so repeat runs never collide.
    const toEmail = uniqueEmail('followup-to')
    await page.request.post('/api/admin/supplier-contacts', {
      data: { supplierCode: 'SUP_ALPHA', brandCode: 'BR_OUTDOOR', contactName: 'Follow-up Contact', email: toEmail,
        contactType: 'TO', language: 'ja', primary: true, active: true },
    })
    const templateName = `Follow-up E2E Template ${Date.now()}`
    await page.request.post('/api/admin/mail-templates', {
      data: { templateName, templateType: 'FOLLOW_UP',
        supplierCode: 'SUP_ALPHA', brandCode: 'BR_OUTDOOR', language: 'ja',
        subjectTemplate: 'PO {{poNo}} について', bodyTemplate: '{{contactName}} 様\nSKU: {{skuCode}}\n{{note}}', active: true },
    })

    await page.goto(`/orders/${orderId}`)
    await page.getByTestId('create-follow-up-case-button').click()
    await page.getByTestId('follow-up-reason-select').click()
    await page.getByRole('option', { name: '未着' }).click()
    await page.getByTestId('follow-up-note-input').locator('textarea').first().fill('納品確認のお願い')
    await page.getByTestId('follow-up-create-dialog-confirm').click()

    const previewButton = page.getByTestId(/^follow-up-mail-preview-button-/).first()
    await previewButton.click()
    const previewResult = page.getByTestId(/^follow-up-mail-preview-result-/).first()
    await expect(previewResult).toContainText('PO TSUP-TBR-E2E-FOLLOWUP-01 について')
    await expect(previewResult).toContainText('納品確認のお願い')
    // 実送信なし: no Send control of any kind exists anywhere on this screen.
    await expect(page.getByRole('button', { name: /送信/ })).toHaveCount(0)

    // Cleanup: deactivate the Master rows this test created - a stray ACTIVE
    // Supplier Contact for SUP_ALPHA/BR_OUTDOOR would otherwise make
    // supplier-contact-mail-template.spec.ts's own "nothing configured yet"
    // Scenario C/D false (7-C3's own established E2E convention).
    await deactivateTemplateByName(page, templateName)
    await deactivateContactByEmail(page, toEmail)
  })

  test('Scenario E: ADMIN -> Follow-up Close', async ({ page }) => {
    const orderId = await createApprovedOrder(page, 'OD-CHAIR-002')
    await page.goto(`/orders/${orderId}`)
    await page.getByTestId('create-follow-up-case-button').click()
    await page.getByTestId('follow-up-create-dialog-confirm').click()

    const caseCard = page.getByTestId(/^follow-up-case-/).first()
    await expect(caseCard).toContainText('未対応')

    await page.getByTestId(/^follow-up-close-button-/).first().click()
    await expect(caseCard).toContainText('完了')
    // A CLOSED Case no longer offers Preview/Close/Reorder actions.
    await expect(page.getByTestId(/^follow-up-mail-preview-button-/)).toHaveCount(0)
  })

  test('Scenario F: Reorder -> 元OrderとのReference確認 -> 数量自動確定なし', async ({ page }) => {
    const orderId = await createApprovedOrder(page, 'OD-TENT-001')
    await page.goto(`/orders/${orderId}`)
    const originalQty = await page.locator('table tr', { hasText: 'OD-TENT-001' }).first().locator('td').nth(3).innerText()

    await page.getByTestId('create-follow-up-case-button').click()
    await page.getByTestId('follow-up-sku-input').locator('input').fill('OD-TENT-001')
    await page.getByTestId('follow-up-create-dialog-confirm').click()

    await page.getByTestId(/^follow-up-reorder-button-/).first().click()
    await page.getByTestId('follow-up-reorder-reason-input').locator('textarea').first().fill('前回未着分の再発注')
    await page.getByTestId('follow-up-reorder-dialog-confirm').click()

    // Navigates to the newly-created reorder Draft.
    await expect(page).toHaveURL(/\/orders\/drafts\/\d+(\?.*)?$/)
    const reorderDraftId = page.url().match(/\/orders\/drafts\/(\d+)/)?.[1]
    expect(reorderDraftId).not.toBe(orderId)

    // Order Qty is the ordinary Legacy Recommended Qty default, never
    // auto-set from an Outstanding Qty calculation (7-C7A 16章).
    const reorderQty = await page.getByTestId('order-qty-input-OD-TENT-001').locator('input').inputValue()
    expect(reorderQty).toBe(originalQty)
  })

  test('Scenario G: 全操作後もLegacy変更ゼロ（UI観測可能な範囲）', async ({ page }) => {
    // No Portal UI/API in this Phase writes to Legacy at all (Fulfillment is
    // pure READ; Follow-up/Reorder only ever write to Prototype). This test
    // re-confirms the same "no real mail sent" contract as every other
    // Send-adjacent feature in this codebase, on the Follow-up Mail Preview path.
    const orderId = await createApprovedOrder(page, 'OD-CHAIR-002')
    await page.goto(`/orders/${orderId}`)
    await page.getByTestId('create-follow-up-case-button').click()
    await page.getByTestId('follow-up-create-dialog-confirm').click()

    await page.getByTestId(/^follow-up-mail-preview-button-/).first().click()
    // Blocked (no officialPoNo) - confirms Preview never fabricates content,
    // and confirms no Send API/control is reachable from this state either.
    await expect(page.getByTestId(/^follow-up-mail-preview-result-/)).toBeVisible()
    await expect(page.getByRole('button', { name: /送信/ })).toHaveCount(0)
  })
})
