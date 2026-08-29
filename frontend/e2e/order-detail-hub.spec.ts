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
const ADMIN_USERNAME = 'admin01'
const ADMIN_PASSWORD = 'DemoPass123!'

async function login(page: Page, username = DEMO_USERNAME, password = DEMO_PASSWORD) {
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

  // Phase 7-H (PO Preview Navigation audit): Root Cause was PoPreviewPage's
  // 戻る button always hardcoding the Draft path regardless of which screen
  // actually linked here - confirmed via Source, fixed via a new `backTo`
  // param (shared/navigation/returnTo.ts) carrying the immediate caller.
  // This proves the Order Detail -> Preview -> 戻る leg specifically (the
  // reported broken case); the Draft -> Preview -> 戻る leg is already
  // covered by role-approval-workflow.spec.ts Scenario A / core-demo-scenario.
  test('APPROVED Order Detail -> PO Preview -> 戻る returns to Order Detail, not the Draft screen', async ({ page }) => {
    await login(page)
    await page.getByTestId('nav-candidates').click()
    const firstCheckbox = page.locator('table tbody tr').first().locator('input[type="checkbox"]')
    await firstCheckbox.check()
    await page.getByTestId('create-draft-button').click()
    await expect(page).toHaveURL(/\/orders\/drafts\/(\d+)/)
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

    const action = page.getByTestId('order-detail-primary-action')
    await expect(action).toHaveText('PO Previewを見る')
    await action.click()
    await expect(page).toHaveURL(new RegExp(`/orders/drafts/${draftId}/preview`))

    // The whole point of this test: previously this button was mislabeled
    // "Order Draftへ戻る" AND navigated to /orders/drafts/{id} even though
    // Order Detail (not Draft) launched this Preview.
    const backButton = page.getByRole('button', { name: '発注詳細へ戻る' })
    await expect(backButton).toBeVisible()
    await backButton.click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))
    await expect(page.getByRole('heading', { name: '発注詳細' })).toBeVisible()
  })

  // Phase 7-H (Audit Timeline readability audit): confirmed via Source the
  // reported "備考変更 — →" / "ドラフト作成 — → DRAFT-..." pattern came from
  // unconditionally rendering a "{{old}} → {{new}}" template even when only
  // one side (a Create event's newValue) had a real value - the other side
  // fell back to a "—" placeholder that reads as "changed FROM nothing"
  // rather than "just created". Event semantics are unchanged - this proves
  // only the DISPLAY difference: a one-sided value renders alone (no "—", no
  // Arrow), a genuine two-sided change (e.g. ORDER_QTY_CHANGED) still shows
  // both sides with a visual Arrow icon (not the "→" character) between them.
  test('Audit Timeline: Create events show only the new value (no "—"/Arrow); real changes show both sides with an Arrow icon', async ({ page }) => {
    await login(page)
    await page.getByTestId('nav-candidates').click()
    const firstCheckbox = page.locator('table tbody tr').first().locator('input[type="checkbox"]')
    await firstCheckbox.check()
    await page.getByTestId('create-draft-button').click()
    await expect(page).toHaveURL(/\/orders\/drafts\/(\d+)/)
    const draftId = page.url().match(/\/orders\/drafts\/(\d+)/)?.[1]
    expect(draftId).toBeTruthy()

    // A real before/after change: bump the first line's Order Qty and Save.
    const qtyInput = page.locator('[data-testid^="order-qty-input-"] input').first()
    const oldQty = await qtyInput.inputValue()
    await qtyInput.fill('9')
    await page.getByTestId('save-draft-button').click()
    await expect(page.getByText('保存しました。')).toBeVisible()

    await page.goto(`/orders/${draftId}`)
    await expect(page.getByRole('heading', { name: '発注詳細' })).toBeVisible()

    // ドラフト作成 (ORDER_DRAFT_CREATED, Create-shaped: only newValue=draftNo
    // is real) - the whole Timeline row must never contain a bare "—".
    const createdRow = page.locator('[data-testid^="timeline-event-"]').filter({ hasText: 'ドラフト作成' })
    await expect(createdRow).toBeVisible()
    await expect(createdRow).toContainText(new RegExp(`DRAFT-\\d+`))
    await expect(createdRow).not.toContainText('—')
    await expect(createdRow.getByTestId('timeline-arrow-icon')).toHaveCount(0)

    // 発注数量変更 (ORDER_QTY_CHANGED, a genuine two-sided change) - both the
    // old and new Qty must be visible, joined by exactly one Arrow icon.
    const qtyChangedRow = page.locator('[data-testid^="timeline-event-"]').filter({ hasText: '発注数量変更' })
    await expect(qtyChangedRow).toBeVisible()
    await expect(qtyChangedRow).toContainText(oldQty)
    await expect(qtyChangedRow).toContainText('9')
    await expect(qtyChangedRow.getByTestId('timeline-arrow-icon')).toHaveCount(1)
  })
})
