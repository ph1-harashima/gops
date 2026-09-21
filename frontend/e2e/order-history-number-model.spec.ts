import { test, expect, type Page } from '@playwright/test'

/**
 * Phase 1 Final Cleanup (Order History Number Model Audit,
 * docs/gops-phase1-final-cleanup-report.md): Order History previously showed
 * a single ambiguous "PO No." column that was always the Portal-internal
 * number (prototypePoNo/draftNo), never the real Official PO No.
 * (officialPoNo) - the number Excel/PDF/Manufacturer Send actually use.
 * These 4 scenarios cover the fix: Management No. and Official PO No. (and
 * Revision) are now distinct, correctly-sourced columns, both before and
 * after Official PO issuance, and both are searchable.
 *
 * Prerequisite: same as official-po-integration.spec.ts - Demo Reset run
 * first against the local Prototype PostgreSQL.
 */

const OPERATOR_USERNAME = 'purchase01'
const OPERATOR_PASSWORD = 'DemoPass123!'
const ADMIN_USERNAME = 'admin01'
const ADMIN_PASSWORD = 'DemoPass123!'

const SKU = 'OD-TENT-001'
const MOBILE_VIEWPORT = { width: 390, height: 844 }

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

async function submitAndApprove(page: Page, draftId: string): Promise<void> {
  await page.goto(`/orders/drafts/${draftId}`)
  await page.getByTestId('submit-for-approval-button').click()
  await page.getByTestId('submit-for-approval-dialog-confirm').click()
  await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))
  await logout(page)
  await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
  await page.goto(`/orders/${draftId}`)
  await page.getByTestId('order-detail-approve-button').click()
  await page.getByTestId('approve-dialog-confirm').click()
  await expect(page.getByText('承認しました。')).toBeVisible()
}

/** Requires ADMIN already logged in (submitAndApprove leaves that session). */
async function issueOfficialPoNo(page: Page, draftId: string): Promise<string> {
  await page.goto(`/orders/${draftId}`)
  await page.getByTestId('official-po-request-button').click()
  await page.getByTestId('official-po-request-dialog-confirm').click()
  await expect(page.getByText('G-SYS連携の準備が完了しました。')).toBeVisible()
  // Post-Freeze Technical Stability Audit
  // (docs/gops-post-freeze-e2e-stability-audit.md): auto-retrying wait
  // FIRST, then capture - see the identical fix/comment in
  // official-po-integration.spec.ts.
  await expect(page.getByTestId('official-po-no')).toHaveText(/^[A-Z]{3}[A-Z]{3}\d{3}$/)
  const officialPoNo = await page.getByTestId('official-po-no').innerText()
  return officialPoNo
}

async function openHistoryFilteredBy(page: Page, keyword: string) {
  await page.goto('/orders/history')
  const input = page.getByTestId('order-no-keyword-input').locator('input')
  await input.fill(keyword)
  await input.press('Enter')
  await expect(page.getByTestId('order-history-table-container')).toBeVisible()
}

test.describe('Phase 1 Final Cleanup: Order History Number Model', () => {
  test('OH-1: a Draft Order shows a Management No. in History, with Official PO No. explicitly not-yet-assigned', async ({ page }) => {
    const draftId = await createOrderableDraft(page)
    await page.goto(`/orders/drafts/${draftId}`)
    // The Draft screen's own heading is "{title} - {draftNo}" (no dedicated
    // testid exists for it) - draftNo is the only stable identifier a Draft
    // has before approve() assigns prototypePoNo, so it's what History's
    // Management No. column must fall back to for this Order.
    const heading = await page.locator('h1').innerText()
    const draftNo = heading.split(' - ').pop()!.trim()
    expect(draftNo).toBeTruthy()

    await openHistoryFilteredBy(page, draftNo)
    const row = page.locator('tbody tr').first()
    await expect(row).toBeVisible()
    await expect(row.getByTestId('order-history-management-no')).toHaveText(draftNo)
    // Official PO No. must NEVER borrow the internal number - it must show
    // the explicit "not yet assigned" state, matching the already-established
    // Order Detail term (officialPoIntegration.officialPoNoUnassigned).
    await expect(row.getByTestId('order-history-official-po-no')).toHaveText('正式PO番号未設定')
  })

  test('OH-2: an Official-PO-issued Order shows Management No., Official PO No., and Revision as distinct values in History', async ({ page }) => {
    const draftId = await createOrderableDraft(page)
    await submitAndApprove(page, draftId)
    const officialPoNo = await issueOfficialPoNo(page, draftId)

    await openHistoryFilteredBy(page, officialPoNo)
    const row = page.locator('tbody tr').first()
    await expect(row).toBeVisible()

    const managementNo = await row.getByTestId('order-history-management-no').innerText()
    const historyOfficialPoNo = await row.getByTestId('order-history-official-po-no').innerText()
    const revision = await row.getByTestId('order-history-revision').innerText()

    // Three genuinely distinct concepts - Management No. is never the
    // Official PO No. and vice versa (the exact bug this Phase fixes).
    expect(managementNo).not.toBe(historyOfficialPoNo)
    expect(historyOfficialPoNo).toBe(officialPoNo)
    expect(revision).toBe('1')
  })

  test('OH-3: searching by Official PO No. finds the correct Order', async ({ page }) => {
    const draftId = await createOrderableDraft(page)
    await submitAndApprove(page, draftId)
    const officialPoNo = await issueOfficialPoNo(page, draftId)

    // A second, unrelated Order exists in the same list (created earlier in
    // this run / by other specs sharing the demo DB) - searching by the
    // Official PO No. must narrow to exactly this Order, proving the search
    // predicate now reaches officialPoNo (Phase 7-H's pre-existing keyword
    // search only ever matched draftNo/prototypePoNo).
    await openHistoryFilteredBy(page, officialPoNo)
    const rows = page.locator('tbody tr')
    await expect(rows).toHaveCount(1)
    await expect(rows.first().getByTestId('order-history-official-po-no')).toHaveText(officialPoNo)

    await rows.first().click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))
  })

  test('OH-4: History -> Detail -> Back preserves the active Filter and Pagination', async ({ page }) => {
    const draftId = await createOrderableDraft(page)
    await submitAndApprove(page, draftId)
    const officialPoNo = await issueOfficialPoNo(page, draftId)

    await openHistoryFilteredBy(page, officialPoNo)
    await expect(page).toHaveURL(new RegExp(`orderNoKeyword=${officialPoNo}`))

    await page.locator('tbody tr').first().click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))

    await page.getByRole('button', { name: '発注一覧へ戻る' }).click()
    await expect(page).toHaveURL(new RegExp(`orderNoKeyword=${officialPoNo}`))
    await expect(page.getByTestId('order-no-keyword-input').locator('input')).toHaveValue(officialPoNo)
    await expect(page.locator('tbody tr')).toHaveCount(1)
  })

  /** G-OPS Visual Re-Review Final Correction (Finding B): root-caused via
   * Source read (OrderHistoryService.buildSpecification - a real DB-pushed
   * Specification/Pageable, case-insensitive LIKE '%keyword%' against
   * draftNo/prototypePoNo/officialPoNo, not a client-side filter) and live
   * network/URL/rendered-row tracing (docs/gops-visual-re-review-final-
   * correction.md §5) - the search itself was never broken. What the
   * original Visual Re-Review screenshot actually showed was two
   * screenshot-capture artifacts: the keyword was typed but Enter was never
   * pressed (this input's own established "commit on Enter/blur, not every
   * keystroke" design, matching OH-3 above), and a separate capture used
   * click coordinates computed for a different browser window size than was
   * actually active. This test locks in the ALREADY-CORRECT behavior this
   * Finding's Root Cause Analysis proved, covering the two areas OH-1
   * through OH-4 above do not: Portal管理番号 (prototypePoNo, not
   * officialPoNo) search, and Clear returning to the unfiltered list. */
  test('OH-5 (Finding B Root Cause Fix): Portal管理番号 search narrows correctly, and Clear returns to the unfiltered list', async ({ page }) => {
    const draftId = await createOrderableDraft(page)
    await submitAndApprove(page, draftId)

    await page.goto(`/orders/${draftId}`)
    const managementNo = (await page.locator('h1').innerText()).split(' - ').pop()!.trim()
    expect(managementNo).toBeTruthy()

    // Portal管理番号 search (distinct from OH-3's 正式PO番号 search above) -
    // must narrow to exactly this Order, same Enter-triggered/URL-param-
    // backed mechanism.
    await openHistoryFilteredBy(page, managementNo)
    await expect(page).toHaveURL(new RegExp(`orderNoKeyword=${managementNo}`))
    // Any Filter change resets Page to 0 (OrderHistoryListPage.updateFilter's
    // own `next.set('page', '0')`) - Search composes correctly with
    // Pagination rather than only ever filtering whatever the current Page
    // happened to already be.
    await expect(page).toHaveURL(/page=0/)
    const rows = page.locator('tbody tr')
    await expect(rows).toHaveCount(1)
    await expect(rows.first().getByTestId('order-history-management-no')).toHaveText(managementNo)

    // Clear (empty the input, commit with Enter) - returns to the unfiltered
    // list, not stuck on the last search's zero/one-row result.
    const input = page.getByTestId('order-no-keyword-input').locator('input')
    await input.fill('')
    await input.press('Enter')
    await expect(page).not.toHaveURL(/orderNoKeyword=/)
    // The unfiltered list always has more than this one Order (every other
    // spec in this suite creates its own) - a strictly stronger assertion
    // than merely "not 1", which a coincidentally-empty DB could satisfy too.
    await expect(rows.nth(1)).toBeVisible()
  })

  test('OH-2 (Mobile): Management No. / Official PO No. / Revision remain distinct and readable at 390px width', async ({ page }) => {
    // Fixture setup (login/nav/draft creation) uses the Desktop nav testids
    // (nav-candidates, nav-logout, etc.) - mirroring mobile-responsive.spec.ts's
    // own convention, only the final History List view is actually checked
    // at Mobile width, which is what this scenario is about.
    const draftId = await createOrderableDraft(page)
    await submitAndApprove(page, draftId)
    const officialPoNo = await issueOfficialPoNo(page, draftId)

    await page.setViewportSize(MOBILE_VIEWPORT)
    await openHistoryFilteredBy(page, officialPoNo)
    // Post-Freeze Business Refinement: Card layout (not the Desktop Table)
    // below `sm` (600px) - no `tbody tr` exists on Mobile anymore.
    const row = page.locator('[data-testid^="order-history-row-"]').first()
    await expect(row).toBeVisible()
    await expect(row.getByTestId('order-history-management-no')).toBeVisible()
    await expect(row.getByTestId('order-history-official-po-no')).toHaveText(officialPoNo)
    await expect(row.getByTestId('order-history-revision')).toHaveText('1')
  })
})
