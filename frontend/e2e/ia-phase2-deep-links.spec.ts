import { test, expect, type Page } from '@playwright/test'

/**
 * G-OPS Information Architecture Phase 2
 * (docs/gops-master-maintenance-hub-implementation.md): Scenarios 7-10 -
 * Price Change Edit's Brand Context Deep Link, and the new Filter Chips on
 * Stock/Sales, Warehouse Stock and Arrival ("現在何を見ているのか"
 * visibility, IA Audit §11/12/13).
 */

const OPERATOR_USERNAME = 'purchase01'
const OPERATOR_PASSWORD = 'DemoPass123!'

async function login(page: Page) {
  await page.goto('/')
  await page.getByLabel('ユーザー名').fill(OPERATOR_USERNAME)
  await page.getByLabel('パスワード').fill(OPERATOR_PASSWORD)
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page.getByTestId('nav-dashboard')).toBeVisible()
}

test.describe('IA Phase 2: Deep Links / Filter Context', () => {
  test('Scenario 7: Dashboard Brand -> Price Change 作成 -> 対象SKUがBrandで絞り込み済み -> Back後もBrand Context維持', async ({ page }) => {
    await login(page)

    // LIVORA (BR_HOME)'s own "価格変更" action - reuses the existing Brand
    // row Deep Link pattern (Candidates/Draft/AwaitingSupplier/Attention
    // columns already worked this way), carrying brandCode through
    // PriceChangeListPage's own silent passthrough into the newly-created
    // Set's Edit screen.
    const brandRow = page.locator('table tbody tr').filter({ hasText: 'LIVORA' }).first()
    await brandRow.getByRole('button', { name: '作成' }).click()
    await expect(page).toHaveURL(/\/price-changes\?brandCode=BR_HOME/)

    await page.getByTestId('create-price-change-button').click()
    await expect(page).toHaveURL(/\/price-changes\/\d+\/edit\?brandCode=BR_HOME/)
    // The Brand Deep Link auto-arms the search - every one of this Brand's
    // SKUs is visible immediately, no manual "検索" click required.
    await expect(page.getByText('HM-MUG-001')).toBeVisible()
    await expect(page.getByText('HM-RUG-001')).toBeVisible()

    const editUrl = page.url()
    await page.getByTestId('back-to-price-change-origin').click()
    await expect(page).toHaveURL(/\/price-changes\?brandCode=BR_HOME/)

    await page.goBack()
    await expect(page).toHaveURL(editUrl)
    // Brand Context survived the round trip - the Filter still reads BR_HOME
    // and the SKU list is still populated, not reset to empty.
    await expect(page.getByLabel('ブランド')).toHaveValue('BR_HOME')
    await expect(page.getByText('HM-MUG-001')).toBeVisible()
  })

  test('Scenario 8: Stock/Sales - Brand Filter適用時にFilter Chipで明示される', async ({ page }) => {
    await login(page)
    await page.goto('/stock-sales?brandCode=BR_HOME')
    await expect(page.getByTestId('stock-sales-filter-chip-brandCode')).toContainText('BR_HOME')

    // Filter Chipの削除でFilterが解除され、Chip自体も消える。
    await page.getByTestId('stock-sales-filter-chip-brandCode').locator('.MuiChip-deleteIcon').click()
    await expect(page).not.toHaveURL(/brandCode=/)
    await expect(page.getByTestId('stock-sales-filter-chip-brandCode')).toHaveCount(0)
  })

  test('Scenario 9: Warehouse Stock - Supplier/Brand Filter適用時にFilter Chipで明示される', async ({ page }) => {
    await login(page)
    await page.goto('/warehouse-stock?brandCode=BR_KITCHEN')
    await expect(page.getByTestId('warehouse-stock-filter-chip-brandCode')).toContainText('BR_KITCHEN')
  })

  test('Scenario 10: Arrival - PO No/Supplier/Brand Filter適用時にFilter Chipで明示される', async ({ page }) => {
    await login(page)
    await page.goto('/arrivals?supplierCode=SUP_ALPHA&brandCode=BR_OUTDOOR')
    await expect(page.getByTestId('arrival-filter-chip-supplierCode')).toContainText('SUP_ALPHA')
    await expect(page.getByTestId('arrival-filter-chip-brandCode')).toContainText('BR_OUTDOOR')

    // PO No.単独でも同様にChip表示される（PO No入口の維持を確認）。
    await page.goto('/arrivals?poNumber=PO-OUTDOOR-01')
    await expect(page.getByTestId('arrival-filter-chip-poNumber')).toContainText('PO-OUTDOOR-01')
  })

  test('Scenario 11: Order History - 既存Flat List/Search, Regressionなし', async ({ page }) => {
    await login(page)
    await page.getByTestId('nav-history').click()
    await expect(page).toHaveURL(/\/orders\/history$/)
    // 既存のFilter/Search欄が変更なく存在すること（IA変更なしの確認）。
    await expect(page.getByTestId('order-history-filter-supplier')).toBeVisible()
    await expect(page.getByTestId('order-history-filter-brand')).toBeVisible()
    await expect(page.getByTestId('order-no-keyword-input')).toBeVisible()
    await expect(page.getByText(/件の発注/)).toBeVisible()
  })
})
