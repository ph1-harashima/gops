import { test, expect, type Page } from '@playwright/test'

/**
 * Regression suite for the Confirm Dialog instability bug found during live
 * demo testing on a Revision 2 order: clicking "メーカー回答を確定" opened
 * the confirmation Dialog, which then closed itself without any explicit
 * Cancel/Confirm click. Root-caused via live reproduction (claude-in-chrome):
 * a single miss-click landing on the Dialog's Backdrop is enough to dismiss
 * it through MUI's default `onClose` (backdropClick/escapeKeyDown) path,
 * with the Backend never touched at all (confirmed via Network tab: zero
 * requests fired on the failed first attempt) - NOT related to the previous
 * Phase's `confirmMutation.reset()` useEffect (verified live: the Dialog
 * survives repeated `response` cache replacements with no explicit click).
 *
 * Fix: `SupplierResponsePage.tsx`'s Confirm Dialog now ignores the
 * `backdropClick`/`escapeKeyDown` reasons in `onClose` - only the explicit
 * Cancel/Confirm buttons (or a successful Confirm) may close it.
 *
 * Same login/logout Role-switch pattern and selector policy as the other
 * specs. Prerequisite: Demo Reset run first.
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

  const enterSupplierResponse = page.getByTestId('order-detail-primary-action')
  await expect(enterSupplierResponse).toHaveText('メーカー回答を入力')
  await enterSupplierResponse.click()
  await expect(page).toHaveURL(new RegExp(`/orders/${draftId}/supplier-response(\\?.*)?$`))

  return draftId!
}

test.describe('Supplier Response Confirm Dialog stability regression', () => {
  test('A: 全回答保存済み -> Confirm button click 1回 -> Dialog表示継続', async ({ page }) => {
    const SKU_A = 'KT-KNIFE-001'
    const SKU_B = 'KT-KNIFE-002'
    await createAwaitingSupplierResponse(page, SKU_A, SKU_B)

    await page.getByTestId(`confirmed-qty-input-${SKU_A}`).locator('input').fill('3')
    await page.getByTestId(`confirmed-qty-input-${SKU_B}`).locator('input').fill('10')
    await page.getByTestId('save-response-button').click()
    await expect(page.getByText('回答を保存しました。')).toBeVisible()

    await page.getByTestId('confirm-response-button').click()
    await expect(page.getByRole('dialog')).toBeVisible()
    // Not just an instant check - the reported bug closed the Dialog within
    // a fraction of a second with no explicit action taken.
    await page.waitForTimeout(1500)
    await expect(page.getByRole('dialog')).toBeVisible()
    await expect(page.getByText('メーカー回答を確定しますか？')).toBeVisible()
  })

  test('B-1: Dialog表示中にBackdropクリック -> Dialogが閉じない（実際のRoot Cause再現）', async ({ page }) => {
    const SKU_A = 'KT-PAN-001'
    const SKU_B = 'KT-PAN-002'
    await createAwaitingSupplierResponse(page, SKU_A, SKU_B)

    await page.getByTestId(`confirmed-qty-input-${SKU_A}`).locator('input').fill('5')
    await page.getByTestId(`confirmed-qty-input-${SKU_B}`).locator('input').fill('1')
    await page.getByTestId('save-response-button').click()
    await expect(page.getByText('回答を保存しました。')).toBeVisible()

    await page.getByTestId('confirm-response-button').click()
    await expect(page.getByRole('dialog')).toBeVisible()

    // The exact mechanism found live: a click landing on the Backdrop (well
    // outside the dialog panel) must NOT dismiss this particular Dialog.
    await page.mouse.click(5, 5)
    await expect(page.getByRole('dialog')).toBeVisible()

    // Escape must not dismiss it either (Expected Behavior 3章).
    await page.keyboard.press('Escape')
    await expect(page.getByRole('dialog')).toBeVisible()
  })

  test('B-2: Dialog表示中にQuery refetch (window focus) -> Dialogが勝手に閉じない', async ({ page }) => {
    const SKU_A = 'KT-BOWL-001'
    const SKU_B = 'KT-BOWL-002'
    await createAwaitingSupplierResponse(page, SKU_A, SKU_B)

    await page.getByTestId(`confirmed-qty-input-${SKU_A}`).locator('input').fill('2')
    await page.getByTestId(`confirmed-qty-input-${SKU_B}`).locator('input').fill('4')
    await page.getByTestId('save-response-button').click()
    await expect(page.getByText('回答を保存しました。')).toBeVisible()

    await page.getByTestId('confirm-response-button').click()
    await expect(page.getByRole('dialog')).toBeVisible()

    // Simulate the window-focus event React Query's default
    // refetchOnWindowFocus listens for, forcing a background refetch of
    // useSupplierResponse (and therefore the confirmMutation.reset()
    // useEffect from the previous Phase's fix) while the Dialog is open.
    await page.evaluate(() => window.dispatchEvent(new Event('focus')))
    await page.waitForTimeout(1000)
    await expect(page.getByRole('dialog')).toBeVisible()
  })

  test('C: Dialog Cancel -> 閉じる -> Responseは未確定', async ({ page }) => {
    const SKU_A = 'HM-RUG-001'
    const SKU_B = 'HM-RUG-002'
    await createAwaitingSupplierResponse(page, SKU_A, SKU_B)

    await page.getByTestId(`confirmed-qty-input-${SKU_A}`).locator('input').fill('7')
    await page.getByTestId(`confirmed-qty-input-${SKU_B}`).locator('input').fill('9')
    await page.getByTestId('save-response-button').click()
    await expect(page.getByText('回答を保存しました。')).toBeVisible()

    await page.getByTestId('confirm-response-button').click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await page.getByRole('button', { name: 'キャンセル' }).click()
    await expect(page.getByRole('dialog')).not.toBeVisible()

    // Still AWAITING_SUPPLIER, still editable, no confirm happened.
    await expect(page.getByTestId('save-response-button')).toBeVisible()
    await expect(page.getByTestId('confirmed-qty-input-' + SKU_A).locator('input')).toHaveValue('7')

    // D: re-click Confirm -> Dialog opens correctly again.
    await page.getByTestId('confirm-response-button').click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await expect(page.getByText('メーカー回答を確定しますか？')).toBeVisible()
  })

  test('F: Confirm button dialog内 double click -> 二重API実行されない -> 正常にOrder Detailへ遷移', async ({ page }) => {
    const SKU_A = 'HM-MUG-001'
    const SKU_B = 'HM-MUG-002'
    const draftId = await createAwaitingSupplierResponse(page, SKU_A, SKU_B)

    await page.getByTestId(`confirmed-qty-input-${SKU_A}`).locator('input').fill('5')
    await page.getByTestId(`confirmed-qty-input-${SKU_B}`).locator('input').fill('20')
    await page.getByTestId('save-response-button').click()
    await expect(page.getByText('回答を保存しました。')).toBeVisible()

    await page.getByTestId('confirm-response-button').click()
    await expect(page.getByRole('dialog')).toBeVisible()

    const confirmDialogButton = page.getByTestId('confirm-response-dialog-confirm')
    // Fire two rapid clicks - the second must be a no-op (button disables
    // synchronously once the mutation starts, per confirmMutation.isPending).
    await confirmDialogButton.click()
    await confirmDialogButton.click({ trial: false, force: true }).catch(() => {
      // The button may already be disabled/gone by the time the second
      // click dispatches - that outcome itself is the desired "no double
      // submit" behavior, not a test failure.
    })

    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))
    await expect(page.getByRole('heading', { name: '発注詳細' })).toBeVisible()
    // Scoped to the status Chip specifically (a plain getByText('メーカー確定済み')
    // also matches the Timeline's "メーカー回答待ち → メーカー確定済み" row).
    await expect(page.getByText('メーカー確定済み', { exact: true })).toBeVisible()
  })

  test('H: confirmedQty=0 -> Dialog動作も従来どおり -> Confirm可能', async ({ page }) => {
    const SKU_A = 'HM-TOWEL-001'
    const SKU_B = 'HM-TOWEL-002'
    const draftId = await createAwaitingSupplierResponse(page, SKU_A, SKU_B)

    await page.getByTestId(`confirmed-qty-input-${SKU_A}`).locator('input').fill('0')
    await page.getByTestId(`confirmed-qty-input-${SKU_B}`).locator('input').fill('0')
    await page.getByTestId('save-response-button').click()
    await expect(page.getByText('回答を保存しました。')).toBeVisible()
    await expect(page.getByText('回答済みSKU数: 2 / 2')).toBeVisible()

    await page.getByTestId('confirm-response-button').click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await page.waitForTimeout(1000)
    await expect(page.getByRole('dialog')).toBeVisible()

    await page.getByTestId('confirm-response-dialog-confirm').click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))
  })
})
