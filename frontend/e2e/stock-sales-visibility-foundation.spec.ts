import { test, expect, type Page } from '@playwright/test'

/**
 * Phase 8-H: Stock / Sales Visibility Foundation. Covers 在庫・販売確認
 * (List + SKU Detail Drawer) end to end against real Legacy Demo MySQL Seed
 * Data (backend/demo-data/02-seed.sql), plus Navigation to Warehouse Stock /
 * SKU Detail and regression checks for existing Ordering/Price Change/
 * Arrival/Warehouse Stock (Section 18).
 *
 * Prerequisite: same as the other Phase 7/8 specs - Demo Reset run first.
 */

const OPERATOR_USERNAME = 'purchase01'
const OPERATOR_PASSWORD = 'DemoPass123!'

async function login(page: Page, username: string, password: string) {
  await page.goto('/')
  await page.getByLabel('ユーザー名').fill(username)
  await page.getByLabel('パスワード').fill(password)
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page.getByTestId('nav-dashboard')).toBeVisible()
}

test.describe('Phase 8-H: Stock / Sales Visibility Foundation', () => {
  test('A: Navigation - 在庫・販売確認 Nav entry reaches the List screen', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-stock-sales').click()
    await expect(page).toHaveURL(/\/stock-sales$/)
    await expect(page.getByRole('heading', { name: '在庫・販売確認' })).toBeVisible()
  })

  test('B: SKU Search narrows results and shows Current Stock/Sales/Open PO/Open Arrival', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-stock-sales').click()

    // 24 SKUs are seeded (backend/demo-data/02-seed.sql), sorted brand_cd
    // then item_cd - OD-TENT-001 does not land on the unfiltered first page
    // (default size 20), so this test filters immediately rather than
    // asserting visibility beforehand.
    await page.getByTestId('stock-sales-filter-sku').locator('input').fill('OD-TENT-001')
    await page.getByTestId('stock-sales-filter-sku').locator('input').blur()

    await expect(page.getByTestId('stock-sales-row-OD-TENT-001')).toBeVisible()
    await expect(page.getByTestId('stock-sales-row-OD-BAG-002')).not.toBeVisible()

    // Seed data (backend/demo-data/02-seed.sql): OD-TENT-001 current_stock(wh01)=2, sold_qty=42.
    const row = page.getByTestId('stock-sales-row-OD-TENT-001')
    await expect(row).toContainText('2')
    await expect(row).toContainText('42')
  })

  test('C: Stock/Sales numeric range Filters are plain Backend Filters', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-stock-sales').click()

    // OD-BAG-002: current_stock(wh01)=0 (backend/demo-data/02-seed.sql).
    await page.getByTestId('stock-sales-filter-max-stock').locator('input').fill('0')
    await page.getByTestId('stock-sales-filter-max-stock').locator('input').blur()
    await expect(page.getByTestId('stock-sales-row-OD-BAG-002')).toBeVisible()
    await expect(page.getByTestId('stock-sales-row-OD-TENT-001')).not.toBeVisible()
  })

  test('D: SKU Detail Drawer shows Current Stock/Sales/Open PO/Open Arrival/Timestamp with Tooltip', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-stock-sales').click()
    await page.getByTestId('stock-sales-detail-button-OD-BAG-002').click()

    await expect(page.getByTestId('stock-sales-drawer')).toBeVisible()
    // Seed: OD-BAG-002 po_qty_1=15, arr_qty_1=10, sold_qty=65, current_stock=0.
    await expect(page.getByTestId('stock-sales-drawer')).toContainText('15')
    await expect(page.getByTestId('stock-sales-drawer')).toContainText('10')
    await expect(page.getByTestId('stock-sales-drawer')).toContainText('65')
    await expect(page.getByTestId('stock-sales-drawer')).toContainText('当月販売数量')
    await expect(page.getByTestId('stock-sales-drawer')).toContainText('G-SYSデータ更新日時')

    await page.getByTestId('stock-sales-drawer-close').click()
    await expect(page.getByTestId('stock-sales-drawer')).not.toBeVisible()
  })

  test('E: SOLD_QTY label never reads as Trend/Daily/Recent - only 当月販売数量 wording appears', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-stock-sales').click()

    await expect(page.getByText('当月販売数量').first()).toBeVisible()
    await expect(page.getByText('最近の販売数')).not.toBeVisible()
    await expect(page.getByText('過去30日')).not.toBeVisible()
    await expect(page.getByText('Sales Trend')).not.toBeVisible()
    await expect(page.getByText('Daily Sales')).not.toBeVisible()
  })

  test('F: Drawer Navigation - 倉庫在庫を見る goes to Warehouse Stock filtered by SKU', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-stock-sales').click()
    // OD-BAG-002 (not OD-TENT-001) - lands on the unfiltered first page
    // (default size 20, sorted brand_cd/item_cd - see test B's own comment).
    await page.getByTestId('stock-sales-detail-button-OD-BAG-002').click()
    await page.getByTestId('stock-sales-drawer-warehouse-stock-link').click()

    await expect(page).toHaveURL(/\/warehouse-stock\?skuKeyword=OD-BAG-002/)
    await expect(page.getByTestId('warehouse-stock-row-01-OD-BAG-002')).toBeVisible()
  })

  test('G: Drawer Navigation - 商品詳細を見る goes to the existing SKU Detail screen', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-stock-sales').click()
    await page.getByTestId('stock-sales-detail-button-OD-BAG-002').click()
    await page.getByTestId('stock-sales-drawer-sku-detail-link').click()

    await expect(page).toHaveURL(/\/items\/OD-BAG-002$/)
  })

  test('H: Pagination controls are present and Backend-driven', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-stock-sales').click()
    await expect(page.getByTestId('stock-sales-pagination')).toBeVisible()

    await page.getByTestId('stock-sales-pagination').getByLabel('Go to next page').click()
    await expect(page).toHaveURL(/[?&]page=1/)
  })

  test('I: Existing Ordering Workflow (Candidate List -> Create Draft) is unaffected', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-candidates').click()
    await expect(page.getByTestId('candidate-row-KT-KNIFE-002')).toBeVisible()
    await page.getByTestId('candidate-checkbox-KT-KNIFE-002').locator('input').check()
    await page.getByTestId('create-draft-button').click()
    await expect(page).toHaveURL(/\/orders\/drafts\/\d+/)
  })

  test('J: Existing Price Change screen is unaffected', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-price-changes').click()
    await expect(page).toHaveURL(/\/price-changes$/)
    await expect(page.getByRole('heading', { name: '価格変更一覧' })).toBeVisible()
  })

  test('K: Existing Arrival / Warehouse Stock screens are unaffected', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-arrivals').click()
    await expect(page).toHaveURL(/\/arrivals$/)
    await expect(page.getByTestId('arrival-row-PO-OUTDOOR-01')).toBeVisible()

    await page.getByTestId('nav-warehouse-stock').click()
    await expect(page).toHaveURL(/\/warehouse-stock$/)
    await expect(page.getByRole('heading', { name: '倉庫在庫' })).toBeVisible()
  })

  test('L: SKU Detail screen (existing) still shows its own 当月販売数 unaffected by this Phase', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.goto('/items/OD-TENT-001')
    await expect(page.getByText('当月販売数')).toBeVisible()
  })
})
