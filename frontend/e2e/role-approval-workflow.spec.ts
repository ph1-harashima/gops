import { test, expect, type Page } from '@playwright/test'

/**
 * Phase 7-C1 25章: the five Browser Acceptance Scenarios (A-E) for the
 * Role / Approval Workflow, run as literally specified:
 *
 *   A. OPERATOR login -> Candidate -> Draft -> 承認依頼 -> 承認待ち
 *   B. ADMIN login -> 承認待ち -> Order Detail -> 承認 -> APPROVED
 *   C. OPERATOR Draft->承認依頼; ADMIN 修正->承認; Audit shows the change
 *   D. OPERATOR Draft->承認依頼; ADMIN 差し戻し+理由; OPERATOR sees the
 *      reason on Draft, edits, resubmits
 *   E. OPERATOR directly calls the Approve API -> rejected (403)
 *
 * Complements core-demo-scenario.spec.ts (which exercises the whole
 * Candidate->...->Attention pipeline once, including one submit/approve
 * round trip) by covering the Role/Approval Workflow's own branches in
 * isolation - edit-and-approve, return-for-correction, and the direct-API
 * 403 rejection - none of which the core scenario touches.
 *
 * Prerequisite: same as core-demo-scenario.spec.ts - Demo Reset run first
 * against the local Prototype PostgreSQL.
 */

const OPERATOR_USERNAME = 'purchase01'
const OPERATOR_PASSWORD = 'DemoPass123!'
const ADMIN_USERNAME = 'admin01'
const ADMIN_PASSWORD = 'DemoPass123!'

// Any single SKU is enough for these Scenarios - none of them exercise
// multi-line Attention/Supplier Response behavior (core-demo-scenario.spec.ts
// already covers that).
const SKU = 'KT-BOWL-001'

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

/** OPERATOR-side setup shared by every Scenario: login, create a fresh
 * Draft, Save (recommendedQty=0 SKUs default orderQty to 0 - bump it so the
 * Draft is orderable), return the Draft id. Leaves the caller logged in as
 * OPERATOR. */
async function createOrderableDraft(page: Page): Promise<string> {
  await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
  await page.getByTestId('nav-candidates').click()
  await expect(page.getByTestId(`candidate-row-${SKU}`)).toBeVisible()
  await page.getByTestId(`candidate-checkbox-${SKU}`).locator('input').check()
  await page.getByTestId('create-draft-button').click()
  await expect(page).toHaveURL(/\/orders\/drafts\/\d+(\?.*)?$/)
  const draftId = page.url().match(/\/orders\/drafts\/(\d+)/)?.[1]
  expect(draftId).toBeTruthy()

  // 6 is the same known-nonzero, known-dirty-triggering value
  // core-demo-scenario.spec.ts already uses for this SKU.
  const qtyInput = page.getByTestId(`order-qty-input-${SKU}`).locator('input')
  await qtyInput.fill('6')
  await page.getByTestId('save-draft-button').click()
  await expect(page.getByText('保存しました。')).toBeVisible()

  return draftId!
}

async function submitForApproval(page: Page, draftId: string) {
  await page.goto(`/orders/drafts/${draftId}`)
  await page.getByTestId('submit-for-approval-button').click()
  await page.getByTestId('submit-for-approval-dialog-confirm').click()
  await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))
}

test.describe('Phase 7-C1: Role / Approval Workflow', () => {
  test('Scenario A: OPERATOR - Candidate -> Draft -> 承認依頼 -> 承認待ち', async ({ page }) => {
    const draftId = await createOrderableDraft(page)

    await page.getByTestId('go-to-preview-button').click()
    await expect(page.getByTestId('edit-order-button')).toBeVisible()
    await expect(page.getByTestId('demo-send-button')).toHaveCount(0)
    await page.getByRole('button', { name: '発注ドラフトへ戻る' }).click()

    await submitForApproval(page, draftId)

    await expect(page.getByTestId('pending-approval-indicator')).toBeVisible()
    await expect(page.getByText('承認待ち').first()).toBeVisible()
    // OPERATOR sees no ADMIN Actions on the now-PENDING_APPROVAL Order.
    await expect(page.getByTestId('order-detail-approve-button')).toHaveCount(0)
    await expect(page.getByTestId('order-detail-return-button')).toHaveCount(0)

    // The Draft screen itself is now read-only for this OPERATOR too
    // (OrderDraftPersistenceService.update()'s PENDING_APPROVAL=ADMIN-only rule).
    await page.goto(`/orders/drafts/${draftId}`)
    await expect(page.getByTestId('save-draft-button')).toHaveCount(0)
    await expect(page.getByTestId(`order-qty-input-${SKU}`).locator('input')).toHaveAttribute('readonly', '')
  })

  test('Scenario B: ADMIN - 承認待ち -> Order Detail -> 承認 -> APPROVED', async ({ page }) => {
    const draftId = await createOrderableDraft(page)
    await submitForApproval(page, draftId)
    await logout(page)

    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto('/')
    // Dashboard's 承認待ち KPI Deep Link (Phase 7-C1 14章).
    const pendingApprovalTile = page.locator('.MuiPaper-root').filter({ has: page.locator('h4') })
      .filter({ has: page.getByText('承認待ち', { exact: true }) })
    await expect(pendingApprovalTile).toBeVisible()
    await pendingApprovalTile.click()
    await expect(page).toHaveURL(/\/orders\/history\?status=PENDING_APPROVAL/)

    await page.locator('table tbody tr').first().click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}`))

    await page.getByTestId('order-detail-approve-button').click()
    await page.getByTestId('approve-dialog-confirm').click()
    await expect(page.getByText('承認しました。')).toBeVisible()
    await expect(page.getByTestId('order-detail-primary-action')).toHaveText('PO Previewを見る')
  })

  test('Scenario C: ADMIN 修正 -> 承認 records the change on the Audit Trail (APPROVED_WITH_CHANGES)', async ({ page }) => {
    const draftId = await createOrderableDraft(page)
    await submitForApproval(page, draftId)
    await logout(page)

    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto(`/orders/${draftId}`)
    await page.getByTestId('order-detail-edit-button').click()
    await expect(page).toHaveURL(new RegExp(`/orders/drafts/${draftId}`))

    // ADMIN can edit a PENDING_APPROVAL Draft in place (no Status change).
    const qtyInput = page.getByTestId(`order-qty-input-${SKU}`).locator('input')
    await qtyInput.fill('5')
    await page.getByTestId('save-draft-button').click()
    await expect(page.getByText('保存しました。')).toBeVisible()

    await page.goto(`/orders/${draftId}`)
    await page.getByTestId('order-detail-approve-button').click()
    await page.getByTestId('approve-dialog-confirm').click()
    await expect(page.getByText('承認しました。')).toBeVisible()

    // Audit Trail: the qty change and "修正のうえ承認" (APPROVED_WITH_CHANGES)
    // must both be visible, not a plain "承認" (7-C1 11章). Two
    // 発注数量変更 entries are expected on the Trail (the OPERATOR's
    // original Save, then this ADMIN edit) - .first() is enough to prove
    // at least one is there.
    await expect(page.getByText('発注数量変更').first()).toBeVisible()
    await expect(page.getByText('修正のうえ承認')).toBeVisible()
  })

  test('Scenario D: ADMIN 差し戻し+理由 -> OPERATOR sees the reason, edits, resubmits', async ({ page }) => {
    const draftId = await createOrderableDraft(page)
    await submitForApproval(page, draftId)
    await logout(page)

    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto(`/orders/${draftId}`)
    await page.getByTestId('order-detail-return-button').click()

    // Reason is mandatory - the Confirm button stays disabled until filled.
    await expect(page.getByTestId('return-dialog-confirm')).toBeDisabled()
    await page.getByTestId('return-reason-input').locator('textarea').first().fill('数量を再確認してください')
    await expect(page.getByTestId('return-dialog-confirm')).toBeEnabled()
    await page.getByTestId('return-dialog-confirm').click()
    await expect(page.getByText('差し戻しました。')).toBeVisible()
    await logout(page)

    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.goto(`/orders/drafts/${draftId}`)
    await expect(page.getByTestId('return-reason-banner')).toBeVisible()
    await expect(page.getByTestId('return-reason-banner')).toContainText('数量を再確認してください')

    const qtyInput = page.getByTestId(`order-qty-input-${SKU}`).locator('input')
    await qtyInput.fill('4')
    await page.getByTestId('save-draft-button').click()
    await expect(page.getByText('保存しました。')).toBeVisible()

    await page.getByTestId('submit-for-approval-button').click()
    await page.getByTestId('submit-for-approval-dialog-confirm').click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))
    await expect(page.getByTestId('pending-approval-indicator')).toBeVisible()
  })

  test('Scenario E: OPERATOR calling the Approve API directly is rejected (403)', async ({ page }) => {
    const draftId = await createOrderableDraft(page)
    await submitForApproval(page, draftId)

    // Still logged in as OPERATOR (purchase01) - the same browser context's
    // cookies are reused by page.request, so this exercises the real
    // Session-authenticated OPERATOR, not a synthetic/unauthenticated call.
    const response = await page.request.post(`/api/orders/${draftId}/approve`)
    expect(response.status()).toBe(403)
    const body = await response.json()
    expect(body.errorCode).toBe('FORBIDDEN')

    // Confirm the rejection was not silently ignored - the Order Status
    // truly did not change server-side.
    await page.reload()
    await expect(page.getByTestId('pending-approval-indicator')).toBeVisible()
  })

  test('Phase 7-E Section 2/3 audit: 承認依頼 is blocked while there are unsaved edits (Save-then-Next server-authoritative gating)', async ({ page }) => {
    const draftId = await createOrderableDraft(page)
    await page.goto(`/orders/drafts/${draftId}`)

    // Edit locally WITHOUT saving - without this guard, 承認依頼 would submit
    // the OLD saved orderQty (6), silently ignoring what is now on screen (9).
    const qtyInput = page.getByTestId(`order-qty-input-${SKU}`).locator('input')
    await qtyInput.fill('9')
    await expect(page.getByTestId('submit-for-approval-button')).toBeDisabled()

    // Saving clears the gate again.
    await page.getByTestId('save-draft-button').click()
    await expect(page.getByText('保存しました。')).toBeVisible()
    await expect(page.getByTestId('submit-for-approval-button')).toBeEnabled()
  })
})
