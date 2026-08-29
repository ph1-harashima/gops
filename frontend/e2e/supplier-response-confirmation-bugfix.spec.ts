import { test, expect, type Page } from '@playwright/test'

/**
 * Regression suite for the Supplier Response Confirmation bug found during
 * live demo testing on `PO-DEMO-20260829-0001` (2026-08-29): the "メーカー回答
 * を確定" button used to become clickable as soon as every field was filled
 * in LOCALLY (unsaved), so clicking it before "回答を保存" made the Backend
 * reject the still-unanswered saved data with SUPPLIER_RESPONSE_INCOMPLETE -
 * and nothing ever cleared that error afterward, so it kept contradicting an
 * already-correct summary even once the data was actually saved and fully
 * answered. Root cause and fix: `SupplierResponsePage.tsx`'s Confirm gate is
 * now derived from the server-persisted `response.summary.unansweredCount`
 * (never from unsaved local edit state), and a `useEffect` resets the
 * Confirm mutation's error state whenever the server response changes.
 *
 * Same login/logout Role-switch pattern and selector policy (data-testid /
 * getByRole / getByLabel only) as the other specs. Prerequisite: Demo Reset
 * run first.
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

/** Candidate -> Draft -> Preview -> Submit -> (ADMIN) Approve -> Demo Send ->
 * Supplier Response, landing on the AWAITING_SUPPLIER Response screen with
 * both SKUs still unanswered. Ends the session logged in as `finalUser`. */
async function createAwaitingSupplierResponse(page: Page, skuA: string, skuB: string): Promise<string> {
  await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
  await page.getByTestId('nav-candidates').click()
  await expect(page.getByTestId(`candidate-row-${skuA}`)).toBeVisible()
  await page.getByTestId(`candidate-checkbox-${skuA}`).locator('input').check()
  await page.getByTestId(`candidate-checkbox-${skuB}`).locator('input').check()
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

  await page.getByTestId('order-detail-primary-action').click()
  await page.getByTestId('demo-send-button').click()
  await page.getByTestId('demo-send-dialog-confirm').click()
  await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))

  // Wait for the primary action button to actually re-render as "メーカー
  // 回答を入力" (post Demo Send / AWAITING_SUPPLIER) before clicking - a
  // same-instant click risks a stale-query race where the button briefly
  // still reflects APPROVED and routes back to the Preview page instead.
  const enterSupplierResponse = page.getByTestId('order-detail-primary-action')
  await expect(enterSupplierResponse).toHaveText('メーカー回答を入力')
  await enterSupplierResponse.click()
  await expect(page).toHaveURL(new RegExp(`/orders/${draftId}/supplier-response(\\?.*)?$`))

  return draftId!
}

test.describe('Supplier Response Confirmation bugfix regression', () => {
  test('A: 回答数量入力 -> Save -> answered 2/2, unanswered 0 -> Confirm可能', async ({ page }) => {
    const SKU_A = 'HM-MUG-001'
    const SKU_B = 'HM-MUG-002'
    await createAwaitingSupplierResponse(page, SKU_A, SKU_B)

    await page.getByTestId(`confirmed-qty-input-${SKU_A}`).locator('input').fill('7')
    await page.getByTestId(`confirmed-qty-input-${SKU_B}`).locator('input').fill('18')
    await page.getByTestId('save-response-button').click()
    await expect(page.getByText('回答を保存しました。')).toBeVisible()

    await expect(page.getByText('回答済みSKU数: 2 / 2')).toBeVisible()
    await expect(page.getByText('未回答SKU数: 0')).toBeVisible()
    await expect(page.getByTestId('confirm-response-button')).toBeEnabled()
    // The reported bug: no stale "incomplete" error must be showing.
    await expect(page.getByText('未回答のSKUがあるため確定できません。')).toHaveCount(0)
  })

  test('B: 片方null -> Confirm不可', async ({ page }) => {
    const SKU_A = 'HM-RUG-001'
    const SKU_B = 'HM-RUG-002'
    await createAwaitingSupplierResponse(page, SKU_A, SKU_B)

    await page.getByTestId(`confirmed-qty-input-${SKU_A}`).locator('input').fill('5')
    // SKU_B left unanswered.
    await page.getByTestId('save-response-button').click()
    await expect(page.getByText('回答を保存しました。')).toBeVisible()

    await expect(page.getByText('未回答SKU数: 1')).toBeVisible()
    await expect(page.getByTestId('confirm-response-button')).toBeDisabled()
  })

  test('C: Confirmed Qty = 0 -> 回答済み -> Confirm可能', async ({ page }) => {
    const SKU_A = 'HM-TOWEL-001'
    const SKU_B = 'HM-TOWEL-002'
    await createAwaitingSupplierResponse(page, SKU_A, SKU_B)

    await page.getByTestId(`confirmed-qty-input-${SKU_A}`).locator('input').fill('0')
    await page.getByTestId(`confirmed-qty-input-${SKU_B}`).locator('input').fill('0')
    await page.getByTestId('save-response-button').click()
    await expect(page.getByText('回答を保存しました。')).toBeVisible()

    await expect(page.getByText('回答済みSKU数: 2 / 2')).toBeVisible()
    await expect(page.getByText('回答数量0 SKU数: 2')).toBeVisible()
    await expect(page.getByTestId('confirm-response-button')).toBeEnabled()
  })

  test('D: 未Save状態でConfirmを試みてもstale Errorが残らない（元の再現手順そのもの）', async ({ page }) => {
    const SKU_A = 'KT-PAN-001'
    const SKU_B = 'KT-PAN-002'
    await createAwaitingSupplierResponse(page, SKU_A, SKU_B)

    // Fill in both fields LOCALLY only - the exact original repro: with the
    // fix, the Confirm button stays disabled because nothing is saved yet
    // (this alone prevents the SUPPLIER_RESPONSE_INCOMPLETE error from ever
    // being reachable through the UI).
    await page.getByTestId(`confirmed-qty-input-${SKU_A}`).locator('input').fill('4')
    await page.getByTestId(`confirmed-qty-input-${SKU_B}`).locator('input').fill('6')
    await expect(page.getByTestId('confirm-response-button')).toBeDisabled()

    // Now actually Save.
    await page.getByTestId('save-response-button').click()
    await expect(page.getByText('回答を保存しました。')).toBeVisible()

    await expect(page.getByText('回答済みSKU数: 2 / 2')).toBeVisible()
    await expect(page.getByTestId('confirm-response-button')).toBeEnabled()
    await expect(page.getByText('未回答のSKUがあるため確定できません。')).toHaveCount(0)

    // Full Confirm now genuinely succeeds.
    await page.getByTestId('confirm-response-button').click()
    await page.getByTestId('confirm-response-dialog-confirm').click()
    await expect(page.getByRole('heading', { name: '発注詳細' })).toBeVisible()
  })
})
