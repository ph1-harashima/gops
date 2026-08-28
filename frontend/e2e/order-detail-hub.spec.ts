import { test, expect, type Page } from '@playwright/test'

/**
 * Phase 6-B: Order List / Order Detail reorganization
 * (docs/production-ux-workflow-redesign.md 9章). Confirms the renamed
 * "発注一覧" / "発注詳細" labels are live, and that 発注詳細's single
 * Status-driven primary Action points at the right existing Route with the
 * right Label - reusing OrderHistoryListPage / OrderHistoryDetailPage as-is,
 * no new screens.
 */

const DEMO_USERNAME = 'purchase01'
const DEMO_PASSWORD = 'DemoPass123!'

async function login(page: Page) {
  await page.goto('/')
  await page.getByLabel('ユーザー名').fill(DEMO_USERNAME)
  await page.getByLabel('パスワード').fill(DEMO_PASSWORD)
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page.getByTestId('nav-dashboard')).toBeVisible()
}

test.describe('Phase 6-B: Order List / Order Detail', () => {
  test('Nav label and List title read "発注一覧", not "発注履歴"', async ({ page }) => {
    await login(page)
    await expect(page.getByTestId('nav-history')).toHaveText('発注一覧')
    await page.getByTestId('nav-history').click()
    await expect(page.getByRole('heading', { name: '発注一覧' })).toBeVisible()
  })

  test('DRAFT Order Detail shows "ドラフトを編集" and links to the Draft edit screen', async ({ page }) => {
    await login(page)
    await page.getByTestId('nav-candidates').click()
    const firstCheckbox = page.locator('table tbody tr').first().locator('input[type="checkbox"]')
    await firstCheckbox.check()
    await page.getByTestId('create-draft-button').click()
    await expect(page).toHaveURL(/\/orders\/drafts\/(\d+)/)
    const draftId = page.url().match(/\/orders\/drafts\/(\d+)/)?.[1]
    expect(draftId).toBeTruthy()

    await page.goto(`/orders/${draftId}`)
    await expect(page.getByRole('heading', { name: '発注詳細' })).toBeVisible()
    const action = page.getByTestId('order-detail-primary-action')
    await expect(action).toHaveText('ドラフトを編集')
    await action.click()
    await expect(page).toHaveURL(new RegExp(`/orders/drafts/${draftId}(\\?|$)`))
  })

  test('AWAITING_SUPPLIER Order Detail shows "メーカー回答を入力" and links to Supplier Response', async ({ page }) => {
    await login(page)
    await page.goto('/orders/history?status=AWAITING_SUPPLIER')
    await expect(page.getByText(/件の発注|該当する発注が見つかりませんでした/)).toBeVisible()
    const rowCount = await page.locator('table tbody tr').count()
    test.skip(rowCount === 0, 'No AWAITING_SUPPLIER orders in the current Demo Data')

    await page.locator('table tbody tr').first().click()
    await expect(page).toHaveURL(/\/orders\/\d+/)
    const action = page.getByTestId('order-detail-primary-action')
    await expect(action).toHaveText('メーカー回答を入力')
    await action.click()
    await expect(page).toHaveURL(/\/supplier-response/)
  })

  test('SUPPLIER_CONFIRMED Order Detail shows "メーカー回答を確認する" (review, not input)', async ({ page }) => {
    await login(page)
    await page.goto('/orders/history?status=SUPPLIER_CONFIRMED')
    await expect(page.getByText(/件の発注|該当する発注が見つかりませんでした/)).toBeVisible()
    const rowCount = await page.locator('table tbody tr').count()
    test.skip(rowCount === 0, 'No SUPPLIER_CONFIRMED orders in the current Demo Data')

    await page.locator('table tbody tr').first().click()
    const action = page.getByTestId('order-detail-primary-action')
    await expect(action).toHaveText('メーカー回答を確認する')

    // Phase 6-C Acceptance Scenario D: following the review link must land
    // on a genuinely READ ONLY Supplier Response - no Save/Confirm CTA, and
    // the confirmed-qty inputs are actually readOnly (not just hidden
    // buttons), confirming SupplierResponsePage.isEditable's existing
    // Status gate rather than assuming it from the button label alone.
    await action.click()
    await expect(page).toHaveURL(/\/supplier-response/)
    await expect(page.getByRole('heading', { name: 'メーカー回答' })).toBeVisible()
    await expect(page.getByTestId('save-response-button')).toHaveCount(0)
    await expect(page.getByTestId('confirm-response-button')).toHaveCount(0)
    const firstQtyInput = page.locator('[data-testid^="confirmed-qty-input-"] input').first()
    await expect(firstQtyInput).toHaveAttribute('readonly', '')
  })
})
