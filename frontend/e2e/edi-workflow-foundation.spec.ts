import { test, expect, type Page } from '@playwright/test'

/**
 * Phase 7-H: EDI発注Workflow Foundation.
 *
 * Source audit confirmed (before any implementation, per this Phase's own
 * instruction) that OrderStatusTransitionService.demoSend was the ONLY code
 * path anywhere that ever created a PortalOrderRevision/SupplierResponse row
 * - a Supplier who is never Email-sent could never reach Supplier Response
 * at all. This spec proves the new recordEdiSend path (a Foundation only -
 * no real EDI file/API/connection anywhere) makes that reachable without
 * touching the Email path or any Approval/Supplier Response Business Rule.
 */

const OPERATOR_USERNAME = 'purchase01'
const OPERATOR_PASSWORD = 'DemoPass123!'
const ADMIN_USERNAME = 'admin01'
const ADMIN_PASSWORD = 'DemoPass123!'
const SKU = 'KT-KNIFE-001'

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

/** Candidate -> Draft (qty bumped so it's orderable) -> 承認依頼 -> ADMIN
 * 承認 -> APPROVED Order Detail, same shape as the other Workflow specs'
 * own setup helpers. Leaves the caller logged in as ADMIN, on the PO
 * Preview screen. */
async function createApprovedOrderAndOpenPreview(page: Page): Promise<string> {
  await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
  await page.getByTestId('nav-candidates').click()
  await expect(page.getByTestId(`candidate-row-${SKU}`)).toBeVisible()
  await page.getByTestId(`candidate-checkbox-${SKU}`).locator('input').check()
  await page.getByTestId('create-draft-button').click()
  await expect(page).toHaveURL(/\/orders\/drafts\/(\d+)/)
  const draftId = page.url().match(/\/orders\/drafts\/(\d+)/)?.[1]
  expect(draftId).toBeTruthy()

  const qtyInput = page.getByTestId(`order-qty-input-${SKU}`).locator('input')
  await qtyInput.fill('4')
  await page.getByTestId('save-draft-button').click()
  await expect(page.getByText('保存しました。')).toBeVisible()

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
  await expect(page).toHaveURL(new RegExp(`/orders/drafts/${draftId}/preview`))
  return draftId!
}

test.describe('Phase 7-H: EDI発注Workflow Foundation', () => {
  test('G: EMAIL Workflow (Demo Send) is unaffected - still sets communicationChannel=EMAIL', async ({ page }) => {
    const draftId = await createApprovedOrderAndOpenPreview(page)

    await expect(page.getByTestId('demo-send-button')).toBeVisible()
    await expect(page.getByTestId('edi-send-button')).toBeVisible()

    await page.getByTestId('demo-send-button').click()
    await page.getByTestId('demo-send-dialog-confirm').click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))
    await expect(page.getByText('メーカーへの発注を記録しました。')).toBeVisible()

    // Channel is visible on Order Detail - PO Preview itself is no longer
    // reachable once past APPROVED (PoPreviewService's own pre-existing
    // Status Gate, confirmed via Source - unrelated to this Phase).
    await expect(page.getByTestId('communication-channel-chip')).toHaveText('発注方法: Email')
  })

  test('H: EDI Foundation - recordEdiSend reaches Supplier Response without any Demo Send', async ({ page }) => {
    const draftId = await createApprovedOrderAndOpenPreview(page)

    await page.getByTestId('edi-send-button').click()
    await page.getByTestId('edi-send-dialog-confirm').click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))
    await expect(page.getByText('EDI発注済みとして記録しました。')).toBeVisible()

    await expect(page.getByTestId('order-detail-primary-action')).toHaveText('メーカー回答を入力')
    await expect(page.getByTestId('communication-channel-chip')).toHaveText('発注方法: EDI')
  })

  test('I: Supplier Response / Revision / Agreement are Channel-independent after an EDI Send', async ({ page }) => {
    const draftId = await createApprovedOrderAndOpenPreview(page)

    await page.getByTestId('edi-send-button').click()
    await page.getByTestId('edi-send-dialog-confirm').click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))

    // Supplier Response for an EDI-sent Order works exactly like the Email
    // path (same screen, same component - no Channel branching anywhere).
    // Wait for the primary Action to actually reflect the fresh
    // AWAITING_SUPPLIER state before clicking - immediately after
    // navigation the still-stale APPROVED-state button ("PO Previewを見る")
    // can briefly still be on screen (same race Test H's own text-check
    // guard already avoids).
    await expect(page.getByTestId('order-detail-primary-action')).toHaveText('メーカー回答を入力')
    await page.getByTestId('order-detail-primary-action').click()
    await expect(page).toHaveURL(/\/supplier-response/)
    await expect(page.getByRole('heading', { name: 'メーカー回答' })).toBeVisible()

    const qtyInput = page.getByTestId(`confirmed-qty-input-${SKU}`).locator('input')
    await qtyInput.fill('4')
    await page.getByTestId('save-response-button').click()
    await expect(page.getByText('保存しました。')).toBeVisible()

    await expect(page.getByTestId('confirm-response-button')).toBeEnabled()
    await page.getByTestId('confirm-response-button').click()
    await page.getByTestId('confirm-response-dialog-confirm').click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))
    // .first() - the Status Chip in the header, not the Audit Timeline's own
    // "メーカー回答待ち [Arrow] メーカー確定済み" row (Phase 7-H's Timeline
    // redesign renders each side as its own isolated text node, so a plain
    // exact-text match can resolve to both - same known ambiguity already
    // documented on the analogous check in
    // supplier-response-confirm-dialog-stability.spec.ts).
    await expect(page.getByText('メーカー確定済み', { exact: true }).first()).toBeVisible()
  })

  test('J (Phase 9-D): EDI status tracker starts WAITING_INPUT after edi-send and can be marked COMPLETED', async ({ page }) => {
    const draftId = await createApprovedOrderAndOpenPreview(page)

    await page.getByTestId('edi-send-button').click()
    await page.getByTestId('edi-send-dialog-confirm').click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))

    await expect(page.getByTestId('edi-status-section')).toBeVisible()
    await expect(page.getByTestId('edi-status-chip')).toHaveText('EDI入力待ち')
    await expect(page.getByTestId('edi-complete-button')).toBeVisible()

    await page.getByTestId('edi-complete-button').click()
    await expect(page.getByText('EDI入力完了として記録しました。')).toBeVisible()
    await expect(page.getByTestId('edi-status-chip')).toHaveText('EDI入力完了')
    await expect(page.getByTestId('edi-complete-button')).toHaveCount(0)

    // No "Send Email" Button ever appears for an EDI-channel Order - the
    // real Email Send Action (Phase 9-E) does not exist yet, but the
    // Mail Preview Section's own send-gating already only shows for a
    // resolved EMAIL Channel (verified again once Phase 9-E lands).
    await expect(page.getByTestId('edi-status-section')).not.toContainText('メールを送信');
  })
})
