import { test, expect, type Page } from '@playwright/test'

/**
 * G-OPS Post-Freeze Business Refinement / Implementation 2 (Manufacturer
 * Stockout Information Management) - covers the requirements doc's own
 * Scenario A-I (§28), building on Implementation 1
 * (post-freeze-business-refinement.spec.ts, unmodified/unbroken by this
 * round).
 *
 * Ground truth re-confirmed against the Demo DB this round: OD-TENT-002
 * (BR_OUTDOOR) has an open Legacy Expected Arrival (eta 2026-07-25);
 * OD-TENT-001 (BR_OUTDOOR) does not (its own Arrival is already stocked
 * in) - same fixtures Implementation 1 already established. Candidate
 * List does not exclude a SKU that already has an in-flight Order (it is
 * not a "remaining to order" list, it is a live Legacy Stock/Sales list),
 * so reusing these SKUs across scenarios in this file is safe.
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
  if ((viewport?.width ?? 0) < 900) {
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

async function openSkuDetail(page: Page, sku: string) {
  await page.goto(`/items/${sku}`)
  await expect(page.getByTestId('sku-restock-section')).toBeVisible()
}

const STATUS_LABEL: Record<string, string> = {
  STOCKOUT: '欠品',
  LONG_TERM_STOCKOUT: '長期欠品',
  RESOLVED: '解消',
  '': '（未設定）',
}
const CONTACT_METHOD_LABEL: Record<string, string> = {
  PHONE: '電話',
  EMAIL: 'メール',
  ORDER_RESPONSE: '発注回答',
  OTHER: 'その他',
}

async function saveStockout(page: Page, opts: {
  status?: 'STOCKOUT' | 'LONG_TERM_STOCKOUT' | 'RESOLVED' | ''
  date?: string
  unknown?: boolean
  shortageQty?: string
  receivedDate?: string
  contactMethod?: 'PHONE' | 'EMAIL' | 'ORDER_RESPONSE' | 'OTHER' | ''
  memo?: string
}) {
  if (opts.status !== undefined) {
    await page.getByTestId('sku-restock-status-select').click()
    await page.getByRole('option', { name: STATUS_LABEL[opts.status], exact: true }).click()
  }
  if (opts.unknown !== undefined) {
    const checkbox = page.getByTestId('sku-restock-unknown-checkbox').locator('input')
    const isChecked = await checkbox.isChecked()
    if (isChecked !== opts.unknown) await checkbox.click()
  }
  if (opts.date !== undefined) {
    await page.getByTestId('sku-restock-date-input').locator('input').fill(opts.date)
  }
  if (opts.shortageQty !== undefined) {
    await page.getByTestId('sku-restock-shortage-qty-input').locator('input').fill(opts.shortageQty)
  }
  if (opts.receivedDate !== undefined) {
    await page.getByTestId('sku-restock-received-date-input').locator('input').fill(opts.receivedDate)
  }
  if (opts.contactMethod !== undefined && opts.contactMethod !== '') {
    await page.getByTestId('sku-restock-contact-method-select').click()
    await page.getByRole('option', { name: CONTACT_METHOD_LABEL[opts.contactMethod], exact: true }).click()
  }
  if (opts.memo !== undefined) {
    await page.getByTestId('sku-restock-memo-input').locator('textarea').first().fill(opts.memo)
  }
  // The Edit form re-syncs every field from the freshly-refetched record
  // after a successful Save (by design - see SkuDetailPage.tsx's own
  // comment on this effect: it invalidates the restock-expectation query on
  // save, which refetches in the background). Waiting only for the Toast
  // is not enough - the invalidation-triggered GET can still resolve AFTER
  // the Toast appears and land its re-sync in between two rapid,
  // back-to-back saveStockout() calls, clobbering a following call's
  // freshly-typed value with the OLDER fetched one. Wait for that specific
  // GET to actually complete before returning, so no following call's
  // .fill() can ever race it.
  const refetch = page.waitForResponse((r) => r.url().includes('/restock-expectation') && r.request().method() === 'GET')
  await page.getByTestId('sku-restock-save-button').click()
  await expect(page.getByText('メーカー欠品情報を更新しました。')).toBeVisible()
  await refetch
}

test.describe('Manufacturer Stockout Information - Desktop', () => {
  test('Scenario A: pre-order phone call registers Long-term Stockout with Unknown restock, reflected on Candidate List', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await openSkuDetail(page, SKU_WITHOUT_LEGACY_ARRIVAL)

    await saveStockout(page, {
      status: 'LONG_TERM_STOCKOUT',
      unknown: true,
      receivedDate: '2026-09-20',
      contactMethod: 'PHONE',
      memo: '電話で長期欠品と確認',
    })

    const section = page.getByTestId('sku-restock-section')
    await expect(section.getByTestId('stockout-status-chip')).toHaveAttribute('data-stockout-status', 'LONG_TERM_STOCKOUT')
    await expect(section.getByTestId('restock-label')).toContainText('未定')
    await expect(section.getByTestId('manufacturer-confirmed-caption')).toContainText('2026-09-20')
    await expect(section.getByTestId('manufacturer-confirmed-caption')).toContainText('電話')

    await page.goto(`/candidates?brandCode=${BRAND_OUTDOOR}&recommendedOnly=false`)
    const row = page.getByTestId(`candidate-row-${SKU_WITHOUT_LEGACY_ARRIVAL}`)
    await expect(row.getByTestId('stockout-status-chip')).toHaveAttribute('data-stockout-status', 'LONG_TERM_STOCKOUT')
  })

  test('Scenario B/E: restock date set then revised, History keeps both entries', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await openSkuDetail(page, SKU_WITHOUT_LEGACY_ARRIVAL)

    // First: still unknown (Scenario A's state, or a fresh unknown here).
    await saveStockout(page, { status: 'LONG_TERM_STOCKOUT', unknown: true, receivedDate: '2026-09-21', contactMethod: 'EMAIL' })
    // Later: a real date becomes known.
    await saveStockout(page, { unknown: false, date: '2026-11-15', receivedDate: '2026-10-05', contactMethod: 'EMAIL' })
    // Later still: the date is revised (Scenario E).
    await saveStockout(page, { date: '2026-11-20', receivedDate: '2026-11-12', contactMethod: 'EMAIL' })

    const section = page.getByTestId('sku-restock-section')
    await expect(section.getByTestId('restock-label')).toContainText('2026-11-20')

    await page.getByTestId('sku-restock-view-history-button').click()
    const dialog = page.getByTestId('stockout-history-dialog')
    await expect(dialog).toBeVisible()
    const rows = dialog.locator('[data-testid^="stockout-history-row-"]')
    await expect(rows).toHaveCount(await rows.count())
    const count = await rows.count()
    expect(count).toBeGreaterThanOrEqual(3)
    // Oldest-first: earliest entry's own restock cell still shows Unknown,
    // not silently rewritten by the later real date - full snapshots, not
    // just a running total.
    await expect(rows.first()).toContainText('未定')
    await expect(rows.last()).toContainText('2026-11-20')
    await page.getByTestId('stockout-history-close-button').click()
  })

  test('Scenario F: RESOLVED transition keeps History, no record deletion', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await openSkuDetail(page, SKU_WITHOUT_LEGACY_ARRIVAL)

    await saveStockout(page, { status: 'RESOLVED', unknown: false, date: '' })

    const section = page.getByTestId('sku-restock-section')
    await expect(section.getByTestId('stockout-status-chip')).toHaveAttribute('data-stockout-status', 'RESOLVED')

    await page.getByTestId('sku-restock-view-history-button').click()
    const dialog = page.getByTestId('stockout-history-dialog')
    const rows = dialog.locator('[data-testid^="stockout-history-row-"]')
    const count = await rows.count()
    expect(count).toBeGreaterThanOrEqual(2)
    await expect(rows.last()).toContainText('解消')
  })

  test('Scenario G: Legacy Expected Arrival and an active Manufacturer Stockout status are both shown, flagged as a conflict', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await openSkuDetail(page, SKU_WITH_LEGACY_ARRIVAL)

    await saveStockout(page, {
      status: 'LONG_TERM_STOCKOUT',
      date: '2026-10-15',
      unknown: false,
      receivedDate: '2026-09-20',
      contactMethod: 'EMAIL',
      memo: 'still short per manufacturer',
    })

    const section = page.getByTestId('sku-restock-section')
    // Legacy's own line is still shown (READ ONLY note present)...
    await expect(page.getByTestId('restock-legacy-readonly-note')).toBeVisible()
    // ...AND the Manufacturer's own status/restock is shown too - neither
    // hides the other.
    await expect(section.getByTestId('stockout-status-chip')).toHaveAttribute('data-stockout-status', 'LONG_TERM_STOCKOUT')
    await expect(section.getByTestId('restock-conflict-warning')).toBeVisible()

    // Candidate List reflects the same conflict marker.
    await page.goto(`/candidates?brandCode=${BRAND_OUTDOOR}&recommendedOnly=false`)
    const row = page.getByTestId(`candidate-row-${SKU_WITH_LEGACY_ARRIVAL}`)
    await expect(row.getByTestId('restock-conflict-warning')).toBeVisible()
  })

  test('Scenario C/D: registering Stockout from a Supplier Response difference is a deliberate, separate action', async ({ page }) => {
    const sku = 'HM-TOWEL-001'
    const brandCode = 'BR_HOME'

    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)

    // Captured right after login (an authenticated request is required),
    // before anything else in this test runs, and compared (not asserted
    // null) after the Supplier Response Save below - sku_expected_restock
    // is Master-like data Demo Reset never truncates, so a prior run of
    // this same test may have already left a non-null baseline; what
    // Scenario D actually requires is that saving the Response alone never
    // CHANGES it, whatever it started as.
    const baseline = await (await page.request.get(`/api/items/${sku}/restock-expectation`)).json()

    await page.goto(`/candidates?brandCode=${brandCode}&recommendedOnly=false`)
    await expect(page.getByTestId(`candidate-row-${sku}`)).toBeVisible()
    await page.getByTestId(`candidate-checkbox-${sku}`).locator('input').check()
    await page.getByTestId('create-draft-button').click()
    await expect(page).toHaveURL(/\/orders\/drafts\/\d+(\?.*)?$/)
    const draftId = page.url().match(/\/orders\/drafts\/(\d+)/)?.[1]
    await page.getByTestId(`order-qty-input-${sku}`).locator('input').fill('20')
    await page.getByTestId('save-draft-button').click()
    await expect(page.getByText('保存しました。')).toBeVisible()
    await page.getByTestId('submit-for-approval-button').click()
    await page.getByTestId('submit-for-approval-dialog-confirm').click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))

    await page.getByTestId('nav-logout').click()
    await expect(page.getByLabel('ユーザー名')).toBeVisible()
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto(`/orders/${draftId}`)
    await page.getByTestId('order-detail-approve-button').click()
    await page.getByTestId('approve-dialog-confirm').click()
    await expect(page.getByText('承認しました。')).toBeVisible()

    // Demo Send lives on the PO Preview screen, reachable only via the
    // "PO プレビューを見る" primary Action that replaces the Approve button
    // once APPROVED (same required sequence core-demo-scenario.spec.ts
    // exercises - Demo Send is not directly available on Order Detail).
    const goToPreview = page.getByTestId('order-detail-primary-action')
    await expect(goToPreview).toHaveText('PO プレビューを見る')
    await goToPreview.click()
    await expect(page).toHaveURL(new RegExp(`/orders/drafts/${draftId}/preview(\\?.*)?$`))
    await page.getByTestId('demo-send-button').click()
    await page.getByTestId('demo-send-dialog-confirm').click()
    const enterSupplierResponse = page.getByTestId('order-detail-primary-action')
    await expect(enterSupplierResponse).toHaveText('メーカー回答を入力')
    await enterSupplierResponse.click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}/supplier-response(\\?.*)?$`))

    // Confirmed Qty (10) < Ordered Qty (20) - a real difference - but
    // Scenario D: saving the Response alone must NOT auto-create a
    // Stockout record.
    await page.getByTestId(`confirmed-qty-input-${sku}`).locator('input').fill('10')
    await page.getByTestId('save-response-button').click()
    await expect(page.getByText('回答を保存しました。')).toBeVisible()

    const afterResponseSave = await (await page.request.get(`/api/items/${sku}/restock-expectation`)).json()
    expect(afterResponseSave.stockoutStatus).toBe(baseline.stockoutStatus)
    expect(afterResponseSave.shortageQty).toBe(baseline.shortageQty)

    // Scenario C: the deliberate, separate registration action.
    await page.getByTestId(`register-stockout-button-${sku}`).click()
    const dialog = page.getByTestId('register-stockout-dialog')
    await expect(dialog).toBeVisible()
    await expect(dialog).toContainText('20')
    await expect(dialog).toContainText('10')
    await page.getByTestId('register-stockout-status-select').click()
    await page.getByRole('option', { name: '長期欠品', exact: true }).click()
    // Shortage Qty should already be prefilled with the Ordered-Confirmed
    // difference (10).
    await expect(page.getByTestId('register-stockout-shortage-qty-input').locator('input')).toHaveValue('10')
    await page.getByTestId('register-stockout-confirm-button').click()
    await expect(dialog).toBeHidden()

    const afterRegister = await page.request.get(`/api/items/${sku}/restock-expectation`)
    const afterBody = await afterRegister.json()
    expect(afterBody.stockoutStatus).toBe('LONG_TERM_STOCKOUT')
    expect(afterBody.shortageQty).toBe(10)
    expect(afterBody.contactMethod).toBe('ORDER_RESPONSE')
  })
})

test.describe('Manufacturer Stockout Information - Mobile (390x844)', () => {
  test.use({ viewport: MOBILE_VIEWPORT })

  test('Scenario H: Mobile Approval shows Manufacturer Stockout info without overflow, then Approve', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.goto(`/candidates?brandCode=${BRAND_OUTDOOR}&recommendedOnly=false`)
    await page.getByTestId(`candidate-checkbox-${SKU_WITH_LEGACY_ARRIVAL}`).locator('input').check()
    await page.getByTestId('create-draft-button').click()
    await expect(page).toHaveURL(/\/orders\/drafts\/\d+(\?.*)?$/)
    const draftId = page.url().match(/\/orders\/drafts\/(\d+)/)?.[1]
    await page.getByTestId(`order-qty-input-${SKU_WITH_LEGACY_ARRIVAL}`).locator('input').fill('2')
    await page.getByTestId('save-draft-button').click()
    await expect(page.getByText('保存しました。')).toBeVisible()
    await page.getByTestId('submit-for-approval-button').click()
    await page.getByTestId('submit-for-approval-dialog-confirm').click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))

    await page.getByTestId('mobile-nav-open-button').click()
    await page.getByTestId('mobile-nav-logout').click()
    await expect(page.getByLabel('ユーザー名')).toBeVisible()

    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto(`/orders/${draftId}`)
    await expect(page.getByTestId('order-detail-line-cards')).toBeVisible()
    await assertNoHorizontalOverflow(page, 'Order Detail Mobile Card with Manufacturer Stockout info')
    await expect(page.getByTestId('stockout-status-chip')).toBeVisible()

    await expect(page.getByTestId('order-detail-approve-button')).toBeInViewport()
    await page.getByTestId('order-detail-approve-button').click()
    await page.getByTestId('approve-dialog-confirm').click()
    await expect(page.getByText('承認しました。')).toBeVisible()
  })

  test('Scenario I: Mobile Candidate List shows Stockout status/restock/confirmation date without overflow', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.goto(`/candidates?brandCode=${BRAND_OUTDOOR}&recommendedOnly=false`)
    await expect(page.getByTestId(`candidate-row-${SKU_WITH_LEGACY_ARRIVAL}`)).toBeVisible()
    await assertNoHorizontalOverflow(page, 'Candidate List (Mobile) with Manufacturer Stockout info')
    const row = page.getByTestId(`candidate-row-${SKU_WITH_LEGACY_ARRIVAL}`)
    await expect(row.getByTestId('stockout-status-chip')).toBeVisible()
  })
})
