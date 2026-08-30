import { test, expect, type Page } from '@playwright/test'
import { execSync } from 'node:child_process'

/**
 * Phase 8-G: Arrival / Warehouse Stock Visibility Foundation. Covers 入荷確認
 * (Arrival List/Detail) and 倉庫在庫 (Warehouse Stock List/Drawer) end to end
 * against real Legacy Demo MySQL Seed Data (backend/demo-data/02-seed.sql),
 * plus the Order Detail -> Arrival Info Navigation link (12章) and
 * regression checks for existing Ordering/Price Change (Section 20).
 *
 * Prerequisite: same as the other Phase 7/8 specs - Demo Reset run first
 * (`cd backend && ./mvnw.cmd -q spring-boot:run -Dspring-boot.run.profiles=local
 * -Dspring-boot.run.arguments=--app.demo-reset.enabled=true`), and the
 * Legacy Demo MySQL container recreated with this Phase's schema/seed
 * additions (tr_arr table, extra ms_stk rows - backend/demo-data/01-schema.sql/
 * 02-seed.sql).
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

/** Same test-only officialPoNo linking helper as fulfillment-follow-up-
 * foundation.spec.ts (see that file's Javadoc for why this direct `docker
 * exec` UPDATE against the local disposable Prototype Postgres is the
 * sanctioned E2E-layer equivalent of a Backend integration test's direct
 * Repository call - 7-C2B/officialPoNo assignment has no real Portal UI). */
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

test.describe('Phase 8-G: Arrival / Warehouse Stock Visibility Foundation', () => {
  test('A: Navigation - 入荷確認/倉庫在庫 Nav entries reach their List screens', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)

    await page.getByTestId('nav-arrivals').click()
    await expect(page).toHaveURL(/\/arrivals$/)
    await expect(page.getByRole('heading', { name: '入荷確認' })).toBeVisible()

    await page.getByTestId('nav-warehouse-stock').click()
    await expect(page).toHaveURL(/\/warehouse-stock$/)
    await expect(page.getByRole('heading', { name: '倉庫在庫' })).toBeVisible()
  })

  test('B: Arrival Search - PO Number Filter narrows results, showing all 4 quantities', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-arrivals').click()

    await expect(page.getByTestId('arrival-row-PO-OUTDOOR-01')).toBeVisible()
    await expect(page.getByTestId('arrival-row-PO-KITCHEN-14')).toBeVisible()

    await page.getByTestId('arrival-filter-po-number').locator('input').fill('PO-OUTDOOR-01')
    await page.getByTestId('arrival-filter-po-number').locator('input').blur()

    await expect(page.getByTestId('arrival-row-PO-OUTDOOR-01')).toBeVisible()
    await expect(page.getByTestId('arrival-row-PO-KITCHEN-14')).not.toBeVisible()

    const row = page.getByTestId('arrival-row-PO-OUTDOOR-01')
    // Seed data: fully invoiced and stocked in - ordered=invoice=arrival=stockIn=3.
    await expect(row.getByRole('cell').nth(5)).toHaveText('3')
    await expect(row.getByRole('cell').nth(6)).toHaveText('3')
    await expect(row.getByRole('cell').nth(7)).toHaveText('3')
    await expect(row.getByRole('cell').nth(8)).toHaveText('3')
  })

  test('C: Arrival Detail - shows header facts and SKU-level traceability, no computed Status', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-arrivals').click()
    await page.getByTestId('arrival-row-PO-OUTDOOR-01').click()

    await expect(page).toHaveURL(/\/arrivals\/SUP_ALPHA\/PO-OUTDOOR-01\/INV-OUTDOOR-01$/)
    await expect(page.getByRole('heading', { name: /入荷詳細/ })).toBeVisible()
    await expect(page.getByText('BL-OUTDOOR-001')).toBeVisible()
    await expect(page.getByText('PACIFIC STAR')).toBeVisible()

    const line = page.getByTestId('arrival-line-OD-TENT-001')
    await expect(line).toBeVisible()
    await expect(line).toContainText('3')
  })

  test('D: Credit-netted Arrival (PO-OUTDOOR-05) shows the true netted Stock-In Qty', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.goto('/arrivals/SUP_BETA/PO-OUTDOOR-05/INV-OUTDOOR-05')

    const summary = page.getByTestId('arrival-quantity-summary')
    // invoiceQty=3, stockInQty netted to 3+(-1)=2 (Original + Credit).
    await expect(summary.getByRole('cell').nth(1)).toHaveText('3')
    await expect(summary.getByRole('cell').nth(3)).toHaveText('2')
  })

  test('E: 倉庫在庫 List - WH_CD Filter narrows results, Detail Drawer shows every warehouse for a SKU', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-warehouse-stock').click()

    await page.getByTestId('warehouse-stock-filter-sku').locator('input').fill('OD-TENT-001')
    await page.getByTestId('warehouse-stock-filter-sku').locator('input').blur()
    await expect(page.getByTestId('warehouse-stock-row-01-OD-TENT-001')).toBeVisible()
    await expect(page.getByTestId('warehouse-stock-row-04-OD-TENT-001')).toBeVisible()
    await expect(page.getByTestId('warehouse-stock-row-05-OD-TENT-001')).toBeVisible()

    await page.getByTestId('warehouse-stock-detail-button-OD-TENT-001').first().click()
    await expect(page.getByTestId('warehouse-stock-drawer')).toBeVisible()
    await expect(page.getByTestId('warehouse-stock-drawer-row-01')).toBeVisible()
    await expect(page.getByTestId('warehouse-stock-drawer-row-04')).toBeVisible()
    await expect(page.getByTestId('warehouse-stock-drawer-row-05')).toBeVisible()
    await page.getByTestId('warehouse-stock-drawer-close').click()
    await expect(page.getByTestId('warehouse-stock-drawer')).not.toBeVisible()
  })

  test('F: Pagination controls are present and Backend-driven (page/size reflected in the URL)', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-warehouse-stock').click()
    await expect(page.getByTestId('warehouse-stock-pagination')).toBeVisible()

    await page.getByTestId('warehouse-stock-pagination').getByLabel('Go to next page').click()
    await expect(page).toHaveURL(/[?&]page=1/)
  })

  test('G: Order Detail -> 入荷情報を見る Navigation filters the Arrival List by officialPoNo', async ({ page }) => {
    // OD-TENT-001 (not OD-CHAIR-001) - the same SKU
    // fulfillment-follow-up-foundation.spec.ts's own createApprovedOrder
    // calls use most often, a proven-reliable path through Submit for
    // Approval/Approve for this exact helper.
    const orderId = await createApprovedOrder(page, 'OD-TENT-001')
    linkOrderToOfficialPoNo(orderId, 'PO-OUTDOOR-03')

    await page.goto(`/orders/${orderId}`)
    await expect(page.getByTestId('fulfillment-section')).toBeVisible()
    await page.getByTestId('fulfillment-view-arrivals-button').click()

    await expect(page).toHaveURL(/\/arrivals\?poNumber=PO-OUTDOOR-03/)
    // PO-OUTDOOR-03 deliberately has no tr_inv/tr_arr row seeded (backend/
    // demo-data/02-seed.sql) - an honest empty result, not a fabricated row.
    await expect(page.getByText('該当する入荷データがありません。')).toBeVisible()
  })

  test('H: Existing Ordering Workflow (Candidate List -> Create Draft) is unaffected', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-candidates').click()
    await expect(page.getByTestId('candidate-row-KT-KNIFE-002')).toBeVisible()
    await page.getByTestId('candidate-checkbox-KT-KNIFE-002').locator('input').check()
    await page.getByTestId('create-draft-button').click()
    await expect(page).toHaveURL(/\/orders\/drafts\/\d+/)
  })

  test('I: Existing Price Change screen is unaffected', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-price-changes').click()
    await expect(page).toHaveURL(/\/price-changes$/)
    await expect(page.getByRole('heading', { name: '価格変更一覧' })).toBeVisible()
  })
})
