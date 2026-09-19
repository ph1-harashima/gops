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

/** Freeze Blocker-2 (Test Data Lifecycle, docs/gops-phase1-final-cleanup-report.md):
 * reuse a prior-run's SUP_ALPHA/BR_OUTDOOR Manufacturer Channel row instead
 * of unconditionally creating a new one every run - see
 * manufacturer-channel.spec.ts's own copy of this helper for the full
 * rationale (duplicated per this project's existing per-file convention,
 * not factored into a shared module). */
async function ensureManufacturerChannel(page: Page, supplierCode: string, brandCode: string, channel: 'EMAIL' | 'EDI' = 'EMAIL') {
  const channels = await (await page.request.get('/api/admin/manufacturer-channels')).json()
  const existing = channels.find((c: { supplierCode: string; brandCode: string | null }) =>
    c.supplierCode === supplierCode && c.brandCode === brandCode)
  if (existing) {
    await page.request.put(`/api/admin/manufacturer-channels/${existing.id}`, {
      data: { supplierCode, brandCode, channel, active: true },
    })
    return
  }
  await page.getByTestId('nav-master-maintenance').click()
  await page.getByTestId('nav-admin-manufacturer-channels').click()
  await page.getByTestId('manufacturer-channel-create-button').click()
  await page.getByTestId('manufacturer-channel-supplierCode').locator('input').fill(supplierCode)
  await page.getByTestId('manufacturer-channel-brandCode').locator('input').fill(brandCode)
  if (channel === 'EDI') {
    await page.getByTestId('manufacturer-channel-channel').click()
    await page.getByRole('option', { name: 'EDI' }).click()
  }
  await page.getByTestId('manufacturer-channel-save').click()
  await expect(page.getByTestId('manufacturer-channel-table-container')).toContainText(supplierCode)
}

async function logout(page: Page) {
  await page.getByTestId('nav-logout').click()
  await expect(page.getByLabel('ユーザー名')).toBeVisible()
}

async function createOrderableDraft(page: Page): Promise<string> {
  await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
  await page.getByTestId('nav-candidates').click()
  await page.getByTestId('order-candidate-brand-link-BR_OUTDOOR').click()
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
  test('Scenario A: ADMIN prepares G-SYS Integration on an APPROVED Order - Request created, Preflight shown, PO No. auto-numbered (BR-08)', async ({ page }) => {
    const draftId = await createOrderableDraft(page)
    await submitAndApprove(page, draftId)
    // Still logged in as ADMIN from submitAndApprove.

    await expect(page.getByTestId('official-po-integration-section')).toBeVisible()
    await expect(page.getByTestId('official-po-no')).toHaveText('正式PO番号未設定')

    await page.getByTestId('official-po-request-button').click()
    await page.getByTestId('official-po-request-dialog-confirm').click()

    await expect(page.getByText('G-SYS連携の準備が完了しました。')).toBeVisible()
    // BR-08 (docs/gulliver-20260917-confirmed-business-rules.md): auto-numbered
    // immediately - no "unassigned" state exists anymore once a Request exists.
    await expect(page.getByTestId('official-po-no')).toHaveText(/^[A-Z]{3}[A-Z]{3}\d{3}$/)
    await expect(page.getByTestId('official-po-no-unassigned-note')).toHaveCount(0)
    await expect(page.getByTestId('preflight-result')).toBeVisible()
    // Acceptance Fix (B-1): the live-state Chip now reads "問題なし" (natural
    // Japanese) instead of the raw internal Code "PASS". Scoped to the
    // Preflight Section, not the whole page - the Audit Timeline below
    // still renders the raw stored Code "PASS" for the same
    // PRECHECK_COMPLETED event (a deliberate, separately-audited exception -
    // resolveTimelineValue's own Javadoc - the historical record intentionally
    // keeps the exact stored value, only "status"/"attentionType" fields are
    // translated there), which a page-wide getByText('PASS') would still
    // collide with.
    await expect(page.getByTestId('preflight-result').getByText('問題なし')).toBeVisible()
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
    // path that can produce them this Phase). BR-08: the Official PO No.
    // itself is now auto-numbered (G-OPS-only, never a Legacy write) rather
    // than staying unassigned.
    const draftId = await createOrderableDraft(page)
    await submitAndApprove(page, draftId)

    await page.getByTestId('official-po-request-button').click()
    await page.getByTestId('official-po-request-dialog-confirm').click()
    await expect(page.getByText('G-SYS連携の準備が完了しました。')).toBeVisible()

    await page.reload()
    // Phase 9-G's "at a glance" panel AND the Gap Analysis C-2 Revision
    // History table (same Section) both also render this statusLabel text -
    // scoped to the dedicated status label element, same disambiguation
    // idiom as Scenario A's own "PASS" collision above.
    await expect(page.getByTestId('official-po-status-label')).toHaveText('準備中') // statusLabel.PENDING
    await expect(page.getByTestId('official-po-no')).toHaveText(/^[A-Z]{3}[A-Z]{3}\d{3}$/)
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
    // BR-08 (docs/gulliver-20260917-confirmed-business-rules.md): the
    // Official PO No. itself is already auto-numbered at this point - only
    // the delivery/shipping/payment details remain to (optionally) confirm.
    const poNo = await page.getByTestId('official-po-no').innerText()
    expect(poNo).toMatch(/^[A-Z]{3}[A-Z]{3}\d{3}$/)
    await page.getByTestId('official-po-delivery-week-input').locator('input').fill('WK40')
    await page.getByTestId('official-po-delivery-date-input').locator('input').fill('2026-10-01')
    await page.getByTestId('official-po-number-confirm-button').click()
    await expect(page.getByText('配送・決済条件を確定しました。')).toBeVisible()
    await expect(page.getByTestId('official-po-no')).toHaveText(poNo)

    await page.getByTestId('official-po-generate-button').click()
    await expect(page.getByText('正式PO Excelを生成しました。')).toBeVisible()
    await expect(page.getByTestId('official-po-download-button')).toBeVisible()
    // Phase 9-G's "at a glance" panel AND the Gap Analysis C-2 Revision
    // History table also render this same statusLabel text - scoped to the
    // dedicated status label element to disambiguate.
    await expect(page.getByTestId('official-po-status-label')).toHaveText('Excel生成済み') // statusLabel.GENERATED

    // Acceptance Fix C-1: the Excel and PDF Download buttons must never
    // share one label - a user must never be able to mistake which
    // Artifact a Download button actually downloads. Generate the PDF too
    // so both buttons are on screen at once and assert their exact,
    // distinct text (not just visibility).
    await page.getByTestId('official-po-pdf-generate-button').click()
    await expect(page.getByText('正式PO PDFを生成しました。')).toBeVisible()
    await expect(page.getByTestId('official-po-download-button')).toHaveText('Excelをダウンロード')
    await expect(page.getByTestId('official-po-pdf-download-button')).toHaveText('PDFをダウンロード')
  })

  test('Scenario G: Excel generation is available immediately - BR-08 auto-numbers the PO No. at Request time', async ({ page }) => {
    const draftId = await createOrderableDraft(page)
    await submitAndApprove(page, draftId)

    await page.getByTestId('official-po-request-button').click()
    await page.getByTestId('official-po-request-dialog-confirm').click()
    await expect(page.getByText('G-SYS連携の準備が完了しました。')).toBeVisible()

    // BR-08 (docs/gulliver-20260917-confirmed-business-rules.md): the
    // Official PO No. is already auto-numbered at this point - there is no
    // longer a "PO No. not yet confirmed" state that could block Generate.
    await expect(page.getByTestId('official-po-generate-button')).toBeEnabled()
  })

  /** Gap Analysis C-1 (docs/gulliver-20260917-phase1-gap-analysis.md 7章):
   * "G-OPS Standard Official PO PDF" - same Gate as Excel (confirmed PO No.)
   * but independent of the Excel/Import Folder Integration Status. */
  test('Scenario J: ADMIN generates the Official PO PDF alongside the Excel', async ({ page }) => {
    const draftId = await createOrderableDraft(page)
    await submitAndApprove(page, draftId)

    await page.getByTestId('official-po-request-button').click()
    await page.getByTestId('official-po-request-dialog-confirm').click()

    // BR-08: the Official PO No. is already auto-numbered at Request time -
    // PDF Generate is available immediately, same as Excel Generate.
    await expect(page.getByTestId('official-po-pdf-generate-button')).toBeEnabled()

    await page.getByTestId('official-po-pdf-generate-button').click()
    await expect(page.getByText('正式PO PDFを生成しました。')).toBeVisible()
    await expect(page.getByTestId('official-po-pdf-download-button')).toBeVisible()

    // Excel is still untouched (never auto-generated by the PDF Action) -
    // Integration Status stays PENDING, proving the two artifacts are
    // independent (7章's explicit design requirement).
    await expect(page.getByTestId('official-po-status-label')).toHaveText('準備中') // statusLabel.PENDING
    await expect(page.getByTestId('official-po-download-button')).toHaveCount(0)
  })
})

test.describe('Phase 9-B: Import Folder Integration', () => {
  test('Scenario H: ADMIN places the generated Excel into the Import Folder', async ({ page }) => {
    const draftId = await createOrderableDraft(page)
    await submitAndApprove(page, draftId)

    await page.getByTestId('official-po-request-button').click()
    await page.getByTestId('official-po-request-dialog-confirm').click()
    await expect(page.getByText('G-SYS連携の準備が完了しました。')).toBeVisible()

    // BR-08: Official PO No. is auto-numbered immediately - no manual input.
    await page.getByTestId('official-po-delivery-week-input').locator('input').fill('WK40')
    await page.getByTestId('official-po-delivery-date-input').locator('input').fill('2026-10-01')
    await page.getByTestId('official-po-number-confirm-button').click()
    await expect(page.getByText('配送・決済条件を確定しました。')).toBeVisible()

    await page.getByTestId('official-po-generate-button').click()
    await expect(page.getByText('正式PO Excelを生成しました。')).toBeVisible()

    await page.getByTestId('official-po-place-button').click()
    await expect(page.getByText('G-SYS連携用ファイルを配置しました。')).toBeVisible()
    await expect(page.getByTestId('official-po-placed-note')).toBeVisible()
    // Scoped to the dedicated status label element - "PO番号確定済みの注記" text
    // also contains this substring once locked, and the "at a glance" panel
    // / Gap Analysis C-2 Revision History table both also render this same
    // statusLabel text (same collision pattern as Scenario A's own PASS
    // note above).
    await expect(page.getByTestId('official-po-status-label')).toHaveText('G-SYSへ投入済み') // statusLabel.SUBMITTED

    // Double-click (idempotent) - button is now disabled while SUBMITTED,
    // so this proves the Frontend Gate itself, not just the Backend.
    await expect(page.getByTestId('official-po-place-button')).toBeDisabled()
  })
})

test.describe('Phase 9-C: G-SYS Import Confirmation', () => {
  test('Scenario I: checking import status before G-SYS has registered the PO shows NOT_YET_IMPORTED, not an error', async ({ page }) => {
    const draftId = await createOrderableDraft(page)
    await submitAndApprove(page, draftId)

    await page.getByTestId('official-po-request-button').click()
    await page.getByTestId('official-po-request-dialog-confirm').click()
    await expect(page.getByText('G-SYS連携の準備が完了しました。')).toBeVisible()

    // The auto-numbered PO No. (BR-08) provably does not (and never will)
    // exist in the Legacy Demo MySQL Fixture set either - no real Import
    // Batch runs in this environment, so SUBMITTED never naturally becomes
    // CONFIRMED here regardless of the value.
    await page.getByTestId('official-po-generate-button').click()
    await expect(page.getByText('正式PO Excelを生成しました。')).toBeVisible()

    await page.getByTestId('official-po-place-button').click()
    await expect(page.getByText('G-SYS連携用ファイルを配置しました。')).toBeVisible()

    await page.getByTestId('official-po-confirm-import-button').click()
    await expect(page.getByTestId('official-po-import-not-matched')).toBeVisible()
    await expect(page.getByTestId('official-po-import-not-matched')).toContainText('エラーではありません')
    // Status stays SUBMITTED - no state change on NOT_YET_IMPORTED. Scoped
    // to the dedicated status label element (the "at a glance" panel / Gap
    // Analysis C-2 Revision History table both also render this same
    // statusLabel text).
    await expect(page.getByTestId('official-po-status-label')).toHaveText('G-SYSへ投入済み')
  })
})

/**
 * Gap Analysis C-2/C-3 (docs/gulliver-20260917-phase1-gap-analysis.md
 * 7章/8章, Scenario 2 of the instruction's End-to-End Scenario list):
 * Official PO発行 -> PO内容修正 -> Revision Required検知 -> ADMIN確認 -> 再発行
 * -> old Revision SUPERSEDED / new Revision ACTIVE.
 */
test.describe('Gap Analysis C-2/C-3: Official PO Reissue', () => {
  test('Scenario K: ADMIN reissues the Official PO after a correction, old Revision stays in history as SUPERSEDED', async ({ page }) => {
    const draftId = await createOrderableDraft(page)
    await submitAndApprove(page, draftId)

    // Issue the Official PO for Revision 1. BR-08: Official PO No. is
    // auto-numbered immediately - no manual input.
    await page.getByTestId('official-po-request-button').click()
    await page.getByTestId('official-po-request-dialog-confirm').click()
    await expect(page.getByText('G-SYS連携の準備が完了しました。')).toBeVisible()
    const poNo = await page.getByTestId('official-po-no').innerText()
    expect(poNo).toMatch(/^[A-Z]{3}[A-Z]{3}\d{3}$/)
    await page.getByTestId('official-po-generate-button').click()
    await expect(page.getByText('正式PO Excelを生成しました。')).toBeVisible()

    // No correction has happened yet - Reissue stays disabled, no banner.
    await expect(page.getByTestId('official-po-reissue-button')).toBeDisabled()
    await expect(page.getByTestId('reissue-required-banner')).toHaveCount(0)

    // Send, then answer the Supplier Response with a different Qty.
    const goToPreview = page.getByTestId('order-detail-primary-action')
    await expect(goToPreview).toHaveText('PO プレビューを見る')
    await goToPreview.click()
    await page.getByTestId('demo-send-button').click()
    await page.getByTestId('demo-send-dialog-confirm').click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))

    const enterSupplierResponse = page.getByTestId('order-detail-primary-action')
    await expect(enterSupplierResponse).toHaveText('メーカー回答を入力')
    await enterSupplierResponse.click()
    const orderedQtyCell = page.getByTestId(`response-row-${SKU}`).locator('td').nth(2)
    const orderedQty = Number(await orderedQtyCell.innerText())
    const confirmedQty = Math.max(0, orderedQty - 1)
    await page.getByTestId(`confirmed-qty-input-${SKU}`).locator('input').fill(String(confirmedQty))
    await page.getByTestId('save-response-button').click()
    await expect(page.getByText('回答を保存しました。')).toBeVisible()
    await page.getByTestId('confirm-response-button').click()
    await page.getByTestId('confirm-response-dialog-confirm').click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))
    await expect(page.getByText('メーカー回答を確定しました。')).toBeVisible()

    // 修正版を作成 lives on the Supplier Response review screen (SUPPLIER_CONFIRMED
    // Order Detail's primary Action), not on Order Detail itself.
    await page.getByTestId('order-detail-primary-action').click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}/supplier-response(\\?.*)?$`))

    // Create the correction (修正版を作成) and re-approve - deliberately
    // stopping short of re-sending (Reissue only requires re-approval, not
    // a completed re-Send - the Backend's own isReissueRequired Gate).
    await page.getByTestId('create-revision-button').click()
    await page.getByTestId('revision-reason-input').locator('textarea').first().fill('メーカー在庫の都合により数量を修正')
    await page.getByTestId('apply-confirmed-values-checkbox').check()
    await page.getByTestId('revision-dialog-confirm').click()
    await expect(page).toHaveURL(new RegExp(`/orders/drafts/${draftId}(\\?.*)?$`))
    await page.getByTestId('submit-for-approval-button').click()
    await page.getByTestId('submit-for-approval-dialog-confirm').click()
    await page.getByTestId('order-detail-approve-button').click()
    await page.getByTestId('approve-dialog-confirm').click()

    // Revision Required is now detected - banner shown, Reissue enabled.
    await expect(page.getByTestId('reissue-required-banner')).toBeVisible()
    await expect(page.getByTestId('official-po-reissue-button')).toBeEnabled()

    await page.getByTestId('official-po-reissue-button').click()
    await page.getByTestId('official-po-reissue-dialog-confirm').click()
    await expect(page.getByText('正式POを再発行しました。')).toBeVisible()

    // Revision Required cleared (the new ACTIVE Document has not been issued
    // yet), and Revision History shows both rows with the correct lifecycle.
    await expect(page.getByTestId('reissue-required-banner')).toHaveCount(0)
    await expect(page.getByTestId('official-po-revision-history-table')).toBeVisible()
    const row1 = page.getByTestId('revision-history-row-1')
    await expect(row1).toContainText('旧版（再発行済み）')
    await expect(row1).toContainText(poNo)
    const row2 = page.getByTestId('revision-history-row-2')
    await expect(row2).toContainText('有効')
    // BR-08 Scenario 7 (docs/gulliver-20260917-confirmed-business-rules.md):
    // Reissue advances the Revision, never the Official PO No. itself -
    // Revision 2 carries forward the exact SAME number as Revision 1.
    await expect(row2).toContainText(poNo)
    await expect(page.getByTestId('official-po-no')).toHaveText(poNo)
    // New Revision starts back at 準備中 (PENDING) - Excel/PDF must be
    // (re)generated again for it, matching the existing "correct-and-restart"
    // workflow. Scoped to the "at a glance" panel specifically (not
    // official-po-integration-section, which also now contains the
    // Revision History table's own PENDING status cell for this same new row).
    await expect(page.getByTestId('at-a-glance-panel').getByText('準備中')).toBeVisible()
  })

  /**
   * Revision Consistency Audit (docs/gulliver-phase1-revision-consistency-audit.md)
   * Scenario D: after a genuine Reissue, Manufacturer Send (real Email
   * Send, Phase 9-E) must target the NEW ACTIVE Revision 2 - never the
   * SUPERSEDED Revision 1 - proven through the real UI, not just the
   * Backend Service layer (OfficialPoRevisionConsistencyIntegrationTest's
   * own scenarioD test covers the same claim at that layer).
   */
  test('Scenario M: Manufacturer Send after Reissue targets the new ACTIVE Revision, not the SUPERSEDED one', async ({ page }) => {
    const draftId = await createOrderableDraft(page)
    await submitAndApprove(page, draftId)

    await page.getByTestId('official-po-request-button').click()
    await page.getByTestId('official-po-request-dialog-confirm').click()
    await expect(page.getByText('G-SYS連携の準備が完了しました。')).toBeVisible()
    // BR-08: Official PO No. is auto-numbered immediately - no manual input.
    const poNo = await page.getByTestId('official-po-no').innerText()
    expect(poNo).toMatch(/^[A-Z]{3}[A-Z]{3}\d{3}$/)
    await page.getByTestId('official-po-generate-button').click()
    await expect(page.getByText('正式PO Excelを生成しました。')).toBeVisible()

    const goToPreview = page.getByTestId('order-detail-primary-action')
    await goToPreview.click()
    await page.getByTestId('demo-send-button').click()
    await page.getByTestId('demo-send-dialog-confirm').click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))

    await page.getByTestId('order-detail-primary-action').click()
    const orderedQtyCell = page.getByTestId(`response-row-${SKU}`).locator('td').nth(2)
    const orderedQty = Number(await orderedQtyCell.innerText())
    await page.getByTestId(`confirmed-qty-input-${SKU}`).locator('input').fill(String(Math.max(0, orderedQty - 1)))
    await page.getByTestId('save-response-button').click()
    await expect(page.getByText('回答を保存しました。')).toBeVisible()
    await page.getByTestId('confirm-response-button').click()
    await page.getByTestId('confirm-response-dialog-confirm').click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))

    await page.getByTestId('order-detail-primary-action').click()
    await page.getByTestId('create-revision-button').click()
    await page.getByTestId('revision-reason-input').locator('textarea').first().fill('メーカー在庫の都合により数量を修正')
    await page.getByTestId('apply-confirmed-values-checkbox').check()
    await page.getByTestId('revision-dialog-confirm').click()
    await page.getByTestId('submit-for-approval-button').click()
    await page.getByTestId('submit-for-approval-dialog-confirm').click()
    await page.getByTestId('order-detail-approve-button').click()
    await page.getByTestId('approve-dialog-confirm').click()
    await page.getByTestId('official-po-reissue-button').click()
    await page.getByTestId('official-po-reissue-dialog-confirm').click()
    await expect(page.getByText('正式POを再発行しました。')).toBeVisible()

    // Revision 2 is now ACTIVE/PENDING - BR-08 Scenario 7: it carries
    // forward the exact SAME Official PO No. as Revision 1, never a new one.
    await expect(page.getByTestId('official-po-no')).toHaveText(poNo)
    await page.getByTestId('official-po-generate-button').click()
    await expect(page.getByText('正式PO Excelを生成しました。')).toBeVisible()
    // BR-01: Manufacturer Send now requires BOTH Excel and PDF.
    await page.getByTestId('official-po-pdf-generate-button').click()
    await expect(page.getByText('正式PO PDFを生成しました。')).toBeVisible()

    // Manufacturer Channel/Contact/Template for SUP_ALPHA/BR_OUTDOOR.
    await ensureManufacturerChannel(page, 'SUP_ALPHA', 'BR_OUTDOOR', 'EMAIL')

    await page.goto('/admin/supplier-contacts')
    await page.getByTestId('supplier-contact-create-button').click()
    await page.getByTestId('supplier-contact-supplierCode').locator('input').fill('SUP_ALPHA')
    await page.getByTestId('supplier-contact-brandCode').locator('input').fill('BR_OUTDOOR')
    await page.getByTestId('supplier-contact-contactName').locator('input').fill('Revision Consistency Tester')
    await page.getByTestId('supplier-contact-email').locator('input').fill('revision-consistency@example.com')
    await page.getByTestId('supplier-contact-save').click()
    await expect(page.getByTestId('supplier-contact-table-container')).toContainText('revision-consistency@example.com')

    await page.goto('/admin/mail-templates')
    await page.getByTestId('mail-template-create-button').click()
    await page.getByTestId('mail-template-templateName').locator('input').fill('Revision Consistency Template')
    await page.getByTestId('mail-template-supplierCode').locator('input').fill('SUP_ALPHA')
    await page.getByTestId('mail-template-brandCode').locator('input').fill('BR_OUTDOOR')
    await page.getByTestId('mail-template-subjectTemplate').locator('input').fill('PO {{poNo}}')
    await page.getByTestId('mail-template-bodyTemplate').locator('textarea').first().fill('Dear {{contactName}}, PO No: {{poNo}}')
    await page.getByTestId('mail-template-save').click()
    await expect(page.getByTestId('mail-template-table-container')).toContainText('Revision Consistency Template')

    // Manufacturer Send - must resolve to Revision 2's own Excel/PDF, not
    // Revision 1's stale attachment (BR-08: same PO No. either way, so this
    // Scenario also proves 001/002 are never conflated purely by Revision,
    // not by a distinguishing PO No. string).
    await page.goto(`/orders/${draftId}`)
    await page.getByTestId('mail-preview-button').click()
    await expect(page.getByTestId('mail-preview-result')).toBeVisible()
    await expect(page.getByText(`PO ${poNo}`)).toBeVisible()
    await page.getByTestId('email-send-button').click()
    // BR-04: Send now requires final confirmation via a Dialog.
    await page.getByTestId('email-send-confirm-dialog-confirm').click()
    await expect(page.getByText('メールを送信しました。')).toBeVisible()

    // Revision History: Revision 2 shows 送信済み, Revision 1 never does -
    // the Send is correctly scoped to the Revision it actually belongs to
    // (confirmed directly in Postgres: order_email.revision_no=2 for this
    // Order). Cache Consistency Fix regression check: NO page.reload() here -
    // useSendEmail's onSuccess now invalidates ['official-po-revisions', orderId]
    // too, so this must already be up to date on the same page render.
    await expect(page.getByTestId('revision-history-row-2')).toContainText('送信済み')
    await expect(page.getByTestId('revision-history-row-1')).not.toContainText('送信済み')

    // Cleanup: this Master data is SUP_ALPHA/BR_OUTDOOR-wide and would
    // otherwise collide with other specs' own "nothing configured yet"
    // assumptions for the same Supplier/Brand in a full-suite run.
    const channels = await (await page.request.get('/api/admin/manufacturer-channels')).json()
    for (const c of channels) {
      if (c.supplierCode === 'SUP_ALPHA' && c.brandCode === 'BR_OUTDOOR' && c.active) {
        await page.request.put(`/api/admin/manufacturer-channels/${c.id}`, {
          data: { supplierCode: c.supplierCode, brandCode: c.brandCode, channel: c.channel, active: false },
        })
      }
    }
    const contacts = await (await page.request.get('/api/admin/supplier-contacts')).json()
    for (const c of contacts) {
      if (c.email === 'revision-consistency@example.com' && c.active) {
        await page.request.put(`/api/admin/supplier-contacts/${c.id}`, { data: { ...c, active: false } })
      }
    }
    const templates = await (await page.request.get('/api/admin/mail-templates')).json()
    for (const t of templates) {
      if (t.templateName === 'Revision Consistency Template' && t.active) {
        await page.request.put(`/api/admin/mail-templates/${t.id}`, { data: { ...t, active: false } })
      }
    }
  })
})

/**
 * BR-03 (docs/gulliver-20260917-confirmed-business-rules.md, Scenario 3 of
 * the instruction's End-to-End Scenario list): Official PO issued -> Cancel
 * Request (reason required) -> ADMIN Approval (-> best-effort メーカーへ
 * 取消連絡) -> CANCELLED -> no automatic Legacy write -> Audit Trail records
 * Requester and Approver distinctly.
 */
test.describe('BR-03: Official PO Cancel Approval Workflow', () => {
  test('Scenario L: ADMIN requests Cancel with a mandatory reason, then approves it - no automatic Legacy write', async ({ page }) => {
    const draftId = await createOrderableDraft(page)
    await submitAndApprove(page, draftId)

    await page.getByTestId('official-po-request-button').click()
    await page.getByTestId('official-po-request-dialog-confirm').click()
    await expect(page.getByText('G-SYS連携の準備が完了しました。')).toBeVisible()
    // BR-08: Official PO No. is auto-numbered immediately - no manual input.
    const poNo = await page.getByTestId('official-po-no').innerText()
    await page.getByTestId('official-po-generate-button').click()
    await expect(page.getByText('正式PO Excelを生成しました。')).toBeVisible()

    // Step 1: Cancel Request - reason required: the Confirm button stays
    // disabled until something is typed.
    await page.getByTestId('official-po-cancel-button').click()
    await expect(page.getByTestId('official-po-cancel-dialog-confirm')).toBeDisabled()
    await page.getByTestId('official-po-cancel-reason-input').locator('textarea').first().fill('顧客都合によりOrderをキャンセル')
    await expect(page.getByTestId('official-po-cancel-dialog-confirm')).toBeEnabled()
    await page.getByTestId('official-po-cancel-dialog-confirm').click()
    await expect(page.getByText('正式POのキャンセルを申請しました。')).toBeVisible()

    // A Cancel Request alone must never reach CANCELLED yet.
    await expect(page.getByTestId('official-po-cancel-requested-note')).toBeVisible()
    await expect(page.getByTestId('official-po-cancel-requested-note')).toContainText('顧客都合によりOrderをキャンセル')
    await expect(page.getByTestId('official-po-cancelled-note')).toHaveCount(0)
    await expect(page.getByTestId('official-po-status-label')).toHaveText('Excel生成済み')
    // Cancel Request button is now disabled (already pending); Approve
    // becomes the only enabled Action for this Workflow.
    await expect(page.getByTestId('official-po-cancel-button')).toBeDisabled()
    await expect(page.getByTestId('official-po-cancel-approve-button')).toBeEnabled()

    // Step 2: ADMIN Approval - only now does it actually become CANCELLED.
    await page.getByTestId('official-po-cancel-approve-button').click()
    await expect(page.getByText('正式POのキャンセルを承認しました。')).toBeVisible()

    await expect(page.getByTestId('official-po-cancelled-note')).toBeVisible()
    await expect(page.getByTestId('official-po-cancelled-note')).toContainText('顧客都合によりOrderをキャンセル')
    // The Integration Status axis (Excel/Import Folder progress) is left
    // untouched by Cancel - it still reads GENERATED, never silently reset.
    await expect(page.getByTestId('official-po-status-label')).toHaveText('Excel生成済み')
    // Both Actions are now disabled - a terminal state, matching the
    // Backend's own "never re-cancel/re-approve" rule.
    await expect(page.getByTestId('official-po-cancel-button')).toBeDisabled()
    await expect(page.getByTestId('official-po-cancel-approve-button')).toBeDisabled()
    await expect(page.getByTestId('official-po-reissue-button')).toBeDisabled()

    const historyRow = page.getByTestId('revision-history-row-1')
    await expect(historyRow).toContainText('キャンセル済み')
    await expect(historyRow).toContainText(poNo)

    // No automatic Legacy write of any kind is possible from this Action -
    // Cancel Request/Approval only ever call markCancelRequested()/
    // markCancelled() and save the Portal DB row (Backend Fact,
    // OfficialPoIntegrationService's own Javadoc), and "メーカーへ取消連絡"
    // only ever goes through the same Local/Demo/Test Simulation Adapter
    // every other Manufacturer communication uses; this Scenario's
    // Browser-observable proxy is that the page never enters an error state
    // and Integration Status/PO No. remain exactly as they were set by the
    // Portal-only Actions above.
    await expect(page.getByTestId('official-po-no')).toHaveText(poNo)
  })
})
