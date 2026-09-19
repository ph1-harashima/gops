import { test, expect, type Page } from '@playwright/test'
import { execSync } from 'node:child_process'

/**
 * Phase 7-C6 26章 Browser Scenario A-G: Excel / Legacy Concurrency Control
 * Foundation. Same login/logout Role-switch pattern and selector policy
 * (data-testid / getByRole / getByLabel only) as the other 7-C* specs.
 *
 * officialPoNo is never assignable through any real Portal UI this Phase
 * (7-C2B is not implemented) - every Scenario that needs one uses the SAME
 * test-only direct SQL UPDATE against the local, disposable Prototype
 * Postgres already established by fulfillment-follow-up-foundation.spec.ts.
 *
 * Scenarios C/D additionally need to mutate the Legacy Demo MySQL fixture
 * itself (PO-CONC-01/02/03, backend/demo-data/02-seed.sql) - 7-C6 14章's
 * explicitly sanctioned Test-only Legacy Demo DB mutation via
 * `docker exec ... mysql ...`, never through the Portal application's own
 * READ ONLY connection. Every mutation is preceded by an idempotent RESET
 * back to the seeded shape, so this spec never depends on a full Legacy Demo
 * MySQL container recreation between repeated runs.
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

function linkOrderToOfficialPoNo(orderId: string, officialPoNo: string) {
  execSync(
    `docker exec gsys-prototype-postgres psql -U gsys_portal -d gsys_portal -c ` +
    `"UPDATE portal_order SET official_po_no=NULL WHERE official_po_no='${officialPoNo}'; ` +
    `UPDATE portal_order SET official_po_no='${officialPoNo}' WHERE id=${orderId};"`,
    { stdio: 'pipe' },
  )
}

function execLegacyMysql(sql: string) {
  execSync(
    `docker exec gsys-legacy-demo-mysql mysql -uroot -proot_demo_pw -h127.0.0.1 legacy_demo -e "${sql}"`,
    { stdio: 'pipe' },
  )
}

/** Idempotent across repeated runs (7-C6 14章): restores PO-CONC-01/02/03 to
 * their backend/demo-data/02-seed.sql seeded shape before each Scenario
 * mutates them further. */
function resetConcurrencyFixtures() {
  execLegacyMysql("UPDATE tr_po_dtl SET qty_po=5 WHERE po_no='PO-CONC-01' AND item_cd='CC-ITEM-001'")
  execLegacyMysql("DELETE FROM tr_po_dtl WHERE po_no='PO-CONC-02' AND line_no=2")
  execLegacyMysql("UPDATE tr_po_dtl SET del_flg=0 WHERE po_no='PO-CONC-03' AND item_cd='CC-ITEM-005'")
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

/** ADMIN "G-SYS連携準備" (must precede Baseline Capture, 7-C6 9章's
 * precondition). Assumes the ADMIN session from createApprovedOrder is
 * already active and on the Order Detail page. */
async function prepareIntegration(page: Page, orderId: string) {
  await page.goto(`/orders/${orderId}`)
  await page.getByTestId('official-po-request-button').click()
  await page.getByTestId('official-po-request-dialog-confirm').click()
  await expect(page.getByTestId('official-po-no')).toBeVisible()
}

test.describe('Phase 7-C6: Excel / Legacy Concurrency Control Foundation', () => {
  test('Scenario A: officialPoNoなし -> 比較不可表示', async ({ page }) => {
    const orderId = await createApprovedOrder(page, 'OD-CHAIR-002')
    await prepareIntegration(page, orderId)

    await expect(page.getByTestId('concurrency-not-linked')).toBeVisible()
    await expect(page.getByTestId('legacy-po-baseline-capture-button')).toBeVisible()
  })

  test('Scenario B: Test Official PO -> Baseline Capture -> Compare -> UNCHANGED', async ({ page }) => {
    resetConcurrencyFixtures()
    const orderId = await createApprovedOrder(page, 'OD-TENT-001')
    await prepareIntegration(page, orderId)
    linkOrderToOfficialPoNo(orderId, 'PO-CONC-01')
    await page.reload()

    await expect(page.getByTestId('concurrency-not-baselined')).toBeVisible()

    await page.getByTestId('legacy-po-baseline-capture-button').click()
    await expect(page.getByText('現在のG-SYS状態を基準として記録しました。')).toBeVisible()
    await expect(page.getByTestId('concurrency-unchanged')).toBeVisible()

    await page.reload();
    await expect(page.getByTestId('concurrency-unchanged')).toBeVisible()
  })

  test('Scenario C: Legacy Demo Fixture Qty変更 -> Compare -> CHANGED -> Structured Diff表示', async ({ page }) => {
    resetConcurrencyFixtures()
    const orderId = await createApprovedOrder(page, 'OD-TENT-001')
    await prepareIntegration(page, orderId)
    linkOrderToOfficialPoNo(orderId, 'PO-CONC-01')
    await page.reload()
    await page.getByTestId('legacy-po-baseline-capture-button').click()
    await expect(page.getByTestId('concurrency-unchanged')).toBeVisible()

    execLegacyMysql("UPDATE tr_po_dtl SET qty_po=8 WHERE po_no='PO-CONC-01' AND item_cd='CC-ITEM-001'")
    await page.reload()

    await expect(page.getByTestId('concurrency-changed')).toBeVisible()
    const diffRows = page.getByTestId('legacy-po-concurrency-section')
    await expect(diffRows).toContainText('orderedQty')
    await expect(diffRows).toContainText('CC-ITEM-001')
    await expect(diffRows).toContainText('5')
    await expect(diffRows).toContainText('8')
    await expect(diffRows).toContainText('明細変更')
  })

  test('Scenario D-1: Line追加 -> Diff表示', async ({ page }) => {
    resetConcurrencyFixtures()
    const orderId = await createApprovedOrder(page, 'OD-BAG-001')
    await prepareIntegration(page, orderId)
    linkOrderToOfficialPoNo(orderId, 'PO-CONC-02')
    await page.reload()
    await page.getByTestId('legacy-po-baseline-capture-button').click()
    await expect(page.getByTestId('concurrency-unchanged')).toBeVisible()

    execLegacyMysql(
      "INSERT INTO tr_po_dtl (po_no, line_no, item_cd, qty_po, prc_unit, amt_line, del_flg, create_datetime, update_datetime) " +
      "VALUES ('PO-CONC-02',2,'CC-ITEM-001',1,999.00000,999.00000,b'0',NOW(),NOW())",
    )
    await page.reload()

    await expect(page.getByTestId('concurrency-changed')).toBeVisible()
    const diffs = page.getByTestId('legacy-po-concurrency-section')
    await expect(diffs).toContainText('明細追加')
    await expect(diffs).toContainText('CC-ITEM-001')
  })

  test('Scenario D-2: Line削除 -> Diff表示', async ({ page }) => {
    resetConcurrencyFixtures()
    const orderId = await createApprovedOrder(page, 'OD-BAG-002')
    await prepareIntegration(page, orderId)
    linkOrderToOfficialPoNo(orderId, 'PO-CONC-03')
    await page.reload()
    await page.getByTestId('legacy-po-baseline-capture-button').click()
    await expect(page.getByTestId('concurrency-unchanged')).toBeVisible()

    execLegacyMysql("UPDATE tr_po_dtl SET del_flg=1 WHERE po_no='PO-CONC-03' AND item_cd='CC-ITEM-005'")
    await page.reload()

    await expect(page.getByTestId('concurrency-changed')).toBeVisible()
    const diffs = page.getByTestId('legacy-po-concurrency-section')
    await expect(diffs).toContainText('明細削除')
    await expect(diffs).toContainText('CC-ITEM-005')
  })

  test('Scenario E: Rev1/Rev2 Baseline混同なし', async ({ page }) => {
    resetConcurrencyFixtures()
    const orderId = await createApprovedOrder(page, 'OD-TENT-001')
    await prepareIntegration(page, orderId)

    // Revision 1 must actually be issued (GENERATED) before it can later be
    // Reissued - same precondition Scenario K in official-po-integration.spec.ts
    // relies on. BR-08: Official PO No. is auto-numbered by prepareIntegration
    // itself now - no manual input.
    await page.getByTestId('official-po-generate-button').click()
    await expect(page.getByText('正式PO Excelを生成しました。')).toBeVisible()

    linkOrderToOfficialPoNo(orderId, 'PO-CONC-01')
    await page.reload()
    await page.getByTestId('legacy-po-baseline-capture-button').click()
    await expect(page.getByTestId('concurrency-unchanged')).toBeVisible()

    // Revision Consistency Audit: a plain Demo Send with no correction never
    // crystallizes a new Official PO Document Revision by itself - Revision
    // 1's own Baseline must remain valid straight through it.
    await page.getByTestId('order-detail-primary-action').click();
    await expect(page).toHaveURL(/\/orders\/drafts\/\d+\/preview(\?.*)?$/)
    await page.getByTestId('demo-send-button').click()
    await page.getByTestId('demo-send-dialog-confirm').click()
    await expect(page).toHaveURL(new RegExp(`/orders/${orderId}(\\?.*)?$`))

    await page.reload()
    await expect(page.getByTestId('concurrency-unchanged')).toBeVisible()

    // Only a genuine correction + Reissue crystallizes Revision 2 - and
    // Revision 1's Baseline must never be silently reused for it.
    const enterSupplierResponse = page.getByTestId('order-detail-primary-action')
    await enterSupplierResponse.click()
    const orderedQtyCell = page.getByTestId('response-row-OD-TENT-001').locator('td').nth(2)
    const orderedQty = Number(await orderedQtyCell.innerText())
    await page.getByTestId('confirmed-qty-input-OD-TENT-001').locator('input').fill(String(Math.max(0, orderedQty - 1)))
    await page.getByTestId('save-response-button').click()
    await expect(page.getByText('回答を保存しました。')).toBeVisible()
    await page.getByTestId('confirm-response-button').click()
    await page.getByTestId('confirm-response-dialog-confirm').click()
    await expect(page).toHaveURL(new RegExp(`/orders/${orderId}(\\?.*)?$`))

    await page.getByTestId('order-detail-primary-action').click()
    await expect(page).toHaveURL(new RegExp(`/orders/${orderId}/supplier-response(\\?.*)?$`))
    await page.getByTestId('create-revision-button').click()
    await page.getByTestId('revision-reason-input').locator('textarea').first().fill('メーカー在庫の都合により数量を修正')
    await page.getByTestId('apply-confirmed-values-checkbox').check()
    await page.getByTestId('revision-dialog-confirm').click()
    await expect(page).toHaveURL(new RegExp(`/orders/drafts/${orderId}(\\?.*)?$`))
    await page.getByTestId('submit-for-approval-button').click()
    await page.getByTestId('submit-for-approval-dialog-confirm').click()
    await page.getByTestId('order-detail-approve-button').click()
    await page.getByTestId('approve-dialog-confirm').click()

    await expect(page.getByTestId('official-po-reissue-button')).toBeEnabled()
    await page.getByTestId('official-po-reissue-button').click()
    await page.getByTestId('official-po-reissue-dialog-confirm').click()
    await expect(page.getByText('正式POを再発行しました。')).toBeVisible()

    await page.reload()
    await expect(page.getByTestId('concurrency-not-baselined')).toBeVisible()
  })

  test('Scenario F: OPERATOR Baseline API -> 403', async ({ page }) => {
    resetConcurrencyFixtures()
    const orderId = await createApprovedOrder(page, 'OD-TENT-001')
    await prepareIntegration(page, orderId)
    await logout(page)

    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    // OPERATOR never even sees the Capture button in the UI (isAdmin gate).
    await page.goto(`/orders/${orderId}`)
    await expect(page.getByTestId('legacy-po-baseline-capture-button')).toHaveCount(0)

    const response = await page.request.post(`/api/orders/${orderId}/official-po/baseline`)
    expect(response.status()).toBe(403)
  })

  test('Scenario G: 全操作後、実Legacyの書込み経路が存在しないことを確認', async ({ page }) => {
    resetConcurrencyFixtures()
    const orderId = await createApprovedOrder(page, 'OD-TENT-001')
    await prepareIntegration(page, orderId)
    linkOrderToOfficialPoNo(orderId, 'PO-CONC-01')
    await page.reload()

    // Label wording never implies a Legacy WRITE (7-C6 20章's explicit ban on
    // "同期"/"上書き"/"更新"-style labels).
    await expect(page.getByRole('button', { name: /同期|上書き|更新/ })).toHaveCount(0)

    await page.getByTestId('legacy-po-baseline-capture-button').click()
    await expect(page.getByTestId('concurrency-unchanged')).toBeVisible()

    // Repeated Compare (page reload = fresh GET) is idempotent - re-fetching
    // never itself mutates anything, Legacy or Prototype.
    await page.reload()
    await expect(page.getByTestId('concurrency-unchanged')).toBeVisible()
    await page.reload()
    await expect(page.getByTestId('concurrency-unchanged')).toBeVisible()
  })
})
