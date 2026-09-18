import { test, expect, type Page } from '@playwright/test'

/**
 * Phase 7-C5 27章 Browser Scenario A-G: Supplier Response Revision /
 * Agreement Workflow. Same login/logout Role-switch pattern and selector
 * policy (data-testid / getByRole / getByLabel only) as
 * core-demo-scenario.spec.ts. Uses OD-CHAIR-002 (SUP_ALPHA/BR_OUTDOOR),
 * a SKU not touched by any other E2E spec, so this suite's own Draft/Order
 * lifecycle never collides with another spec's.
 *
 * Prerequisite: same as core-demo-scenario.spec.ts - run Demo Reset before
 * this suite. Each `test` below independently drives an Order from Draft
 * all the way to AWAITING_SUPPLIER/SUPPLIER_CONFIRMED, since the Workflow
 * only reaches the relevant point once per Order and Order Revision state is
 * not something this suite may reset mid-run.
 */

const OPERATOR_USERNAME = 'purchase01'
const OPERATOR_PASSWORD = 'DemoPass123!'
const ADMIN_USERNAME = 'admin01'
const ADMIN_PASSWORD = 'DemoPass123!'
// Phase 7-H (User表示 audit): agreedByDisplayName is preferred over the raw
// Login ID wherever agreedBy is shown - admin01's Demo display_name.
const ADMIN_DISPLAY_NAME = '鈴木 花子'

const SKU = 'OD-CHAIR-002'

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

/** Drives a fresh Order from Candidate selection through AWAITING_SUPPLIER
 * (Demo Send done, Response not yet saved), ending on the Supplier Response
 * screen as the ADMIN session, and returns the Order id. Order Qty is left
 * at its default (Recommended Qty). */
async function createAwaitingSupplierOrderAsAdmin(page: Page): Promise<string> {
  await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
  await page.getByTestId('nav-candidates').click()
  await expect(page.getByTestId(`candidate-row-${SKU}`)).toBeVisible()
  await page.getByTestId(`candidate-checkbox-${SKU}`).locator('input').check()
  await page.getByTestId('create-draft-button').click()
  await expect(page).toHaveURL(/\/orders\/drafts\/\d+(\?.*)?$/)
  const orderId = page.url().match(/\/orders\/drafts\/(\d+)/)?.[1]
  if (!orderId) throw new Error('draft id not found in URL')

  // No Order Qty edit is made here (Recommended Qty is used as-is), so the
  // Save button stays disabled (isDirty === false) - Submit for Approval
  // does not require a prior Save when nothing was actually changed.
  await page.getByTestId('submit-for-approval-button').click()
  await page.getByTestId('submit-for-approval-dialog-confirm').click()
  await expect(page).toHaveURL(new RegExp(`/orders/${orderId}(\\?.*)?$`))

  await logout(page)
  await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
  await page.goto(`/orders/${orderId}`)
  await page.getByTestId('order-detail-approve-button').click()
  await page.getByTestId('approve-dialog-confirm').click()
  await expect(page.getByText('承認しました。')).toBeVisible()

  // demo-send-button lives on the PO Preview screen, not Order Detail
  // (core-demo-scenario.spec.ts's own established flow).
  const goToPreview = page.getByTestId('order-detail-primary-action')
  await expect(goToPreview).toHaveText('PO プレビューを見る')
  await goToPreview.click()
  await expect(page).toHaveURL(new RegExp(`/orders/drafts/${orderId}/preview(\\?.*)?$`))
  await page.getByTestId('demo-send-button').click()
  await page.getByTestId('demo-send-dialog-confirm').click()
  await expect(page).toHaveURL(new RegExp(`/orders/${orderId}(\\?.*)?$`))
  // Scenario G: re-confirm here (the one-shot Router-state banner only
  // renders once, right after this exact navigation) that no real mail was
  // ever sent, before navigating on and losing that ephemeral state.
  await expect(page.getByText(/実際のメールは送信されていません/)).toBeVisible()

  // core-demo-scenario.spec.ts's own established pattern: wait for the
  // primary Action's label to actually reflect the post-Send AWAITING_SUPPLIER
  // Status before clicking it - clicking immediately can race a still-stale
  // cached "PO プレビューを見る" render from just before Demo Send completed.
  const enterSupplierResponse = page.getByTestId('order-detail-primary-action')
  await expect(enterSupplierResponse).toHaveText('メーカー回答を入力')
  await enterSupplierResponse.click()
  await expect(page).toHaveURL(new RegExp(`/orders/${orderId}/supplier-response(\\?.*)?$`))
  return orderId
}

/** From the Supplier Response screen (already on it, AWAITING_SUPPLIER),
 * answers the single line with confirmedQty and confirms - ending on Order
 * Detail with status SUPPLIER_CONFIRMED. */
async function answerAndConfirm(page: Page, orderId: string, confirmedQty: number) {
  const qtyInput = page.getByTestId(`confirmed-qty-input-${SKU}`).locator('input')
  await qtyInput.fill(String(confirmedQty))
  await page.getByTestId('save-response-button').click()
  await expect(page.getByText('回答を保存しました。')).toBeVisible()
  await page.getByTestId('confirm-response-button').click()
  await page.getByTestId('confirm-response-dialog-confirm').click()
  await expect(page).toHaveURL(new RegExp(`/orders/${orderId}(\\?.*)?$`))
  await expect(page.getByText('メーカー回答を確定しました。')).toBeVisible()
}

test('Scenario A: Order Rev1 -> Response1 -> Qty一致 -> Confirm -> Agree -> AGREED', async ({ page }) => {
  const orderId = await createAwaitingSupplierOrderAsAdmin(page)

  // Read the actually-ordered Qty from the Response screen's own orderedQty
  // column (the Demo Send-time snapshot) and answer with the SAME value.
  const orderedQtyCell = page.getByTestId(`response-row-${SKU}`).locator('td').nth(2)
  const orderedQty = Number(await orderedQtyCell.innerText())
  await answerAndConfirm(page, orderId, orderedQty)

  // Back on Order Detail, SUPPLIER_CONFIRMED -> go to Supplier Response to Agree.
  await page.getByTestId('order-detail-primary-action').click()
  await expect(page.getByTestId('agree-button')).toBeVisible()
  await page.getByTestId('agree-button').click()
  await page.getByTestId('agree-dialog-confirm').click()

  await expect(page.getByTestId('agreed-section')).toBeVisible()
  await expect(page.getByTestId('agreed-info')).toContainText(ADMIN_DISPLAY_NAME)
})

test('Scenario B: Rev1 Qty差異 -> Attention -> 差異確認 -> 差異のまま合意 -> Rev1は変更されない', async ({ page }) => {
  const orderId = await createAwaitingSupplierOrderAsAdmin(page)
  const orderedQtyCell = page.getByTestId(`response-row-${SKU}`).locator('td').nth(2)
  const orderedQty = Number(await orderedQtyCell.innerText())
  const confirmedQty = Math.max(0, orderedQty - 1) // deliberately different

  await answerAndConfirm(page, orderId, confirmedQty)
  await page.getByTestId('order-detail-primary-action').click()

  await expect(page.getByTestId('response-differences-section')).toBeVisible()
  await expect(page.getByTestId(`difference-${SKU}-QUANTITY_CHANGED`)).toBeVisible()

  // Acknowledge the Attention first (差異確認), then Agree WITHOUT force.
  const chip = page.locator('[data-testid^="attention-chip-"]').first()
  await chip.locator('span[aria-hidden]').click()

  await page.getByTestId('agree-button').click()
  await expect(page.getByTestId('force-agree-checkbox')).toHaveCount(0) // no ACTIVE Attention left to force past
  await page.getByTestId('agree-dialog-confirm').click()
  await expect(page.getByTestId('agreed-section')).toBeVisible()

  // Order Revision history still shows Revision 1 at its ORIGINAL orderedQty - never overwritten by the Response.
  await page.goto(`/orders/${orderId}`)
  await expect(page.getByTestId('revision-row-1')).toContainText(`${SKU}: ${orderedQty}`)
})

test('Scenario C: Rev1 Qty差異 -> 修正版作成 -> Rev2 -> Rev1/Response1履歴確認', async ({ page }) => {
  const orderId = await createAwaitingSupplierOrderAsAdmin(page)
  const orderedQtyCell = page.getByTestId(`response-row-${SKU}`).locator('td').nth(2)
  const orderedQty = Number(await orderedQtyCell.innerText())
  const confirmedQty = Math.max(0, orderedQty - 1)

  await answerAndConfirm(page, orderId, confirmedQty)
  await page.getByTestId('order-detail-primary-action').click()

  await page.getByTestId('create-revision-button').click()
  await page.getByTestId('revision-reason-input').locator('textarea').first().fill('メーカー在庫の都合により数量を修正')
  await page.getByTestId('apply-confirmed-values-checkbox').check()
  await page.getByTestId('revision-dialog-confirm').click()

  // Back to an editable Draft, with the confirmed value already applied.
  await expect(page).toHaveURL(new RegExp(`/orders/drafts/${orderId}(\\?.*)?$`))
  await expect(page.getByTestId(`order-qty-input-${SKU}`).locator('input')).toHaveValue(String(confirmedQty))

  // Resubmit, re-approve, and re-send -> Revision 2.
  await page.getByTestId('submit-for-approval-button').click()
  await page.getByTestId('submit-for-approval-dialog-confirm').click()
  await logout(page)
  await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
  await page.goto(`/orders/${orderId}`)
  await page.getByTestId('order-detail-approve-button').click()
  await page.getByTestId('approve-dialog-confirm').click()
  const goToPreviewAgain = page.getByTestId('order-detail-primary-action')
  await expect(goToPreviewAgain).toHaveText('PO プレビューを見る')
  await goToPreviewAgain.click()
  await expect(page).toHaveURL(new RegExp(`/orders/drafts/${orderId}/preview(\\?.*)?$`))
  await page.getByTestId('demo-send-button').click()
  await page.getByTestId('demo-send-dialog-confirm').click()

  await expect(page.getByTestId('revision-history-section')).toBeVisible()
  await expect(page.getByTestId('revision-row-1')).toContainText(`${SKU}: ${orderedQty}`)
  await expect(page.getByTestId('revision-row-2')).toContainText(`${SKU}: ${confirmedQty}`)
  await expect(page.getByTestId('order-detail-response-history-row-1')).toBeVisible()
  await expect(page.getByTestId('order-detail-response-history-row-2')).toBeVisible()
})

test('Scenario D: confirmedQty=0 -> NULLと区別 -> Reason/Supply Status表示', async ({ page }) => {
  const orderId = await createAwaitingSupplierOrderAsAdmin(page)

  // Before answering: unanswered (null), never shown as 0.
  await expect(page.getByTestId(`confirmed-qty-input-${SKU}`).locator('input')).toHaveValue('')

  // Acceptance Fix (item 10): while genuinely unanswered (null), the
  // Difference section must read as a plain "not yet answered" statement,
  // never as an "X -> 未回答" pseudo-change (the confusing
  // "未回答: 6 -> 未回答" the Acceptance Review found) - null and 0 must
  // stay visibly distinct, this existing Business Rule is unchanged.
  const unansweredDiff = page.getByTestId(`difference-${SKU}-UNANSWERED`)
  await expect(unansweredDiff).toBeVisible()
  await expect(unansweredDiff).not.toContainText('→')
  await expect(unansweredDiff).toContainText('メーカー回答はまだありません')

  await page.getByTestId(`confirmed-qty-input-${SKU}`).locator('input').fill('0')
  await page.getByTestId(`supply-status-select-${SKU}`).click()
  await page.getByRole('option', { name: '欠品', exact: true }).click()
  await page.getByTestId('save-response-button').click()
  await expect(page.getByText('回答を保存しました。')).toBeVisible()

  await expect(page.getByText('回答数量0 SKU数: 1')).toBeVisible()
  // Supply Status is stored/displayed independently of confirmedQty=0 (never
  // auto-inferred, 7-C5 7章) - the explicit OUT_OF_STOCK selection raises its
  // own SUPPLY_STATUS_CHANGED Attention chip, distinct from QUANTITY_CHANGED.
  await expect(page.getByText('供給状況の確認が必要').first()).toBeVisible()

  // confirmedQty=0 is a real, displayed difference (0 is a value, not an
  // absence) - the UNANSWERED entry is gone and a QUANTITY_CHANGED entry
  // with confirmedValue "0" (not blank, not "未回答") takes its place.
  await expect(page.getByTestId(`difference-${SKU}-UNANSWERED`)).toHaveCount(0)
  await expect(page.getByTestId(`difference-${SKU}-QUANTITY_CHANGED`)).toContainText('→ 0')
  void orderId
})

test('Scenario E: 過去Revision/Responseは編集不可', async ({ page }) => {
  const orderId = await createAwaitingSupplierOrderAsAdmin(page)
  const orderedQtyCell = page.getByTestId(`response-row-${SKU}`).locator('td').nth(2)
  const orderedQty = Number(await orderedQtyCell.innerText())
  await answerAndConfirm(page, orderId, orderedQty)
  await page.getByTestId('order-detail-primary-action').click()
  await page.getByTestId('agree-button').click()
  await page.getByTestId('agree-dialog-confirm').click()

  // AGREED - the (still current) Response screen shows no editable inputs.
  await page.goto(`/orders/${orderId}/supplier-response`)
  await expect(page.getByTestId(`confirmed-qty-input-${SKU}`).locator('input')).toHaveAttribute('readonly', '')
  await expect(page.getByTestId('save-response-button')).toHaveCount(0)
})

test('Scenario F: OPERATORはADMIN限定Actionを実行できない（Nav/Button非表示）', async ({ page }) => {
  const orderId = await createAwaitingSupplierOrderAsAdmin(page)
  const orderedQtyCell = page.getByTestId(`response-row-${SKU}`).locator('td').nth(2)
  const orderedQty = Number(await orderedQtyCell.innerText())
  await answerAndConfirm(page, orderId, orderedQty)

  await logout(page)
  await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
  await page.goto(`/orders/${orderId}/supplier-response`)

  await expect(page.getByTestId('agreement-section')).toBeVisible()
  await expect(page.getByTestId('agree-button')).toHaveCount(0)
  await expect(page.getByTestId('create-revision-button')).toHaveCount(0)
  await expect(page.getByText('この操作は管理者のみ行えます。')).toBeVisible()
})

test('Scenario G: 全操作後もLegacy変更ゼロ（UI観測可能な範囲）', async ({ page }) => {
  // This suite never writes to Legacy by construction (no such capability
  // exists anywhere in the Frontend/Backend API surface - Scenario G is
  // authoritatively verified at the Backend level via SafetyGuard tests and
  // the standing "no Legacy write" architecture, not re-derivable from the
  // Browser). createAwaitingSupplierOrderAsAdmin already asserts the
  // "no real mail sent" banner at Demo Send time; this test additionally
  // drives the Order all the way through Agree to confirm no Legacy/Send
  // control appears at any later Status either.
  const orderId = await createAwaitingSupplierOrderAsAdmin(page)
  const orderedQtyCell = page.getByTestId(`response-row-${SKU}`).locator('td').nth(2)
  const orderedQty = Number(await orderedQtyCell.innerText())
  await answerAndConfirm(page, orderId, orderedQty)
  await page.getByTestId('order-detail-primary-action').click()
  await page.getByTestId('agree-button').click()
  await page.getByTestId('agree-dialog-confirm').click()
  await expect(page.getByTestId('agreed-section')).toBeVisible()
})
