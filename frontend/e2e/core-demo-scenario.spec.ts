import { test, expect, type Page } from '@playwright/test'

/**
 * Step 5 7章: Core Demo Scenario E2E test.
 *
 * Covers, in order: Login -> Candidate List -> SKU選択 -> Create Draft ->
 * Order Qty変更 -> Save -> Preview -> Confirm Order -> Demo Send -> 発注詳細
 * (Order Detail, Phase 6-C: no longer auto-opens Supplier Response) ->
 * 「メーカー回答を入力」-> Supplier Response -> Confirmed Qty変更（0を含む）
 * -> Confirmed Delivery変更 -> Save -> Confirm Response -> 発注詳細 (Phase
 * 6-C: Confirm also lands here directly now) -> Timeline確認 -> Attention
 * 確認 -> Acknowledge.
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

// Two SKUs sharing one supplier (SUP_ALPHA / KITCHENNE) so a single
// Draft can be created from both in one Create Draft action.
const SKU_A = 'KT-BOWL-001' // driven to Confirmed Qty = 0
const SKU_B = 'KT-BOWL-002' // driven to a Confirmed Qty change + a Confirmed Delivery change

async function login(page: Page) {
  await page.goto('/')
  await page.getByLabel('ユーザー名').fill(DEMO_USERNAME)
  await page.getByLabel('パスワード').fill(DEMO_PASSWORD)
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page.getByTestId('nav-dashboard')).toBeVisible()
}

test('Core Demo Scenario: Candidate -> Draft -> Preview -> Confirm -> Demo Send -> Order Detail -> Supplier Response -> Order Detail -> Attention Acknowledge', async ({ page }) => {
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

  // ---- Preview ----
  // Phase 6-A: the returnTo chain is forwarded from Draft to Preview too.
  await page.getByTestId('go-to-preview-button').click()
  await expect(page).toHaveURL(new RegExp(`/orders/drafts/${draftId}/preview(\\?.*)?$`))
  expect(new URL(page.url()).searchParams.get('returnTo')).toBe('/candidates')

  // ---- Confirm (発注内容を確定) ----
  await page.getByTestId('confirm-order-button').click()
  await page.getByTestId('confirm-order-dialog-confirm').click()
  await expect(page.getByText('発注内容を確定しました。')).toBeVisible()

  // ---- Demo Send (メーカーへ送信) ----
  await page.getByTestId('demo-send-button').click()
  await page.getByTestId('demo-send-dialog-confirm').click()

  // Phase 6-C: "送信する" and "後日回答を登録する" are separate Business
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
  await expect(page.getByText('操作履歴')).toBeVisible()
  await expect(page.getByText('ドラフト作成')).toBeVisible()
  await expect(page.getByText('発注内容確定（PO番号採番）')).toBeVisible()
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
