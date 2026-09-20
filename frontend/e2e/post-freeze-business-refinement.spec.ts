import { test, expect, type Page } from '@playwright/test'

/**
 * G-OPS Post-Freeze Business Refinement / Implementation 1
 * (docs/gops-post-freeze-business-refinement-implementation.md) - covers
 * the Business Scenarios from the implementation instructions that are new
 * this round:
 *
 *  - Mobile Approval UX (Scenario A/B): Order History List's new Mobile
 *    Card layout, and Order Detail's restructured 3-row Mobile Header
 *    (back link / static title / number+status), both below `sm` (600px).
 *  - Stockout / Restock Date (Scenario C/D/E): Legacy Expected Arrival
 *    (Type A, READ ONLY, wins display priority) vs Portal Manual Expected
 *    Restock (Type C, editable, with an explicit "未定" state) on Candidate
 *    List + SKU Detail.
 *
 * Ground truth (re-confirmed against the Legacy Demo DB while writing this
 * spec, both SKU_ALPHA/BR_OUTDOOR): OD-TENT-002 has an open (stk_in_date
 * IS NULL) Arrival with a real eta - Scenario C. OD-TENT-001's own Arrival
 * (PO-OUTDOOR-01) is already stocked in (stk_in_date set), so it has no
 * Legacy Expected Arrival - used for Scenario D/E (Manual entry never
 * interfered with by Legacy).
 *
 * Scenario F (Audit Trail) and G/H (Formal PO auto-number-only / Reissue
 * keeps the same PO number) are NOT duplicated here: F is already covered
 * by the Backend's own SkuRestockExpectationServiceIntegrationTest
 * (changeIsAudited_scenarioF), and G/H are already exercised by
 * official-po-integration.spec.ts (every `official-po-request-button`
 * assertion there already checks the PO No. auto-populates in
 * `[A-Z]{3}[A-Z]{3}\d{3}` form with no manual number input step, and
 * Scenario K covers Reissue).
 */

const OPERATOR_USERNAME = 'purchase01'
const OPERATOR_PASSWORD = 'DemoPass123!'
const ADMIN_USERNAME = 'admin01'
const ADMIN_PASSWORD = 'DemoPass123!'
const MOBILE_VIEWPORT = { width: 390, height: 844 }

const SKU_WITH_LEGACY_ARRIVAL = 'OD-TENT-002'
const SKU_WITHOUT_LEGACY_ARRIVAL = 'OD-TENT-001'
const BRAND_OUTDOOR = 'BR_OUTDOOR'

async function login(page: Page, username: string, password: string) {
  await page.goto('/')
  await page.getByLabel('ユーザー名').fill(username)
  await page.getByLabel('パスワード').fill(password)
  await page.getByRole('button', { name: 'ログイン' }).click()
  const viewport = page.viewportSize()
  const isMobile = (viewport?.width ?? 0) < 900
  if (isMobile) {
    await expect(page.getByTestId('mobile-nav-open-button')).toBeVisible()
  } else {
    await expect(page.getByTestId('nav-dashboard')).toBeVisible()
  }
}

async function assertNoHorizontalOverflow(page: Page, label: string) {
  const { scrollWidth, clientWidth } = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }))
  expect(scrollWidth, `${label}: page-level horizontal overflow (scrollWidth=${scrollWidth} > clientWidth=${clientWidth})`).toBeLessThanOrEqual(clientWidth + 1)
}

test.describe('Post-Freeze Business Refinement - Mobile Approval (390x844)', () => {
  test.use({ viewport: MOBILE_VIEWPORT })

  test('Scenario A/B: Order History List uses Mobile Card layout, Order Detail Mobile Header does not overflow', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.getByTestId('mobile-nav-open-button').click()
    await page.getByTestId('mobile-nav-history').click()
    await expect(page).toHaveURL(/\/orders\/history/)

    // Card layout (not the Desktop 12-column Table) below `sm`.
    await expect(page.getByTestId('order-history-cards')).toBeVisible()
    await expect(page.locator('table')).toHaveCount(0)
    await assertNoHorizontalOverflow(page, 'Order History List (Mobile Card layout)')

    const firstCard = page.locator('[data-testid^="order-history-row-"]').first()
    await expect(firstCard).toBeVisible()
    await firstCard.click()
    await expect(page).toHaveURL(/\/orders\/\d+/)

    // 3-row Mobile Header (back link / static title / number+status) -
    // replaces the Desktop single wrapping row that overflowed at
    // 375-430px (re-audit doc §3.2/§4.1).
    await expect(page.getByTestId('order-detail-header-mobile')).toBeVisible()
    await expect(page.getByTestId('portal-po-no-caption')).toBeInViewport()
    await assertNoHorizontalOverflow(page, 'Order Detail (Mobile Header)')
  })

  test('Scenario A/B: at the 375px floor too', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 })
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.getByTestId('mobile-nav-open-button').click()
    await page.getByTestId('mobile-nav-history').click()
    // Same settle wait mobile-responsive.spec.ts's own Overflow Audit uses
    // after every navigation - the Filter Stack's Keyword inputs re-sync
    // from the URL via a useEffect (existing pattern, unrelated to this
    // Round's Card layout) and can still be reflowing right after
    // navigation at this narrow a width.
    await page.waitForTimeout(300)
    await assertNoHorizontalOverflow(page, 'Order History List @ 375px')

    const firstCard = page.locator('[data-testid^="order-history-row-"]').first()
    await firstCard.scrollIntoViewIfNeeded()
    await firstCard.click()
    await expect(page).toHaveURL(/\/orders\/\d+/)
    await expect(page.getByTestId('order-detail-header-mobile')).toBeVisible()
    await assertNoHorizontalOverflow(page, 'Order Detail (Mobile Header) @ 375px')
  })
})

test.describe('Post-Freeze Business Refinement - Stockout / Restock Date', () => {
  test('Scenario C: Legacy Expected Arrival displays READ ONLY and wins over any Manual data', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)

    await page.goto(`/candidates?brandCode=${BRAND_OUTDOOR}&recommendedOnly=false`)
    const row = page.getByTestId(`candidate-row-${SKU_WITH_LEGACY_ARRIVAL}`)
    await expect(row).toBeVisible()
    const label = row.getByTestId('restock-label')
    await expect(label).toHaveAttribute('data-restock-source', 'LEGACY_EXPECTED_ARRIVAL')
    await expect(label).toContainText('入荷予定')

    await page.goto(`/items/${SKU_WITH_LEGACY_ARRIVAL}`)
    const section = page.getByTestId('sku-restock-section')
    await expect(section).toBeVisible()
    const sectionLabel = section.getByTestId('restock-label')
    await expect(sectionLabel).toHaveAttribute('data-restock-source', 'LEGACY_EXPECTED_ARRIVAL')
    // The Legacy READ ONLY note explains why the Edit form below it cannot
    // change what's currently displayed - never "Legacy"/"TR_ARR" wording,
    // only the ja business term.
    await expect(page.getByTestId('restock-legacy-readonly-note')).toBeVisible()
    await expect(page.getByTestId('restock-legacy-readonly-note')).toContainText('G-SYSの入荷情報')
  })

  test('Scenario D: Manual Expected Restock entry is reflected on SKU Detail and Candidate List', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)

    const target = new Date()
    target.setDate(target.getDate() + 21)
    const dateStr = target.toISOString().slice(0, 10)

    await page.goto(`/items/${SKU_WITHOUT_LEGACY_ARRIVAL}`)
    const section = page.getByTestId('sku-restock-section')
    await expect(section).toBeVisible()
    // No open Legacy Arrival for this SKU - the readonly note must not
    // appear, and the date field must be directly editable.
    await expect(page.getByTestId('restock-legacy-readonly-note')).toHaveCount(0)

    const unknownCheckbox = page.getByTestId('sku-restock-unknown-checkbox').locator('input')
    if (await unknownCheckbox.isChecked()) {
      await unknownCheckbox.uncheck()
    }
    await page.getByTestId('sku-restock-date-input').locator('input').fill(dateStr)
    await page.getByTestId('sku-restock-save-button').click()
    await expect(page.getByText('再入荷予定を更新しました。')).toBeVisible()

    const sectionLabel = section.getByTestId('restock-label')
    await expect(sectionLabel).toHaveAttribute('data-restock-source', 'PORTAL_MANUAL')
    await expect(sectionLabel).toContainText(dateStr)

    await page.goto(`/candidates?brandCode=${BRAND_OUTDOOR}&recommendedOnly=false`)
    const row = page.getByTestId(`candidate-row-${SKU_WITHOUT_LEGACY_ARRIVAL}`)
    await expect(row).toBeVisible()
    const rowLabel = row.getByTestId('restock-label')
    await expect(rowLabel).toHaveAttribute('data-restock-source', 'PORTAL_MANUAL')
    await expect(rowLabel).toContainText(dateStr)
  })

  test('Scenario E: marking 未定 (Unknown) explicitly is reflected on SKU Detail and Candidate List', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)

    await page.goto(`/items/${SKU_WITHOUT_LEGACY_ARRIVAL}`)
    const section = page.getByTestId('sku-restock-section')
    await expect(section).toBeVisible()

    await page.getByTestId('sku-restock-unknown-checkbox').locator('input').check()
    // Checking Unknown clears the date input in the same form (mutual
    // exclusion enforced both client-side and by the Backend's
    // InvalidSkuExpectedRestockException).
    await expect(page.getByTestId('sku-restock-date-input').locator('input')).toHaveValue('')
    await page.getByTestId('sku-restock-save-button').click()
    await expect(page.getByText('再入荷予定を更新しました。')).toBeVisible()

    const sectionLabel = section.getByTestId('restock-label')
    await expect(sectionLabel).toHaveAttribute('data-restock-source', 'PORTAL_MANUAL_UNKNOWN')
    await expect(sectionLabel).toContainText('再入荷予定：未定')

    await page.goto(`/candidates?brandCode=${BRAND_OUTDOOR}&recommendedOnly=false`)
    const row = page.getByTestId(`candidate-row-${SKU_WITHOUT_LEGACY_ARRIVAL}`)
    const rowLabel = row.getByTestId('restock-label')
    await expect(rowLabel).toHaveAttribute('data-restock-source', 'PORTAL_MANUAL_UNKNOWN')
    await expect(rowLabel).toContainText('再入荷予定：未定')
  })

  test('Scenario C (Order Detail): an Approval Detail line for a SKU with a Legacy Expected Arrival shows it', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.goto(`/candidates?brandCode=${BRAND_OUTDOOR}&recommendedOnly=false`)
    await page.getByTestId(`candidate-checkbox-${SKU_WITH_LEGACY_ARRIVAL}`).locator('input').check()
    await page.getByTestId('create-draft-button').click()
    await expect(page).toHaveURL(/\/orders\/drafts\/\d+(\?.*)?$/)
    await page.getByTestId(`order-qty-input-${SKU_WITH_LEGACY_ARRIVAL}`).locator('input').fill('2')
    await page.getByTestId('save-draft-button').click()
    await expect(page.getByText('保存しました。')).toBeVisible()
    await page.getByTestId('submit-for-approval-button').click()
    await page.getByTestId('submit-for-approval-dialog-confirm').click()
    await expect(page).toHaveURL(/\/orders\/\d+(\?.*)?$/)

    // Desktop Table: the new Restock column, appended after the
    // pre-existing openArrival column.
    const row = page.locator('tbody tr', { has: page.getByTestId(`sku-detail-link-${SKU_WITH_LEGACY_ARRIVAL}`) })
    const rowLabel = row.getByTestId('restock-label')
    await expect(rowLabel).toHaveAttribute('data-restock-source', 'LEGACY_EXPECTED_ARRIVAL')
  })
})
