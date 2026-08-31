import { test, expect, type Page } from '@playwright/test'
import { execSync } from 'node:child_process'

/**
 * Phase 8-M: Global Navigation / Back Operation Audit & Implementation.
 *
 * Targeted coverage for the Navigation Context work done in this Phase -
 * Price Change / Arrival / Warehouse Stock / Stock-Sales screens now reuse
 * the existing `returnTo`/`backTo` mechanism (Phase 6-A/7-H,
 * shared/navigation/returnTo.ts) the same way Candidate List / Order
 * History already did (see list-state-preservation.spec.ts for that
 * existing coverage - this file does not repeat it).
 *
 * This suite specifically proves:
 *  - a List's own Filter state survives a round trip through its Detail/Edit
 *    screen (§6 scenarios 6/7/16/17/18)
 *  - a List reached via an incoming returnTo (SKU Detail / Order Detail /
 *    a sibling Drawer) shows its own conditional "戻る" and returns to the
 *    exact originating screen (§6 scenarios 9/10/11/12/13/14)
 *  - a List reached directly (Nav Bar) shows no such button - Direct Access
 *    Fallback per Principle E (§6 scenario 15)
 */

const OPERATOR_USERNAME = 'purchase01'
const OPERATOR_PASSWORD = 'DemoPass123!'
const ADMIN_USERNAME = 'admin01'
const ADMIN_PASSWORD = 'DemoPass123!'

async function login(page: Page, username = OPERATOR_USERNAME, password = OPERATOR_PASSWORD) {
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

/** Same test-only officialPoNo linking helper as
 * arrival-warehouse-stock-visibility-foundation.spec.ts (see that file's
 * Javadoc for why this direct `docker exec` UPDATE against the local
 * disposable Prototype Postgres is the sanctioned E2E-layer equivalent of a
 * Backend integration test's direct Repository call). */
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

test.describe('Phase 8-M: Global Navigation / Back Operation', () => {
  test('Scenario 6/7: Price Change List Status Filter survives a Detail round trip', async ({ page }) => {
    await login(page)
    await page.getByTestId('nav-price-changes').click()
    await expect(page).toHaveURL(/\/price-changes$/)

    // Create one Change Set so there is a non-DRAFT-free List to filter on -
    // status defaults to DRAFT, which is exactly the Filter this Scenario drives.
    await page.getByTestId('create-price-change-button').click()
    await expect(page).toHaveURL(/\/price-changes\/\d+\/edit(\?.*)?$/)
    await page.getByTestId('back-to-price-change-origin').click()
    await expect(page).toHaveURL(/\/price-changes$/)

    await page.getByTestId('price-change-status-filter').click()
    await page.getByRole('option', { name: '下書き' }).click()
    await expect(page).toHaveURL(/status=DRAFT/)
    const listUrl = page.url()

    const row = page.locator('table tbody tr').first()
    await expect(row).toBeVisible()
    await row.click()
    // DRAFT rows route to Edit; the Edit screen's own Back returns to this
    // exact filtered List URL (Principle A/B), not a fixed unfiltered one.
    await expect(page).toHaveURL(/\/price-changes\/\d+\/edit\?.*returnTo=/)

    await page.getByTestId('back-to-price-change-origin').click()
    await expect(page).toHaveURL(listUrl)
    await expect(page.getByTestId('price-change-status-filter')).toContainText('下書き')
  })

  test('Scenario 6/8: Price Change Detail (read-only) round trip, then Edit round trip back to Detail', async ({ page }) => {
    await login(page)
    await page.getByTestId('nav-price-changes').click()
    await page.getByTestId('create-price-change-button').click()
    await expect(page).toHaveURL(/\/price-changes\/\d+\/edit/)
    const url = page.url()
    const id = url.match(/\/price-changes\/(\d+)\/edit/)?.[1]
    expect(id).toBeTruthy()

    // Detail is reached by Direct Access here (no returnTo) - its own Back
    // Fallback must still be the plain List (Principle E).
    await page.goto(`/price-changes/${id}`)
    await expect(page.getByTestId('back-to-price-change-list')).toBeVisible()

    // Detail -> Edit -> Back returns to Detail specifically (backTo), not
    // the List - same two-tier chain PoPreviewPage established.
    await page.getByTestId('edit-price-change-button').click()
    await expect(page).toHaveURL(new RegExp(`/price-changes/${id}/edit`))
    const backButton = page.getByTestId('back-to-price-change-origin')
    await expect(backButton).toHaveText('詳細へ戻る')
    await backButton.click()
    await expect(page).toHaveURL(new RegExp(`/price-changes/${id}$`))
  })

  test('Scenario 13/16/17: Arrival List Filter survives a Detail round trip', async ({ page }) => {
    await login(page)
    await page.getByTestId('nav-arrivals').click()
    await expect(page).toHaveURL(/\/arrivals$/)
    // Direct Access (Nav Bar) - no incoming returnTo, so no conditional Back button.
    await expect(page.getByTestId('back-to-arrival-origin')).toHaveCount(0)

    await page.getByTestId('arrival-filter-po-number').locator('input').fill('PO-OUTDOOR-01')
    await page.getByTestId('arrival-filter-po-number').locator('input').blur()
    await expect(page).toHaveURL(/poNumber=PO-OUTDOOR-01/)
    const listUrl = page.url()

    await page.getByTestId('arrival-row-PO-OUTDOOR-01').click()
    await expect(page).toHaveURL(/\/arrivals\/SUP_ALPHA\/PO-OUTDOOR-01\/INV-OUTDOOR-01\?returnTo=/)

    await page.getByTestId('back-to-arrival-list').click()
    await expect(page).toHaveURL(listUrl)
    await expect(page.getByTestId('arrival-filter-po-number').locator('input')).toHaveValue('PO-OUTDOOR-01')
  })

  test('Scenario 9/10: SKU Detail -> Arrival List -> Back returns to SKU Detail (Principle D)', async ({ page }) => {
    await login(page)
    await page.getByTestId('nav-candidates').click()
    await expect(page.getByTestId('candidate-row-OD-TENT-001')).toBeVisible()
    await page.getByRole('button', { name: 'OD-TENT-001' }).click()
    await expect(page).toHaveURL(/\/items\/OD-TENT-001/)
    const skuDetailUrl = page.url()

    await page.getByTestId('sku-detail-view-arrivals-button').click()
    await expect(page).toHaveURL(/\/arrivals\?skuKeyword=OD-TENT-001&returnTo=/)

    // Reached via returnTo from SKU Detail - Arrival List must show its own
    // conditional Back button (absent on Direct Access, see the prior test).
    const backToOrigin = page.getByTestId('back-to-arrival-origin')
    await expect(backToOrigin).toBeVisible()
    await backToOrigin.click()
    await expect(page).toHaveURL(skuDetailUrl)
  })

  test('Scenario 11/12: Warehouse Stock Drawer -> Stock-Sales -> Back returns to Warehouse Stock (List)', async ({ page }) => {
    await login(page)
    await page.getByTestId('nav-warehouse-stock').click()
    await expect(page).toHaveURL(/\/warehouse-stock$/)
    await expect(page.getByTestId('back-to-warehouse-stock-origin')).toHaveCount(0)

    await page.getByTestId('warehouse-stock-filter-sku').locator('input').fill('OD-TENT-001')
    await page.getByTestId('warehouse-stock-filter-sku').locator('input').blur()
    await page.getByTestId('warehouse-stock-detail-button-OD-TENT-001').first().click()
    await expect(page.getByTestId('warehouse-stock-drawer')).toBeVisible()
    const warehouseStockListUrl = page.url()

    await page.getByTestId('warehouse-stock-drawer-stock-sales-link').click()
    await expect(page).toHaveURL(/\/stock-sales\?skuKeyword=OD-TENT-001&returnTo=/)

    const backToOrigin = page.getByTestId('back-to-stock-sales-origin')
    await expect(backToOrigin).toBeVisible()
    await backToOrigin.click()
    await expect(page).toHaveURL(warehouseStockListUrl)
  })

  test('Scenario 12: Stock-Sales Drawer -> Warehouse Stock -> Back returns to Stock-Sales (List)', async ({ page }) => {
    await login(page)
    await page.getByTestId('nav-stock-sales').click()
    await expect(page).toHaveURL(/\/stock-sales$/)
    await expect(page.getByTestId('back-to-stock-sales-origin')).toHaveCount(0)

    await page.getByTestId('stock-sales-filter-sku').locator('input').fill('OD-TENT-001')
    await page.getByTestId('stock-sales-filter-sku').locator('input').blur()
    await page.getByTestId('stock-sales-detail-button-OD-TENT-001').click()
    const stockSalesListUrl = page.url()

    await page.getByTestId('stock-sales-drawer-warehouse-stock-link').click()
    await expect(page).toHaveURL(/\/warehouse-stock\?skuKeyword=OD-TENT-001&returnTo=/)

    const backToOrigin = page.getByTestId('back-to-warehouse-stock-origin')
    await expect(backToOrigin).toBeVisible()
    await backToOrigin.click()
    await expect(page).toHaveURL(stockSalesListUrl)
  })

  test('Scenario 14: Order Detail Fulfillment -> Arrival List -> Back returns to Order Detail', async ({ page }) => {
    // PO-OUTDOOR-03 deliberately has no tr_inv/tr_arr row seeded (an honest
    // empty Arrival List result, matching the existing G scenario in
    // arrival-warehouse-stock-visibility-foundation.spec.ts) - this Scenario
    // only needs the round trip itself, not a populated result.
    const orderId = await createApprovedOrder(page, 'OD-TENT-001')
    linkOrderToOfficialPoNo(orderId, 'PO-OUTDOOR-03')

    await page.goto(`/orders/${orderId}`)
    await expect(page.getByTestId('fulfillment-section')).toBeVisible()
    const orderDetailUrl = page.url()

    await page.getByTestId('fulfillment-view-arrivals-button').click()
    await expect(page).toHaveURL(/\/arrivals\?poNumber=PO-OUTDOOR-03&returnTo=/)

    const backToOrigin = page.getByTestId('back-to-arrival-origin')
    await expect(backToOrigin).toBeVisible()
    await backToOrigin.click()
    await expect(page).toHaveURL(orderDetailUrl)
  })

  test('Scenario 15: Arrival Detail direct URL access falls back to the plain Arrival List', async ({ page }) => {
    await login(page)
    // No returnTo in this URL at all - simulates a bookmarked/typed link.
    await page.goto('/arrivals/SUP_ALPHA/PO-OUTDOOR-01/INV-OUTDOOR-01')
    await expect(page.getByRole('heading', { name: /入荷詳細/ })).toBeVisible()

    await page.getByTestId('back-to-arrival-list').click()
    await expect(page).toHaveURL(/^http:\/\/localhost:5173\/arrivals$/)
  })
})
