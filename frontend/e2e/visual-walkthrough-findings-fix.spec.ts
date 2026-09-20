import { test, expect, type Page } from '@playwright/test'

/**
 * G-OPS Post-Freeze Visual Walkthrough Findings Fix
 * (docs/gops-visual-walkthrough-findings-fix.md).
 *
 * Desktop-viewport regression coverage for the 7 Findings fixed in this
 * pass, for the Areas NOT already exercised by an existing suite:
 *
 *   Area B (Finding #2): Official PO Reissue keeps the SAME 正式PO番号 and
 *   only increments Revision (1 -> 2); the "修正版を作成" guidance surfaces
 *   automatically once a Manufacturer-confirmed Qty differs from ordered.
 *   Area C (Finding #2): a confirmed Qty change makes internal Reapproval
 *   (re-submit -> re-approve) mandatory before Reissue becomes available -
 *   this is the same underlying mechanism supplier-response-revision-
 *   workflow.spec.ts's Scenario C already exercises; this file adds the
 *   Reissue step on top of it and the explicit guidance-hint assertion.
 *   Area D (Finding #4): Cancellation's final Primary Business Status
 *   reads キャンセル済み at Order Detail top AND in Order History - not
 *   still 承認済み - on Desktop (mobile-responsive.spec.ts's M3 covers the
 *   same Finding at 390x844).
 *   Area G (Finding #3): the Supplier Response Save button stays reachable
 *   and clickable despite the unsaved-changes Warning, on Desktop
 *   (mobile-responsive.spec.ts's M8 covers the same Finding on Mobile).
 *
 * Same login/logout Role-switch pattern and selector policy (data-testid /
 * getByRole / getByLabel only) as core-demo-scenario.spec.ts. Uses
 * OD-TENT-002 (SUP_ALPHA/BR_OUTDOOR, recommendedQty 20, unitPrice 1274 per
 * PoPreviewServiceIntegrationTest's own fixture comment) - a distinct Draft/
 * Order per test run, so this suite's own Order lifecycle never collides
 * with another spec's, even though other specs reference the same
 * read-only catalog SKU.
 */

const OPERATOR_USERNAME = 'purchase01'
const OPERATOR_PASSWORD = 'DemoPass123!'
const ADMIN_USERNAME = 'admin01'
const ADMIN_PASSWORD = 'DemoPass123!'

const SKU = 'OD-TENT-001'
const BRAND = 'BR_OUTDOOR'

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

/** Creates a fresh Draft for SKU with an explicit Order Qty, submits it, and
 * approves it as ADMIN - ending on Order Detail with Status APPROVED.
 * Returns the Order id. */
async function createApprovedOrder(page: Page, orderQty: number): Promise<string> {
  await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
  await page.getByTestId('nav-candidates').click()
  await page.getByTestId(`order-candidate-brand-link-${BRAND}`).click()
  await expect(page.getByTestId(`candidate-row-${SKU}`)).toBeVisible()
  await page.getByTestId(`candidate-checkbox-${SKU}`).locator('input').check()
  await page.getByTestId('create-draft-button').click()
  await expect(page).toHaveURL(/\/orders\/drafts\/\d+(\?.*)?$/)
  const orderId = page.url().match(/\/orders\/drafts\/(\d+)/)?.[1]
  if (!orderId) throw new Error('draft id not found in URL')

  await page.getByTestId(`order-qty-input-${SKU}`).locator('input').fill(String(orderQty))
  await page.getByTestId('save-draft-button').click()
  await expect(page.getByText('保存しました。')).toBeVisible()
  await page.getByTestId('submit-for-approval-button').click()
  await page.getByTestId('submit-for-approval-dialog-confirm').click()
  await expect(page).toHaveURL(new RegExp(`/orders/${orderId}(\\?.*)?$`))

  await logout(page)
  await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
  await page.goto(`/orders/${orderId}`)
  await page.getByTestId('order-detail-approve-button').click()
  await page.getByTestId('approve-dialog-confirm').click()
  await expect(page.getByText('承認しました。')).toBeVisible()
  return orderId
}

/** From an already-APPROVED Order Detail page (ADMIN session), goes through
 * PO Preview and Demo Send - ending back on Order Detail with Status
 * AWAITING_SUPPLIER. Matches supplier-response-revision-workflow.spec.ts's
 * own established Demo Send sequence. */
async function demoSend(page: Page, orderId: string) {
  const goToPreview = page.getByTestId('order-detail-primary-action')
  await expect(goToPreview).toHaveText('PO プレビューを見る')
  await goToPreview.click()
  await expect(page).toHaveURL(new RegExp(`/orders/drafts/${orderId}/preview(\\?.*)?$`))
  await page.getByTestId('demo-send-button').click()
  await page.getByTestId('demo-send-dialog-confirm').click()
  await expect(page).toHaveURL(new RegExp(`/orders/${orderId}(\\?.*)?$`))
}

test.describe('Areas B/C (Finding #2): Reissue keeps the same 正式PO番号, increments Revision', () => {
  test('Qty100 -> Manufacturer confirms 80 -> guidance hint -> 修正版作成 -> Reapproval -> Reissue -> Rev.2 same PO No.', async ({ page }) => {
    const orderId = await createApprovedOrder(page, 100)

    // Official PO Rev.1: request Integration and generate Excel on the
    // ORIGINAL (Qty100) Revision, before any correction exists and before
    // Demo Send (matches official-po-integration.spec.ts's own established
    // Request-right-after-Approve sequence).
    await page.getByTestId('official-po-request-button').click()
    await page.getByTestId('official-po-request-dialog-confirm').click()
    await expect(page.getByText('G-SYS連携の準備が完了しました。')).toBeVisible()
    await expect(page.getByTestId('official-po-no')).toHaveText(/^[A-Z]{3}[A-Z]{3}\d{3}$/)
    const rev1PoNo = await page.getByTestId('official-po-no').innerText()
    await page.getByTestId('official-po-generate-button').click()
    await expect(page.getByText('正式PO Excelを生成しました。')).toBeVisible()

    await demoSend(page, orderId)

    // Manufacturer answers 80 (shortage of 20 against the ordered 100).
    const enterSupplierResponse = page.getByTestId('order-detail-primary-action')
    await expect(enterSupplierResponse).toHaveText('メーカー回答を入力')
    await enterSupplierResponse.click()
    await expect(page).toHaveURL(new RegExp(`/orders/${orderId}/supplier-response(\\?.*)?$`))
    await page.getByTestId(`confirmed-qty-input-${SKU}`).locator('input').fill('80')
    await page.getByTestId('save-response-button').click()
    await expect(page.getByText('回答を保存しました。')).toBeVisible()
    await page.getByTestId('confirm-response-button').click()
    await page.getByTestId('confirm-response-dialog-confirm').click()
    await expect(page).toHaveURL(new RegExp(`/orders/${orderId}(\\?.*)?$`))
    await expect(page.getByText('メーカー回答を確定しました。')).toBeVisible()

    // Finding #2: a QUANTITY_CHANGED Attention on a SUPPLIER_CONFIRMED
    // Order must surface guidance pointing at 修正版を作成, instead of
    // leaving the correct next Action undiscoverable.
    await expect(page.getByTestId('next-action-hint')).toBeVisible()

    // Create the correction (修正版を作成), applying the confirmed value.
    await page.getByTestId('order-detail-primary-action').click()
    await expect(page).toHaveURL(new RegExp(`/orders/${orderId}/supplier-response(\\?.*)?$`))
    await page.getByTestId('create-revision-button').click()
    await page.getByTestId('revision-reason-input').locator('textarea').first().fill('メーカー在庫不足のため数量を80に修正')
    await page.getByTestId('apply-confirmed-values-checkbox').check()
    await page.getByTestId('revision-dialog-confirm').click()

    // Back to an editable Draft with 80 already applied - internal
    // Reapproval Required (Area C): Submit -> Approve again from scratch.
    await expect(page).toHaveURL(new RegExp(`/orders/drafts/${orderId}(\\?.*)?$`))
    await expect(page.getByTestId(`order-qty-input-${SKU}`).locator('input')).toHaveValue('80')
    await page.getByTestId('submit-for-approval-button').click()
    await page.getByTestId('submit-for-approval-dialog-confirm').click()
    await logout(page)
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto(`/orders/${orderId}`)
    await page.getByTestId('order-detail-approve-button').click()
    await page.getByTestId('approve-dialog-confirm').click()
    await expect(page.getByText('承認しました。')).toBeVisible()

    // Reissue is now required and available (isReissueRequired: the
    // current ACTIVE request was already issued AND an
    // ORDER_REVISION_CREATED Audit Event exists after that issuance) - like
    // the original Request, Reissue requires the Order to still be APPROVED
    // (OfficialPoIntegrationService's own ORDER_NOT_APPROVED Gate), so it
    // happens here, BEFORE the second Demo Send, not after.
    await expect(page.getByTestId('reissue-required-banner')).toBeVisible()
    await expect(page.getByTestId('official-po-reissue-button')).toBeEnabled()
    await page.getByTestId('official-po-reissue-button').click()
    await page.getByTestId('official-po-reissue-dialog-confirm').click()

    // Rev.2: the SAME Formal PO Number, Revision only increments 1 -> 2.
    await expect(page.getByTestId('official-po-no')).toHaveText(rev1PoNo)
    await expect(page.getByTestId('revision-history-row-1')).toContainText(rev1PoNo)
    await expect(page.getByTestId('revision-history-row-2')).toContainText(rev1PoNo)
    await expect(page.getByTestId('revision-history-row-1')).toBeVisible()
    await expect(page.getByTestId('revision-history-row-2')).toBeVisible()

    // Rev.2 PDF/Excel must be regenerable from the reissued (ACTIVE) request.
    await page.getByTestId('official-po-generate-button').click()
    await expect(page.getByText('正式PO Excelを生成しました。')).toBeVisible()
    await page.getByTestId('official-po-pdf-generate-button').click()
    await expect(page.getByText('正式PO PDFを生成しました。')).toBeVisible()
  })
})

test.describe('Area D (Finding #4): Cancellation final Primary Status is キャンセル済み (Desktop)', () => {
  test('Request -> Approval -> Manufacturer Notification -> CANCELLED shows キャンセル済み, not 承認済み', async ({ page }) => {
    const orderId = await createApprovedOrder(page, 5)

    await page.getByTestId('official-po-request-button').click()
    await page.getByTestId('official-po-request-dialog-confirm').click()
    await expect(page.getByText('G-SYS連携の準備が完了しました。')).toBeVisible()
    await page.getByTestId('official-po-generate-button').click()
    await expect(page.getByText('正式PO Excelを生成しました。')).toBeVisible()

    await page.getByTestId('official-po-cancel-button').click()
    await page.getByTestId('official-po-cancel-reason-input').locator('textarea').first().fill('Desktop Finding #4のテストキャンセル')
    await page.getByTestId('official-po-cancel-dialog-confirm').click()
    await expect(page.getByTestId('official-po-cancel-requested-note')).toBeVisible()

    await page.getByTestId('official-po-cancel-approve-button').click()
    await expect(page.getByTestId('official-po-cancelled-note')).toBeVisible()

    // `PortalOrder.status` intentionally stays APPROVED (Reissue
    // eligibility/history preservation) - only the DISPLAYED Primary Status
    // is corrected: Order Detail top must show キャンセル済み, not 承認済み.
    await expect(page.getByTestId('order-status-chip-cancelled')).toBeVisible()
    await expect(page.getByTestId('order-status-chip-cancelled')).toHaveText('キャンセル済み')
    // History (e.g. an Audit Timeline entry reading "承認済み -> ...") may
    // still mention the past status - only the Chip's own Primary Status
    // text must never be 承認済み, which toHaveText('キャンセル済み') above
    // already guarantees for both header Chip instances (mobile/desktop
    // Stacks render the SAME OrderStatusChip, so any other Chip on this
    // page unrelated to Primary Status uses a different component/testid).

    // Order History must show the same corrected Primary Status - never
    // contradicting Order Detail for the same Order.
    await page.goto('/orders/history')
    await page.getByTestId('order-history-filter-brand').locator('input').fill(BRAND)
    await page.getByTestId('order-history-filter-brand').locator('input').blur()
    const historyRow = page.getByTestId(`order-history-row-${orderId}`)
    await expect(historyRow).toBeVisible()
    await expect(historyRow.getByTestId('order-status-chip-cancelled')).toHaveText('キャンセル済み')
  })
})

test.describe('Area G (Finding #3): Supplier Response Save button reachable despite the unsaved-changes Warning (Desktop)', () => {
  test('Save button stays visible and clickable while the Warning is shown', async ({ page }) => {
    const orderId = await createApprovedOrder(page, 6)
    await demoSend(page, orderId)

    const enterSupplierResponse = page.getByTestId('order-detail-primary-action')
    await expect(enterSupplierResponse).toHaveText('メーカー回答を入力')
    await enterSupplierResponse.click()
    await expect(page).toHaveURL(new RegExp(`/orders/${orderId}/supplier-response(\\?.*)?$`))

    await page.getByTestId(`confirmed-qty-input-${SKU}`).locator('input').fill('5')
    await expect(page.getByTestId('response-unsaved-changes-toast')).toBeVisible()

    // Finding #3: scroll all the way to the bottom of the page - exactly
    // where the fixed-position Warning Toast used to sit directly on top of
    // the in-flow Save button (§22-safe fix: reserved bottom padding, not a
    // z-index-only patch) - and confirm the Save button is still reachable
    // and clickable there, not covered by the still-visible Warning.
    const saveButton = page.getByTestId('save-response-button')
    await saveButton.scrollIntoViewIfNeeded()
    await page.mouse.wheel(0, 2000)
    await expect(page.getByTestId('response-unsaved-changes-toast')).toBeVisible()
    await expect(saveButton).toBeInViewport()
    await saveButton.click({ trial: true }) // throws if another element (the Warning) would intercept the click
    await saveButton.click()
    await expect(page.getByText('回答を保存しました。')).toBeVisible()
  })
})
