import { test, expect, type Page } from '@playwright/test'

/**
 * G-OPS Phase1 Mobile Responsive Audit (docs/gops-admin-mobile-responsive-audit.md).
 *
 * Two things this file verifies, kept in one place since both exist purely
 * to catch a Mobile Responsive regression (neither touches Business Logic):
 *
 *  1. A parameterized Horizontal Overflow audit across the 4 required
 *     viewports (375/390/430/768) plus a 1440x900 Desktop regression check,
 *     over every STABLE (not order-ID-dependent) target route. The single
 *     shared AppBar Header was the sole source of overflow before the
 *     Hamburger+Drawer Navigation fix (App.tsx) - this is the permanent
 *     regression gate for that fix and every route added since.
 *  2. The six explicit Mobile Scenarios (M1-M6), each driven at 390x844
 *     exactly like a phone-using Admin/Approver would use them - these
 *     create their own fresh Order/Master fixtures rather than relying on
 *     specific pre-existing Demo Order IDs, which drift as other Suites run.
 */

const OPERATOR_USERNAME = 'purchase01'
const OPERATOR_PASSWORD = 'DemoPass123!'
const ADMIN_USERNAME = 'admin01'
const ADMIN_PASSWORD = 'DemoPass123!'
const MOBILE_VIEWPORT = { width: 390, height: 844 }

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

async function logout(page: Page) {
  const viewport = page.viewportSize()
  if ((viewport?.width ?? 0) < 900) {
    await page.getByTestId('mobile-nav-open-button').click()
    await page.getByTestId('mobile-nav-logout').click()
  } else {
    await page.getByTestId('nav-logout').click()
  }
  await expect(page.getByLabel('ユーザー名')).toBeVisible()
}

/** Freeze Blocker-2 (Test Data Lifecycle, docs/gops-phase1-final-cleanup-report.md):
 * reuse a prior-run's Manufacturer Channel row instead of unconditionally
 * creating a new one every run - see manufacturer-channel.spec.ts's own
 * copy of this helper for the full rationale (duplicated per this project's
 * existing per-file convention). Uses page.goto (not the Nav menu) for the
 * fallback Create path, matching this file's own Mobile-viewport-agnostic
 * convention for reaching /admin/* screens directly. */
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
  await page.goto('/admin/manufacturer-channels')
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

async function assertNoHorizontalOverflow(page: Page, label: string) {
  const { scrollWidth, clientWidth } = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }))
  expect(scrollWidth, `${label}: page-level horizontal overflow (scrollWidth=${scrollWidth} > clientWidth=${clientWidth})`).toBeLessThanOrEqual(clientWidth + 1)
}

async function createOrderableDraft(page: Page, sku: string, brandCode: string): Promise<string> {
  await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
  await page.getByTestId('mobile-nav-open-button').click()
  await page.getByTestId('mobile-nav-candidates').click()
  await page.getByTestId(`order-candidate-brand-link-${brandCode}`).click()
  // Finding #6: below `sm` this screen renders Cards (candidate-card-*),
  // never the Desktop Table (candidate-row-*) - this describe block always
  // runs at 390x844, so every candidate lookup in this file targets the
  // Card testid.
  await expect(page.getByTestId(`candidate-card-${sku}`)).toBeVisible()
  await page.getByTestId(`candidate-checkbox-${sku}`).locator('input').check()
  await page.getByTestId('create-draft-button').click()
  await expect(page).toHaveURL(/\/orders\/drafts\/\d+(\?.*)?$/)
  const draftId = page.url().match(/\/orders\/drafts\/(\d+)/)?.[1]
  expect(draftId).toBeTruthy()
  await page.getByTestId(`order-qty-input-${sku}`).locator('input').fill('4')
  await page.getByTestId('save-draft-button').click()
  await expect(page.getByText('保存しました。')).toBeVisible()
  await page.getByTestId('submit-for-approval-button').click()
  await page.getByTestId('submit-for-approval-dialog-confirm').click()
  await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))
  return draftId!
}

/** Post-Freeze Visual Walkthrough Findings Fix (Finding #5): a single-SKU
 * Order never actually scrolls, so M1 alone cannot prove the Approval
 * Action Bar stays reachable "while scrolling a long SKU list" - this
 * selects every BR_HOME candidate SKU (6 lines, all their Demo fixture
 * defaults) to create a genuinely long, scrollable Order Detail line-card
 * list. */
async function createMultiSkuOrderableDraft(page: Page, skus: string[], brandCode: string): Promise<string> {
  await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
  await page.getByTestId('mobile-nav-open-button').click()
  await page.getByTestId('mobile-nav-candidates').click()
  await page.getByTestId(`order-candidate-brand-link-${brandCode}`).click()
  for (const sku of skus) {
    await expect(page.getByTestId(`candidate-card-${sku}`)).toBeVisible()
    await page.getByTestId(`candidate-checkbox-${sku}`).locator('input').check()
  }
  await page.getByTestId('create-draft-button').click()
  await expect(page).toHaveURL(/\/orders\/drafts\/\d+(\?.*)?$/)
  const draftId = page.url().match(/\/orders\/drafts\/(\d+)/)?.[1]
  expect(draftId).toBeTruthy()
  await page.getByTestId('submit-for-approval-button').click()
  await page.getByTestId('submit-for-approval-dialog-confirm').click()
  await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))
  return draftId!
}

test.describe('Mobile Responsive Overflow Audit', () => {
  const VIEWPORTS = [
    { name: '375x667', width: 375, height: 667 },
    { name: '390x844', width: 390, height: 844 },
    { name: '430x932', width: 430, height: 932 },
    { name: '768x1024', width: 768, height: 1024 },
    { name: '1440x900 (Desktop regression)', width: 1440, height: 900 },
  ]

  // Stable routes only (no order/draft IDs, which drift as other Suites run).
  const ROUTES = [
    '/',
    '/candidates',
    '/candidates?brandCode=BR_HOME',
    '/orders/history',
    '/price-changes',
    '/arrivals',
    '/warehouse-stock',
    '/stock-sales',
    '/master/suppliers',
    '/master/suppliers/SUP_ALPHA/overview',
    '/master/suppliers/SUP_ALPHA/contacts',
    '/master/suppliers/SUP_ALPHA/communication',
    '/master/suppliers/SUP_ALPHA/region',
    '/master/suppliers/SUP_ALPHA/po-code',
    '/admin/mail-templates',
    '/admin/mail-settings',
  ]

  for (const vp of VIEWPORTS) {
    test(`no page-level horizontal overflow @ ${vp.name}`, async ({ page }) => {
      await page.setViewportSize({ width: vp.width, height: vp.height })
      await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
      for (const route of ROUTES) {
        await page.goto(route)
        await page.waitForTimeout(300)
        await assertNoHorizontalOverflow(page, route)
      }
    })
  }
})

test.describe('Mobile Scenarios (390x844)', () => {
  test.use({ viewport: MOBILE_VIEWPORT })

  test('M1: Login -> Dashboard -> Approval -> Order確認 -> Approve', async ({ page }) => {
    const draftId = await createOrderableDraft(page, 'HM-TOWEL-001', 'BR_HOME')
    await logout(page)

    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto(`/orders/${draftId}`)
    // Card layout (not the Desktop 12-column Table) below `md`.
    await expect(page.getByTestId('order-detail-line-cards')).toBeVisible()
    await assertNoHorizontalOverflow(page, 'Order Detail (Mobile Card layout)')
    // Approve button reachable via the sticky action bar without needing to
    // scroll past the line cards first.
    await expect(page.getByTestId('order-detail-approve-button')).toBeInViewport()
    await page.getByTestId('order-detail-approve-button').click()
    await expect(page.getByTestId('approve-dialog-confirm')).toBeVisible()
    await page.getByTestId('approve-dialog-confirm').click()
    await expect(page.getByText('承認しました。')).toBeVisible()
  })

  test('M1b (Finding #5): Approval Sticky Action Bar stays reachable while scrolling a long SKU list', async ({ page }) => {
    // All 4 SKUs must share one Supplier (MIXED_SUPPLIER_NOT_ALLOWED) - of
    // BR_HOME's 6 candidate SKUs, HM-MUG-*/HM-TOWEL-* are SUP_GAMMA while
    // HM-RUG-* is SUP_BETA, so only these 4 combine into a single Draft.
    const skus = ['HM-MUG-001', 'HM-MUG-002', 'HM-TOWEL-001', 'HM-TOWEL-002']
    const draftId = await createMultiSkuOrderableDraft(page, skus, 'BR_HOME')
    await logout(page)

    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto(`/orders/${draftId}`)
    await expect(page.getByTestId('order-detail-line-cards')).toBeVisible()
    await assertNoHorizontalOverflow(page, 'Order Detail with 6 SKU lines (Mobile Card layout)')

    // Before scrolling, the Action Bar is already reachable (matches M1).
    await expect(page.getByTestId('order-detail-approve-button')).toBeInViewport()

    // Scroll the last SKU card into view - a long SKU list is exactly the
    // case `position: sticky` silently failed at (Finding #5's Root Cause:
    // scrolled fully off-screen, `top: -130px`, at max scroll). Uses
    // Playwright's own scrollIntoViewIfNeeded (not window.scrollTo), since
    // the actual scrollable element is a NESTED container, not the
    // document body - the same nested-scroll-container layout that made
    // `position: sticky` fail in the first place.
    const lastSkuLink = page.getByTestId(`sku-detail-link-${skus[skus.length - 1]}`)
    await lastSkuLink.scrollIntoViewIfNeeded()
    await page.waitForTimeout(200)

    // The last SKU line card must still be readable...
    await expect(lastSkuLink).toBeInViewport()
    // ...and the Approve/修正/差し戻し Action Bar must NOT have scrolled away
    // with the content - `position: fixed` keeps it pinned regardless of
    // scroll position.
    await expect(page.getByTestId('order-detail-approve-button')).toBeInViewport()
    await expect(page.getByTestId('order-detail-edit-button')).toBeInViewport()
    await expect(page.getByTestId('order-detail-return-button')).toBeInViewport()
    await assertNoHorizontalOverflow(page, 'Order Detail scrolled to bottom (Mobile Card layout)')

    await page.getByTestId('order-detail-approve-button').click()
    await expect(page.getByTestId('approve-dialog-confirm')).toBeVisible()
    await page.getByTestId('approve-dialog-confirm').click()
    await expect(page.getByText('承認しました。')).toBeVisible()
  })

  test('M2: Order History -> 検索 -> Order Detail -> PO/Revision確認', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.getByTestId('mobile-nav-open-button').click()
    await page.getByTestId('mobile-nav-history').click()
    await assertNoHorizontalOverflow(page, 'Order History (Mobile)')
    await page.getByTestId('order-history-filter-brand').locator('input').fill('BR_HOME')
    await page.getByTestId('order-history-filter-brand').locator('input').blur()
    // Post-Freeze Business Refinement: Card layout (not the Desktop
    // 12-column Table) below `sm` (600px), same isCardLayout idiom as
    // Order Candidates Brand List - no `tbody tr` exists on Mobile anymore.
    const firstCard = page.locator('[data-testid^="order-history-row-"]').first()
    await expect(firstCard).toBeVisible()
    await firstCard.click()
    await expect(page).toHaveURL(/\/orders\/\d+/)
    await assertNoHorizontalOverflow(page, 'Order Detail from Order History (Mobile)')
    // Portal管理番号 vs 正式PO番号 must both still be readable on Mobile -
    // the restructured 3-row Mobile Header keeps this Chip visible.
    await expect(page.getByTestId('order-detail-header-mobile')).toBeVisible()
    await expect(page.getByText('Portal管理番号')).toBeVisible()
  })

  test('M3: Cancel Request -> Cancel Approval', async ({ page }) => {
    const draftId = await createOrderableDraft(page, 'OD-TENT-001', 'BR_OUTDOOR')
    await logout(page)
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto(`/orders/${draftId}`)
    await page.getByTestId('order-detail-approve-button').click()
    await page.getByTestId('approve-dialog-confirm').click()
    await expect(page.getByText('承認しました。')).toBeVisible()
    await page.getByTestId('official-po-request-button').click()
    await page.getByTestId('official-po-request-dialog-confirm').click()
    await expect(page.getByText('G-SYS連携の準備が完了しました。')).toBeVisible()
    await page.getByTestId('official-po-generate-button').click()
    await expect(page.getByText('正式PO Excelを生成しました。')).toBeVisible()

    await page.getByTestId('official-po-cancel-button').click()
    await expect(page.getByTestId('official-po-cancel-dialog-confirm')).toBeVisible()
    await assertNoHorizontalOverflow(page, 'Cancel Request Dialog (Mobile)')
    await page.getByTestId('official-po-cancel-reason-input').locator('textarea').first().fill('Mobile Scenario M3のテストキャンセル')
    await expect(page.getByTestId('official-po-cancel-dialog-confirm')).toBeEnabled()
    await page.getByTestId('official-po-cancel-dialog-confirm').click()
    await expect(page.getByTestId('official-po-cancel-requested-note')).toBeVisible()

    await page.getByTestId('official-po-cancel-approve-button').click()
    await expect(page.getByTestId('official-po-cancelled-note')).toBeVisible()

    // Finding #4: the final state's Primary Business Status must read
    // キャンセル済み at Order Detail top - `status` itself intentionally
    // stays APPROVED (Reissue eligibility/history preservation), so this
    // is only correct if the Chip actually consults the PO's own
    // lifecycleStatus, not just `status`.
    await expect(page.getByTestId('order-status-chip-cancelled')).toBeVisible()
    await expect(page.getByTestId('order-status-chip-cancelled')).toHaveText('キャンセル済み')

    // ...and the same Order's row in Order History (Mobile Card layout)
    // must not still say 承認済み.
    await page.getByTestId('mobile-nav-open-button').click()
    await page.getByTestId('mobile-nav-history').click()
    await page.getByTestId('order-history-filter-brand').locator('input').fill('BR_OUTDOOR')
    await page.getByTestId('order-history-filter-brand').locator('input').blur()
    const historyCard = page.getByTestId(`order-history-row-${draftId}`)
    await expect(historyCard).toBeVisible()
    await expect(historyCard.getByTestId('order-status-chip-cancelled')).toHaveText('キャンセル済み')
  })

  test('M4: Master Maintenance -> Supplier -> 5 Tabs in sequence, Context常時表示', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.getByTestId('mobile-nav-open-button').click()
    await page.getByTestId('mobile-nav-admin-suppliers').click()
    await assertNoHorizontalOverflow(page, 'Supplier List (Mobile)')
    await page.getByTestId('supplier-master-link-SUP_ALPHA').click()
    await expect(page).toHaveURL(/\/master\/suppliers\/SUP_ALPHA\/overview/)

    for (const tab of ['contacts', 'communication', 'region', 'po-code']) {
      await page.getByTestId(`supplier-settings-tab-${tab}`).click()
      await expect(page).toHaveURL(new RegExp(`/master/suppliers/SUP_ALPHA/${tab}`))
      // Supplier Context Header stays visible on every Tab (scrollable Tabs
      // must not push it off-screen).
      await expect(page.getByTestId('supplier-settings-context-name')).toBeVisible()
      await assertNoHorizontalOverflow(page, `Supplier Settings - ${tab} (Mobile)`)
    }
  })

  test('M5: 発注候補 -> Brand選択 -> Candidate確認', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('mobile-nav-open-button').click()
    await page.getByTestId('mobile-nav-candidates').click()
    // Brand Card layout (not the Desktop 7-column Table) below `sm`.
    await expect(page.getByTestId('order-candidate-brand-list-table-container')).toBeVisible()
    await assertNoHorizontalOverflow(page, 'Order Candidates Brand List (Mobile)')
    await page.getByTestId('order-candidate-brand-link-BR_KITCHEN').click()
    await expect(page).toHaveURL(/brandCode=BR_KITCHEN/)
    await assertNoHorizontalOverflow(page, 'Candidate List filtered by Brand (Mobile)')

    // Finding #6: below `sm`, the Candidate List uses a Card layout (not the
    // Desktop Table, which has no `tbody tr` on Mobile at all) - confirm
    // the required minimum fields are all present on a real card and that
    // a long list of them still causes no horizontal scroll.
    await expect(page.getByTestId('candidate-list-cards')).toBeVisible()
    await expect(page.getByTestId('candidate-list-table-container')).toHaveCount(0)
    const firstCard = page.locator('[data-testid^="candidate-card-"]').first()
    await expect(firstCard).toBeVisible()
    await expect(firstCard.getByText('現在庫')).toBeVisible()
    await expect(firstCard.getByText('当月販売数')).toBeVisible()
    await expect(firstCard.getByText('推奨発注数')).toBeVisible()
    await expect(firstCard.getByText('在庫判定')).toBeVisible()
    await expect(firstCard.getByText('入荷/再入荷予定')).toBeVisible()
    await assertNoHorizontalOverflow(page, 'Candidate Card List (Mobile)')
  })

  test('M6: メーカーへ送信 -> To/CC -> Domain Warning -> Final Confirmation', async ({ page }) => {
    const sku = 'HM-RUG-001'
    const brandCode = 'BR_HOME'
    const supplierCode = 'SUP_BETA'
    const draftId = await createOrderableDraft(page, sku, brandCode)
    await logout(page)

    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto(`/orders/${draftId}`)
    await page.getByTestId('order-detail-approve-button').click()
    await page.getByTestId('approve-dialog-confirm').click()
    await expect(page.getByText('承認しました。')).toBeVisible()

    await ensureManufacturerChannel(page, supplierCode, brandCode, 'EMAIL')

    await page.goto('/admin/supplier-contacts')
    await page.getByTestId('supplier-contact-create-button').click()
    await page.getByTestId('supplier-contact-supplierCode').locator('input').fill(supplierCode)
    await page.getByTestId('supplier-contact-brandCode').locator('input').fill(brandCode)
    await page.getByTestId('supplier-contact-contactName').locator('input').fill('Mobile Scenario M6 Contact')
    await page.getByTestId('supplier-contact-email').locator('input').fill('m6-mobile-scenario@example.com')
    await page.getByTestId('supplier-contact-save').click()
    await expect(page.getByTestId('supplier-contact-table-container')).toContainText('m6-mobile-scenario@example.com')

    await page.goto('/admin/mail-templates')
    await page.getByTestId('mail-template-create-button').click()
    await page.getByTestId('mail-template-templateName').locator('input').fill('Mobile M6 Template')
    await page.getByTestId('mail-template-supplierCode').locator('input').fill(supplierCode)
    await page.getByTestId('mail-template-brandCode').locator('input').fill(brandCode)
    await page.getByTestId('mail-template-subjectTemplate').locator('input').fill('PO {{poNo}}')
    await page.getByTestId('mail-template-bodyTemplate').locator('textarea').first().fill('Dear {{contactName}}, PO No: {{poNo}}')
    await page.getByTestId('mail-template-save').click()
    await expect(page.getByTestId('mail-template-table-container')).toContainText('Mobile M6 Template')

    await page.goto(`/orders/${draftId}`)
    await page.getByTestId('official-po-request-button').click()
    await page.getByTestId('official-po-request-dialog-confirm').click()
    await expect(page.getByText('G-SYS連携の準備が完了しました。')).toBeVisible()
    await page.getByTestId('official-po-generate-button').click()
    await expect(page.getByText('正式PO Excelを生成しました。')).toBeVisible()
    await page.getByTestId('official-po-pdf-generate-button').click()
    await expect(page.getByText('正式PO PDFを生成しました。')).toBeVisible()

    await page.getByTestId('mail-preview-button').click()
    await expect(page.getByTestId('mail-preview-result')).toBeVisible()

    const differentDomainTo = 'buyer@maker-different-domain.example'
    await page.getByTestId('mail-send-to-input').locator('input').fill(differentDomainTo)
    await page.getByTestId('email-send-button').click()
    await expect(page.getByTestId('email-domain-mismatch-warning')).toBeVisible()
    await assertNoHorizontalOverflow(page, 'Email Send Confirm Dialog with Domain Warning (Mobile)')

    await page.getByTestId('email-send-confirm-dialog-confirm').click()
    await expect(page.getByText('メールを送信しました。')).toBeVisible()
  })

  test('M7 (Finding #7): SKU Detail long product name does not consume most of First View', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto('/items/KT-BOWL-002')
    await expect(page.getByTestId('sku-detail-header-mobile')).toBeVisible()
    await assertNoHorizontalOverflow(page, 'SKU Detail long product name (Mobile)')

    // The full product name must still be present in the DOM (line-clamped
    // visually, never lost/truncated as text) - the underlying data, not a
    // shortened label.
    await expect(page.getByText('ステンレスボウル 5点セット(発注停止)')).toBeVisible()

    // Header height must leave meaningful room for content below it: before
    // this fix, SKU code + long product name shared a single row with two
    // Action buttons, squeezing 商品名 into a narrow column and wrapping it
    // across most of a 844px First View. Line-clamping to 3 lines plus a
    // dedicated small SKU line caps the header at a small, bounded fraction
    // of the viewport instead.
    const headerBox = await page.getByTestId('sku-detail-header-mobile').boundingBox()
    expect(headerBox).not.toBeNull()
    expect(headerBox!.height).toBeLessThan(250)

    // Stock/Sales/Stockout info must appear "relatively soon" - visible
    // without scrolling past the header at all.
    await expect(page.getByText('在庫情報')).toBeInViewport()
  })

  test('M8 (Finding #3/G): Supplier Response save button stays reachable despite the unsaved-changes Warning', async ({ page }) => {
    const sku = 'HM-MUG-001'
    const draftId = await createOrderableDraft(page, sku, 'BR_HOME')
    await logout(page)

    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto(`/orders/${draftId}`)
    await page.getByTestId('order-detail-approve-button').click()
    await page.getByTestId('approve-dialog-confirm').click()
    await expect(page.getByText('承認しました。')).toBeVisible()

    const goToPreview = page.getByTestId('order-detail-primary-action')
    await expect(goToPreview).toHaveText('PO プレビューを見る')
    await goToPreview.click()
    await expect(page).toHaveURL(new RegExp(`/orders/drafts/${draftId}/preview(\\?.*)?$`))
    await page.getByTestId('demo-send-button').click()
    await page.getByTestId('demo-send-dialog-confirm').click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))

    const enterSupplierResponse = page.getByTestId('order-detail-primary-action')
    await expect(enterSupplierResponse).toHaveText('メーカー回答を入力')
    await enterSupplierResponse.click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}/supplier-response(\\?.*)?$`))

    // G-OPS Visual Re-Review Final Correction (Finding A): below `sm` this
    // screen renders a 1-SKU-per-Card layout (response-cards /
    // response-card-*), never the Desktop Table - the Desktop Table's own
    // <table> element must be entirely absent, not just visually squeezed.
    await expect(page.getByTestId('response-cards')).toBeVisible()
    await expect(page.getByTestId(`response-card-${sku}`)).toBeVisible()
    await expect(page.locator('table')).toHaveCount(0)

    // Confirmed Delivery Date input must also be directly usable on the
    // Card (not just Confirmed Qty) - same readOnly/isEditable gating as
    // the Desktop Table's equivalent cell.
    await expect(page.getByTestId(`confirmed-delivery-input-${sku}`).locator('input')).toBeEditable()

    // Type into the confirmed Qty field WITHOUT saving yet - this is
    // exactly the state (isDirty === true) that previously showed the
    // unsaved-changes Warning directly on top of the Save button.
    await page.getByTestId(`confirmed-qty-input-${sku}`).locator('input').fill('3')
    await expect(page.getByTestId('response-unsaved-changes-toast')).toBeVisible()
    await assertNoHorizontalOverflow(page, 'Supplier Response with unsaved-changes Warning (Mobile)')

    // Finding #3: scroll all the way down - exactly where the fixed-
    // position Warning Toast used to sit directly on top of the in-flow
    // Save button - and confirm the Save button is still reachable and
    // clickable there, not covered by the still-visible Warning.
    const saveButton = page.getByTestId('save-response-button')
    await saveButton.scrollIntoViewIfNeeded()
    await page.mouse.wheel(0, 2000)
    await expect(page.getByTestId('response-unsaved-changes-toast')).toBeVisible()
    await expect(saveButton).toBeInViewport()
    await saveButton.click({ trial: true }) // throws if another element would intercept the click
    await saveButton.click()
    await expect(page.getByText('回答を保存しました。')).toBeVisible()
  })
})
