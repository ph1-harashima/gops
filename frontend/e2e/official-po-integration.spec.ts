import { test, expect, type Page } from '@playwright/test'

/**
 * Phase 7-C2A 24章: the five Browser Acceptance Scenarios (A-E) for Official
 * PO Integration Foundation.
 *
 *   A. ADMIN -> APPROVED Order -> Order Detail -> G-SYS連携準備 -> Request作成
 *      -> Preflight結果表示 -> 正式PO番号未設定確認
 *   B. 同一Order/Revisionで二重Request -> 二重作成されない
 *   C. OPERATOR直接API -> 403
 *   D. DRAFT/PENDING_APPROVAL OrderからRequest -> Reject
 *   E. Legacyには何も書き込まれていないことを確認
 *
 * Prerequisite: same as core-demo-scenario.spec.ts / role-approval-workflow.spec.ts -
 * Demo Reset run first against the local Prototype PostgreSQL.
 */

const OPERATOR_USERNAME = 'purchase01'
const OPERATOR_PASSWORD = 'DemoPass123!'
const ADMIN_USERNAME = 'admin01'
const ADMIN_PASSWORD = 'DemoPass123!'

const SKU = 'OD-TENT-001'

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

async function createOrderableDraft(page: Page): Promise<string> {
  await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
  await page.getByTestId('nav-candidates').click()
  await expect(page.getByTestId(`candidate-row-${SKU}`)).toBeVisible()
  await page.getByTestId(`candidate-checkbox-${SKU}`).locator('input').check()
  await page.getByTestId('create-draft-button').click()
  await expect(page).toHaveURL(/\/orders\/drafts\/\d+(\?.*)?$/)
  const draftId = page.url().match(/\/orders\/drafts\/(\d+)/)?.[1]
  expect(draftId).toBeTruthy()

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

async function submitAndApprove(page: Page, draftId: string): Promise<void> {
  await submitForApproval(page, draftId)
  await logout(page)
  await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
  await page.goto(`/orders/${draftId}`)
  await page.getByTestId('order-detail-approve-button').click()
  await page.getByTestId('approve-dialog-confirm').click()
  await expect(page.getByText('承認しました。')).toBeVisible()
}

test.describe('Phase 7-C2A: Official PO Integration Foundation', () => {
  test('Scenario A: ADMIN prepares G-SYS Integration on an APPROVED Order - Request created, Preflight shown, PO No. unassigned', async ({ page }) => {
    const draftId = await createOrderableDraft(page)
    await submitAndApprove(page, draftId)
    // Still logged in as ADMIN from submitAndApprove.

    await expect(page.getByTestId('official-po-integration-section')).toBeVisible()
    await expect(page.getByTestId('official-po-no')).toHaveText('正式PO番号未設定')

    await page.getByTestId('official-po-request-button').click()
    await page.getByTestId('official-po-request-dialog-confirm').click()

    await expect(page.getByText('G-SYS連携の準備が完了しました。')).toBeVisible()
    await expect(page.getByTestId('official-po-no')).toHaveText('正式PO番号未設定')
    await expect(page.getByTestId('official-po-no-unassigned-note')).toBeVisible()
    await expect(page.getByTestId('preflight-result')).toBeVisible()
    // Scoped to the Preflight Section, not the whole page - the Audit
    // Timeline below also renders "PASS" (Phase 7-H: no Arrow, no "—" - a
    // Create-shaped event with only one side of a value, since
    // PRECHECK_COMPLETED has no real "before") for the same
    // PRECHECK_COMPLETED event, which a page-wide getByText('PASS') would
    // collide with.
    await expect(page.getByTestId('preflight-result').getByText('PASS')).toBeVisible()
  })

  test('Scenario B: a second Request on the same Order/Revision does not duplicate', async ({ page }) => {
    const draftId = await createOrderableDraft(page)
    await submitAndApprove(page, draftId)

    await page.getByTestId('official-po-request-button').click()
    await page.getByTestId('official-po-request-dialog-confirm').click()
    await expect(page.getByText('G-SYS連携の準備が完了しました。')).toBeVisible()

    // Second click (double-request) - still succeeds, still Revision 1, no
    // Conflict/duplicate-creation error.
    await page.getByTestId('official-po-request-button').click()
    await page.getByTestId('official-po-request-dialog-confirm').click()
    await expect(page.getByText('G-SYS連携の準備が完了しました。')).toBeVisible()
    await expect(page.getByText('Revision: 1')).toBeVisible()

    // Reload confirms the persisted state is still a single, consistent Request.
    await page.reload()
    await expect(page.getByText('Revision: 1')).toBeVisible()
  })

  test('Scenario C: OPERATOR calling the Request API directly is rejected (403)', async ({ page }) => {
    const draftId = await createOrderableDraft(page)
    await submitAndApprove(page, draftId)
    await logout(page)
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)

    const response = await page.request.post(`/api/orders/${draftId}/official-po/request`)
    expect(response.status()).toBe(403)
    const body = await response.json()
    expect(body.errorCode).toBe('FORBIDDEN')

    // The OPERATOR can still VIEW the (unchanged, still NOT_REQUESTED)
    // Integration Section (the Order is APPROVED) but never sees the ADMIN-
    // only Action button - the rejected direct API call above proves the
    // Backend gate; this proves the Frontend never even offers the action.
    await page.goto(`/orders/${draftId}`)
    await expect(page.getByTestId('official-po-integration-section')).toBeVisible()
    await expect(page.getByTestId('official-po-request-button')).toHaveCount(0)
    await expect(page.getByTestId('official-po-no')).toHaveText('正式PO番号未設定')
  })

  test('Scenario D: Request on a DRAFT or PENDING_APPROVAL Order is rejected', async ({ page }) => {
    const draftId = await createOrderableDraft(page)

    // DRAFT
    let response = await page.request.post(`/api/orders/${draftId}/official-po/request`)
    // purchase01 (OPERATOR) session - would 403 before even reaching the
    // Status Gate, so re-check the Gate itself as ADMIN.
    expect(response.status()).toBe(403)

    await logout(page)
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    response = await page.request.post(`/api/orders/${draftId}/official-po/request`)
    expect(response.status()).toBe(409)
    expect((await response.json()).errorCode).toBe('ORDER_NOT_APPROVED')

    // PENDING_APPROVAL
    await logout(page)
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await submitForApproval(page, draftId)
    await logout(page)
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    response = await page.request.post(`/api/orders/${draftId}/official-po/request`)
    expect(response.status()).toBe(409)
    expect((await response.json()).errorCode).toBe('ORDER_NOT_APPROVED')
  })

  test('Scenario E: after the full flow, nothing was ever actually submitted to or confirmed by Legacy', async ({ page }) => {
    // Playwright only has HTTP/Frontend access, not a direct Legacy DB
    // connection - Legacy READ ONLY / no-write is exhaustively proven at the
    // Backend level (LegacyReadOnlyIntegrationTest, OfficialPoIntegrationApiTest).
    // This Scenario's Browser-observable proxy: after a full Request +
    // repeat-Request flow, the Integration Status must still be exactly
    // PENDING (never SUBMITTED/CONFIRMED - those values have no Controller
    // path that can produce them this Phase) and the Official PO No. must
    // still be unassigned.
    const draftId = await createOrderableDraft(page)
    await submitAndApprove(page, draftId)

    await page.getByTestId('official-po-request-button').click()
    await page.getByTestId('official-po-request-dialog-confirm').click()
    await expect(page.getByText('G-SYS連携の準備が完了しました。')).toBeVisible()

    await page.reload()
    await expect(page.getByText('準備中')).toBeVisible() // statusLabel.PENDING
    await expect(page.getByTestId('official-po-no')).toHaveText('正式PO番号未設定')
    await expect(page.getByText('G-SYSへ投入済み')).toHaveCount(0) // statusLabel.SUBMITTED
    await expect(page.getByText('G-SYS登録確認済み')).toHaveCount(0) // statusLabel.CONFIRMED
  })
})

test.describe('Phase 9-A: Official PO Number / Excel Generation', () => {
  test('Scenario F: ADMIN confirms the Official PO No. and generates the Excel', async ({ page }) => {
    const draftId = await createOrderableDraft(page)
    await submitAndApprove(page, draftId)

    await page.getByTestId('official-po-request-button').click()
    await page.getByTestId('official-po-request-dialog-confirm').click()
    await expect(page.getByText('G-SYS連携の準備が完了しました。')).toBeVisible()

    await expect(page.getByTestId('official-po-number-form')).toBeVisible()
    const poNo = `E2E-TEST-${draftId}`
    await page.getByTestId('official-po-number-input').locator('input').fill(poNo)
    await page.getByTestId('official-po-delivery-week-input').locator('input').fill('WK40')
    await page.getByTestId('official-po-delivery-date-input').locator('input').fill('2026-10-01')
    await page.getByTestId('official-po-number-confirm-button').click()
    await expect(page.getByText('正式PO番号を確定しました。')).toBeVisible()
    await expect(page.getByTestId('official-po-no')).toHaveText(poNo)

    await page.getByTestId('official-po-generate-button').click()
    await expect(page.getByText('正式PO Excelを生成しました。')).toBeVisible()
    await expect(page.getByTestId('official-po-download-button')).toBeVisible()
    await expect(page.getByText('Excel生成済み')).toBeVisible() // statusLabel.GENERATED
  })

  test('Scenario G: Excel generation is blocked until a PO No. is confirmed', async ({ page }) => {
    const draftId = await createOrderableDraft(page)
    await submitAndApprove(page, draftId)

    await page.getByTestId('official-po-request-button').click()
    await page.getByTestId('official-po-request-dialog-confirm').click()
    await expect(page.getByText('G-SYS連携の準備が完了しました。')).toBeVisible()

    // No PO No. confirmed yet - the Generate button stays disabled (the
    // over-length rejection path itself is covered at the Backend,
    // OfficialPoNumberAndExcelGenerationIntegrationTest).
    await expect(page.getByTestId('official-po-generate-button')).toBeDisabled()
  })
})
