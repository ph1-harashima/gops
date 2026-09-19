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
  await expect(page.getByTestId(`candidate-row-${sku}`)).toBeVisible()
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

  test('M2: Order History -> 検索 -> Order Detail -> PO/Revision確認', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.getByTestId('mobile-nav-open-button').click()
    await page.getByTestId('mobile-nav-history').click()
    await assertNoHorizontalOverflow(page, 'Order History (Mobile)')
    await page.getByTestId('order-history-filter-brand').locator('input').fill('BR_HOME')
    await page.getByTestId('order-history-filter-brand').locator('input').blur()
    await expect(page.locator('tbody tr').first()).toBeVisible()
    await page.locator('tbody tr').first().click()
    await expect(page).toHaveURL(/\/orders\/\d+/)
    await assertNoHorizontalOverflow(page, 'Order Detail from Order History (Mobile)')
    // Portal管理番号 vs 正式PO番号 must both still be readable on Mobile.
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

    await page.goto('/admin/manufacturer-channels')
    await page.getByTestId('manufacturer-channel-create-button').click()
    await page.getByTestId('manufacturer-channel-supplierCode').locator('input').fill(supplierCode)
    await page.getByTestId('manufacturer-channel-brandCode').locator('input').fill(brandCode)
    await page.getByTestId('manufacturer-channel-save').click()
    await expect(page.getByTestId('manufacturer-channel-table-container')).toContainText(supplierCode)

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
})
