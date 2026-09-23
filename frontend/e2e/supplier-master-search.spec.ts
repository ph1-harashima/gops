import { test, expect, type Page } from '@playwright/test'

/**
 * G-OPS Operational Workflow Realignment Phase G §18 (Supplier/Master UX):
 * Supplier Code/Name search, added on top of the existing (Stage 5H RC-J)
 * Backend-side pagination - this is the first E2E coverage of the Supplier
 * Master List screen at all.
 */

const ADMIN_USERNAME = 'admin01'
const ADMIN_PASSWORD = 'DemoPass123!'

async function login(page: Page) {
  await page.goto('/')
  await page.getByLabel('ユーザー名').fill(ADMIN_USERNAME)
  await page.getByLabel('パスワード').fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page.getByTestId('nav-dashboard')).toBeVisible()
}

/** nav-admin-suppliers lives inside the "Master Maintenance" dropdown Menu
 * (ADMIN-only) - opened via nav-master-maintenance first. */
async function goToSupplierMasterList(page: Page) {
  await page.getByTestId('nav-master-maintenance').click()
  await page.getByTestId('nav-admin-suppliers').click()
  await expect(page.getByTestId('supplier-master-list-table-container')).toBeVisible()
}

test.describe('Phase G §18: Supplier Master List Search', () => {
  test('Search by Supplier Code narrows the list to an exact match', async ({ page }) => {
    await login(page)
    await goToSupplierMasterList(page)

    await page.getByTestId('supplier-master-search-input').fill('SUP_ALPHA')
    await page.getByTestId('supplier-master-search-input').blur()

    await expect(page.getByTestId('supplier-master-row-SUP_ALPHA')).toBeVisible()
    const rows = page.locator('table tbody tr[data-testid^="supplier-master-row-"]')
    await expect(rows).toHaveCount(1)
  })

  test('Search matching nothing shows the empty state, not an error', async ({ page }) => {
    await login(page)
    await goToSupplierMasterList(page)

    await page.getByTestId('supplier-master-search-input').fill('NO_SUCH_SUPPLIER_XYZ')
    await page.getByTestId('supplier-master-search-input').blur()

    await expect(page.getByText('該当するメーカーがありません。')).toBeVisible()
  })

  test('Clearing the search restores the full list', async ({ page }) => {
    await login(page)
    await goToSupplierMasterList(page)

    const rows = page.locator('table tbody tr[data-testid^="supplier-master-row-"]')
    // Baseline: whatever the first unfiltered page shows today (this
    // environment's own demo seed count, not a Production Snapshot's
    // 602 - the two are independent Supplier catalogs).
    const fullListCount = await rows.count()
    expect(fullListCount).toBeGreaterThan(0)

    await page.getByTestId('supplier-master-search-input').fill('SUP_ALPHA')
    await page.getByTestId('supplier-master-search-input').blur()
    await expect(rows).toHaveCount(1)

    await page.getByTestId('supplier-master-search-input').fill('')
    await page.getByTestId('supplier-master-search-input').blur()

    // A cleared search must show the same full page again, not stay
    // narrowed to the last search.
    await expect(rows).toHaveCount(fullListCount)
  })

  test('Pagination controls exist at both top and bottom', async ({ page }) => {
    await login(page)
    await goToSupplierMasterList(page)
    await expect(page.getByTestId('supplier-master-list-pagination-top')).toBeVisible()
    await expect(page.getByTestId('supplier-master-list-pagination-bottom')).toBeVisible()
  })
})
