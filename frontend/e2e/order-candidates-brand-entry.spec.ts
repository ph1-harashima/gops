import { test, expect, type Page } from '@playwright/test'

/**
 * G-OPS 発注候補 Brand起点の主導線
 * (docs/gops-order-candidates-brand-entry-implementation.md): "発注候補 ->
 * Brand一覧 -> Brand選択 -> そのBrandの発注候補SKU一覧 -> SKU選択 -> Draft" -
 * the same "select the business-level parent Context before operating on
 * its children" principle Master Maintenance Hub already established
 * (docs/gops-master-maintenance-hub-implementation.md).
 *
 * Ground truth (re-confirmed against the Legacy Demo DB while writing this
 * phase): LIVORA=BR_HOME, KITCHENNE=BR_KITCHEN, FIELDNEST=BR_OUTDOOR.
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

test.describe('Order Candidates Brand Entry', () => {
  test('Scenario 1: 上部「発注候補」-> Brand一覧が表示される -> SKU一覧がいきなり表示されない', async ({ page }) => {
    await login(page)
    await page.getByTestId('nav-candidates').click()
    await expect(page).toHaveURL(/\/candidates$/)

    // Brand一覧 - never the SKU Flat List directly.
    await expect(page.getByTestId('order-candidate-brand-list-table-container')).toBeVisible()
    await expect(page.getByRole('heading', { name: '発注候補 - ブランド選択' })).toBeVisible()
    await expect(page.locator('[data-testid^="candidate-row-"]')).toHaveCount(0)
    await expect(page.getByTestId('order-candidate-brand-row-BR_HOME')).toBeVisible()
    await expect(page.getByTestId('order-candidate-brand-row-BR_KITCHEN')).toBeVisible()
    await expect(page.getByTestId('order-candidate-brand-row-BR_OUTDOOR')).toBeVisible()
    await expect(page.getByTestId('candidates-view-all-button')).toBeVisible()
  })

  test('Scenario 2: Brand LIVORA -> LIVORA Candidatesのみ表示', async ({ page }) => {
    await login(page)
    await page.getByTestId('nav-candidates').click()
    await page.getByTestId('order-candidate-brand-link-BR_HOME').click()
    await expect(page).toHaveURL(/\/candidates\?brandCode=BR_HOME$/)

    await expect(page.getByTestId('filter-chip-brandCode')).toContainText('LIVORA')
    const rowCount = await page.locator('table tbody tr').count()
    expect(rowCount).toBeGreaterThan(0)
    const brandCells = page.locator('table tbody tr td:nth-child(4)')
    const count = await brandCells.count()
    for (let i = 0; i < count; i++) {
      await expect(brandCells.nth(i)).toHaveText('LIVORA')
    }
  })

  test('Scenario 3: Brand KITCHENNE -> KITCHENNE Candidatesのみ表示', async ({ page }) => {
    await login(page)
    await page.getByTestId('nav-candidates').click()
    await page.getByTestId('order-candidate-brand-link-BR_KITCHEN').click()
    await expect(page).toHaveURL(/\/candidates\?brandCode=BR_KITCHEN$/)

    await expect(page.getByTestId('filter-chip-brandCode')).toContainText('KITCHENNE')
    await expect(page.getByText(/件の発注候補/)).toBeVisible()
    const brandCells = page.locator('table tbody tr td:nth-child(4)')
    const count = await brandCells.count()
    expect(count).toBeGreaterThan(0)
    for (let i = 0; i < count; i++) {
      await expect(brandCells.nth(i)).toHaveText('KITCHENNE')
    }
  })

  test('Scenario 4: 「すべての発注候補を表示」-> 全Brand Candidates表示', async ({ page }) => {
    await login(page)
    await page.getByTestId('nav-candidates').click()
    await page.getByTestId('candidates-view-all-button').click()
    await expect(page).toHaveURL(/\/candidates\?recommendedOnly=true$/)
    await expect(page.getByText(/件の発注候補/)).toBeVisible()

    // Multiple Brands present in one unfiltered-by-Brand List.
    const brandCells = page.locator('table tbody tr td:nth-child(4)')
    const count = await brandCells.count()
    expect(count).toBeGreaterThan(0)
    const distinctBrands = new Set<string>()
    for (let i = 0; i < count; i++) {
      distinctBrands.add((await brandCells.nth(i).textContent()) ?? '')
    }
    expect(distinctBrands.size).toBeGreaterThan(1)
  })

  test('Scenario 5: Dashboard Brand -> Brand別Candidatesへ直接遷移 -> 従来Deep Link維持', async ({ page }) => {
    await login(page)
    // Dashboard's own Brand row Deep Link (Order Candidates監査で確認済み) is
    // untouched - it must reach the Brand-scoped Candidate List directly,
    // WITHOUT passing through the new Brand List page at all.
    const brandNameCell = page.locator('table tbody tr').first().locator('td').first().getByRole('button')
    const brandName = (await brandNameCell.textContent())?.trim()
    await brandNameCell.click()
    await expect(page).toHaveURL(/\/candidates\?brandCode=[^&]+$/)
    await expect(page.getByTestId('order-candidate-brand-list-table-container')).toHaveCount(0)
    await expect(page.getByTestId('filter-chip-brandCode')).toContainText(brandName ?? '')
  })

  test('Scenario 6: Brand Candidates -> SKU Detail -> Back -> Brand Context維持', async ({ page }) => {
    await login(page)
    await page.getByTestId('nav-candidates').click()
    await page.getByTestId('order-candidate-brand-link-BR_HOME').click()
    const listUrl = page.url()

    const firstSkuButton = page.locator('table tbody tr').first().locator('td').nth(1).getByRole('button')
    await firstSkuButton.click()
    await expect(page).toHaveURL(/\/items\/.+returnTo=.*brandCode/)

    await page.getByRole('button', { name: '発注候補一覧へ戻る' }).click()
    await expect(page).toHaveURL(listUrl)
    await expect(page.getByTestId('filter-chip-brandCode')).toContainText('LIVORA')
  })

  test('Scenario 7: Brand Candidates -> Draft -> Back -> Brand Context維持', async ({ page }) => {
    await login(page)
    await page.getByTestId('nav-candidates').click()
    await page.getByTestId('order-candidate-brand-link-BR_HOME').click()
    const listUrl = page.url()

    const firstCheckbox = page.locator('table tbody tr').first().locator('input[type="checkbox"]')
    await firstCheckbox.check()
    await page.getByTestId('create-draft-button').click()
    await expect(page).toHaveURL(/\/orders\/drafts\/\d+\?returnTo=.*brandCode/)

    await page.getByRole('button', { name: '発注候補一覧へ戻る' }).click()
    await expect(page).toHaveURL(listUrl)
    await expect(page.getByTestId('filter-chip-brandCode')).toContainText('LIVORA')
  })

  test('Candidate List -> 「ブランド一覧へ戻る」-> Brand一覧に戻る', async ({ page }) => {
    await login(page)
    await page.getByTestId('nav-candidates').click()
    await page.getByTestId('order-candidate-brand-link-BR_HOME').click()
    await page.getByTestId('candidates-back-to-brand-list').click()
    await expect(page).toHaveURL(/\/candidates$/)
    await expect(page.getByTestId('order-candidate-brand-list-table-container')).toBeVisible()
  })
})
