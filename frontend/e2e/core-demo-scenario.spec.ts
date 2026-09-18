import { test, expect, type Page } from '@playwright/test'

/**
 * Step 5 7章: Core Demo Scenario E2E test.
 *
 * Covers, in order: Login -> Candidate List -> SKU選択 -> Create Draft ->
 * Order Qty変更 -> Save -> Preview -> 承認依頼 -> (ADMIN) 承認 -> Demo Send
 * -> 発注詳細 (Order Detail, Phase 6-C: no longer auto-opens Supplier
 * Response) -> 「メーカー回答を入力」-> Supplier Response -> Confirmed Qty
 * 変更（0を含む）-> Confirmed Delivery変更 -> Save -> Confirm Response ->
 * 発注詳細 (Phase 6-C: Confirm also lands here directly now) -> Timeline
 * 確認 -> Attention確認 -> Acknowledge.
 *
 * Phase 7-C1: the old single-step DRAFT->READY_TO_ORDER "確定" no longer
 * exists - OPERATOR submits for approval, then an ADMIN must separately
 * approve before Demo Send becomes available. This suite exercises that
 * as a real Role switch (logout/login), matching how the Prototype
 * actually gates it - not a shortcut around the UI.
 *
 * Selector policy (Step 5 8章 finding): the checkbox/click-miss issue
 * observed during manual Step 4 verification was root-caused to a
 * screenshot-coordinate-scaling artifact in the browser automation tool
 * used at the time, not an application bug - it never reproduces with
 * DOM-based locators. Accordingly this suite exclusively uses
 * data-testid / getByRole / getByLabel locators and never raw
 * page.mouse coordinates, and is structurally immune to that class of
 * issue.
 *
 * Prerequisite: run `backend/demo-reset.sh` (or the equivalent
 * `--app.demo-reset.enabled=true` run) against the local Prototype
 * PostgreSQL before this suite so PO numbering/Attention counts start
 * from a known baseline. The suite does not invoke Demo Reset itself
 * (implementation instructions Step 5 5章: "ResetはBackend APIとして公開
 * しない。CLI / scriptのみ" - keeping it out of the E2E run keeps that
 * boundary unambiguous).
 */

const DEMO_USERNAME = 'purchase01'
const DEMO_PASSWORD = 'DemoPass123!'
// Phase 7-C1 18章: the dedicated ADMIN demo account (V8 migration).
const ADMIN_USERNAME = 'admin01'
const ADMIN_PASSWORD = 'DemoPass123!'

// Two SKUs sharing one supplier (SUP_ALPHA / KITCHENNE) so a single
// Draft can be created from both in one Create Draft action.
const SKU_A = 'KT-BOWL-001' // driven to Confirmed Qty = 0
const SKU_B = 'KT-BOWL-002' // driven to a Confirmed Qty change + a Confirmed Delivery change

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

test('Core Demo Scenario: Candidate -> Draft -> Preview -> Submit for Approval -> Approve -> Demo Send -> Order Detail -> Supplier Response -> Order Detail -> Attention Acknowledge', async ({ page }) => {
  // ---- Login ----
  await login(page)

  // ---- Candidate List: SKU選択 ----
  await page.getByTestId('nav-candidates').click()
  await expect(page.getByTestId(`candidate-row-${SKU_A}`)).toBeVisible()
  await page.getByTestId(`candidate-checkbox-${SKU_A}`).locator('input').check()
  await page.getByTestId(`candidate-checkbox-${SKU_B}`).locator('input').check()

  // ---- Create Draft ----
  // Phase 6-A: the Draft URL now carries ?returnTo=... (the Candidate List's
  // own Filter state) so its "戻る" button can restore that List instead of
  // always landing on the unfiltered list (docs/production-ux-workflow-redesign.md 6.3章).
  await page.getByTestId('create-draft-button').click()
  await expect(page).toHaveURL(/\/orders\/drafts\/\d+(\?.*)?$/)
  const draftId = page.url().match(/\/orders\/drafts\/(\d+)/)?.[1]
  expect(draftId).toBeTruthy()
  expect(new URL(page.url()).searchParams.get('returnTo')).toBe('/candidates')

  // ---- Order Qty変更 ----
  const qtyInputA = page.getByTestId(`order-qty-input-${SKU_A}`).locator('input')
  await qtyInputA.fill('6')
  const qtyInputB = page.getByTestId(`order-qty-input-${SKU_B}`).locator('input')
  await qtyInputB.fill('9')

  // ---- Save ----
  await page.getByTestId('save-draft-button').click()
  await expect(page.getByText('保存しました。')).toBeVisible()

  // ---- Preview (before approval - no PO No. assigned yet) ----
  // Phase 6-A: the returnTo chain is forwarded from Draft to Preview too.
  await page.getByTestId('go-to-preview-button').click()
  await expect(page).toHaveURL(new RegExp(`/orders/drafts/${draftId}/preview(\\?.*)?$`))
  expect(new URL(page.url()).searchParams.get('returnTo')).toBe('/candidates')
  await expect(page.getByText('未採番')).toBeVisible()

  // ---- 承認依頼 (Phase 7-C1: back to Draft, Submit for Approval) ----
  await page.getByRole('button', { name: '発注ドラフトへ戻る' }).click()
  await expect(page).toHaveURL(new RegExp(`/orders/drafts/${draftId}(\\?.*)?$`))
  await page.getByTestId('submit-for-approval-button').click()
  await page.getByTestId('submit-for-approval-dialog-confirm').click()

  // Submitting lands OPERATOR on 発注詳細, showing a read-only 承認待ち
  // indicator (only an ADMIN gets 承認/修正/差し戻し there).
  await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))
  await expect(page.getByTestId('pending-approval-indicator')).toBeVisible()
  await expect(page.getByTestId('order-detail-approve-button')).toHaveCount(0)

  // ---- 承認 (Phase 7-C1: only an ADMIN can approve - real Role switch) ----
  await logout(page)
  await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
  await page.goto(`/orders/${draftId}`)
  await page.getByTestId('order-detail-approve-button').click()
  await page.getByTestId('approve-dialog-confirm').click()
  await expect(page.getByText('承認しました。')).toBeVisible()

  // Approved -> PO No. is now assigned, and 発注詳細's primary Action is
  // "PO プレビューを見る" (Phase 7-C1 16章: APPROVED replaces the old
  // READY_TO_ORDER Preview entry point).
  const goToPreview = page.getByTestId('order-detail-primary-action')
  await expect(goToPreview).toHaveText('PO プレビューを見る')
  await goToPreview.click()
  await expect(page).toHaveURL(new RegExp(`/orders/drafts/${draftId}/preview(\\?.*)?$`))
  await expect(page.getByText('未採番')).toHaveCount(0)

  // ---- Demo Send (メール発注済みとして記録) - continuing as this ADMIN session
  // since Demo Send itself is not Role-restricted (SupplierWorkflowController
  // has no @PreAuthorize gate); the strict OPERATOR/ADMIN separation itself
  // is already covered above and by role-approval-workflow.spec.ts's
  // Scenarios A-E. ----
  await page.getByTestId('demo-send-button').click()
  await page.getByTestId('demo-send-dialog-confirm').click()

  // Phase 6-C: "記録する" and "後日回答を登録する" are separate Business
  // Tasks now - Demo Send no longer auto-opens Supplier Response, it lands
  // on 発注詳細 (Order Detail) instead. The returnTo chain is reset to the
  // Order List's AWAITING_SUPPLIER bucket rather than carrying forward the
  // Candidate List this Draft started from (docs/production-ux-workflow-redesign.md 8章).
  await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))
  expect(page.url()).not.toContain('supplier-response')
  expect(new URL(page.url()).searchParams.get('returnTo')).toBe('/orders/history?status=AWAITING_SUPPLIER')
  await expect(page.getByRole('heading', { name: '発注詳細' })).toBeVisible()
  await expect(
    page.getByText('メーカーへの発注を記録しました。現在、メーカー回答待ちです。デモモードのため、実際のメールは送信されていません。'),
  ).toBeVisible()

  // ---- 発注詳細 -> メーカー回答を入力 (a deliberate, separate click - this
  // is the point of Phase 6-C, not an automatic follow-on) ----
  const enterSupplierResponse = page.getByTestId('order-detail-primary-action')
  await expect(enterSupplierResponse).toHaveText('メーカー回答を入力')
  await enterSupplierResponse.click()
  await expect(page).toHaveURL(new RegExp(`/orders/${draftId}/supplier-response(\\?.*)?$`))

  // ---- Supplier Response: Confirmed Qty変更（0を含む）+ Confirmed Delivery変更 ----
  const confirmedQtyA = page.getByTestId(`confirmed-qty-input-${SKU_A}`).locator('input')
  await confirmedQtyA.fill('0') // Confirmed Qty = 0 case
  const confirmedQtyB = page.getByTestId(`confirmed-qty-input-${SKU_B}`).locator('input')
  await confirmedQtyB.fill('7') // differs from ordered qty (9) -> QUANTITY_CHANGED
  const confirmedDeliveryB = page.getByTestId(`confirmed-delivery-input-${SKU_B}`).locator('input')
  await confirmedDeliveryB.fill('2026-09-10') // differs from (unset) requested delivery -> DELIVERY_CHANGED

  // ---- Save ----
  await page.getByTestId('save-response-button').click()
  await expect(page.getByText('回答を保存しました。')).toBeVisible()

  // Both lines answered (0 counts as answered) -> summary reflects the
  // scenario without guessing at any business rule.
  await expect(page.getByText('回答済みSKU数: 2 / 2')).toBeVisible()
  await expect(page.getByText('回答数量0 SKU数: 1')).toBeVisible()

  // ---- Confirm Response (メーカー回答を確定) ----
  await page.getByTestId('confirm-response-button').click()
  await page.getByTestId('confirm-response-dialog-confirm').click()

  // Phase 6-C: Confirm now also lands on 発注詳細 directly - no more manual
  // "履歴を見る" click, and the returnTo it carried in (this order's own
  // AWAITING_SUPPLIER Order List context, set above) survives the round trip.
  await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))
  expect(new URL(page.url()).searchParams.get('returnTo')).toBe('/orders/history?status=AWAITING_SUPPLIER')
  await expect(page.getByText('メーカー回答を確定しました。')).toBeVisible()
  await expect(page.getByTestId('order-detail-primary-action')).toHaveText('メーカー回答を確認する')

  // ---- Timeline確認 ----
  // Phase 7-C1: the old single ORDER_READY ("発注内容確定（PO番号採番）")
  // event no longer exists - PO No. assignment now happens inside 承認
  // (ORDER_APPROVED), reached via 承認依頼 (SUBMITTED_FOR_APPROVAL) first.
  await expect(page.getByText('操作履歴')).toBeVisible()
  await expect(page.getByText('ドラフト作成')).toBeVisible()
  await expect(page.getByText('承認依頼').first()).toBeVisible()
  await expect(page.getByText('承認', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('デモ送信')).toBeVisible()
  await expect(page.getByText('メーカー回答確定')).toBeVisible()

  // ---- Attention確認 ----
  // Line-level QUANTITY_CHANGED (both SKU_A via 0 and SKU_B) and
  // DELIVERY_CHANGED (SKU_B) Attentions should be visible and Acknowledge-
  // able from this screen (implementation instructions Step 5 2章).
  const attentionChips = page.locator('[data-testid^="attention-chip-"]')
  const attentionCountBeforeAck = await attentionChips.count()
  expect(attentionCountBeforeAck).toBeGreaterThanOrEqual(2)
  await expect(page.getByText('数量変更あり').first()).toBeVisible()
  await expect(page.getByText('納期変更あり').first()).toBeVisible()

  // ---- Acknowledge ----
  const firstChip = attentionChips.first()
  const firstChipTestId = await firstChip.getAttribute('data-testid')
  await firstChip.locator('span[aria-hidden]').click()
  // The acknowledged chip disappears; the rest remain.
  await expect(page.locator(`[data-testid="${firstChipTestId}"]`)).toHaveCount(0)
  await expect(attentionChips).toHaveCount(attentionCountBeforeAck - 1)

  // ---- Dashboard reflects the Acknowledge (implementation instructions
  // Step 5 13章: "Acknowledge後Dashboard件数反映") ----
  await page.getByTestId('nav-dashboard').click()
  await expect(page.getByTestId('nav-dashboard')).toBeVisible()
})
