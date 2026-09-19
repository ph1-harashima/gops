import { test, expect, type Page } from '@playwright/test'

/**
 * G-OPS Information Architecture Phase 2
 * (docs/gops-master-maintenance-hub-implementation.md): Master Maintenance
 * Hub - "Master Maintenance -> Supplier一覧 -> Supplier選択 -> Supplier
 * Settings", reusing the 4 existing Master screens (Supplier Contact/
 * Manufacturer Channel/Region Classification/Official PO Short Code) as-is
 * via their own optional `supplierCodeFilter` prop - their Backend Service/
 * API/Validation is completely untouched.
 *
 * Supplier Code/Name/Brand associations referenced below are Legacy `ms_comm`
 * ground truth (re-confirmed directly against the Demo MySQL DB while
 * writing this phase): SUP_ALPHA supplies BR_KITCHEN/BR_OUTDOOR, SUP_BETA
 * supplies BR_HOME/BR_OUTDOOR, SUP_GAMMA supplies BR_HOME/BR_KITCHEN - and
 * V30's demo seed pre-registers each Supplier's own Official PO Short Code
 * (SUP_ALPHA=ALP, SUP_BETA=BET, SUP_GAMMA=GAM), so these facts are stable
 * across the whole Demo DB's lifetime, unlike the ever-growing Order/Price
 * Change/Contact row counts this suite otherwise avoids hardcoding.
 */

const ADMIN_USERNAME = 'admin01'
const ADMIN_PASSWORD = 'DemoPass123!'
const OPERATOR_USERNAME = 'purchase01'
const OPERATOR_PASSWORD = 'DemoPass123!'

async function login(page: Page, username: string, password: string) {
  await page.goto('/')
  await page.getByLabel('ユーザー名').fill(username)
  await page.getByLabel('パスワード').fill(password)
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page.getByTestId('nav-dashboard')).toBeVisible()
}

test.describe('IA Phase 2: Master Maintenance Hub', () => {
  test('Scenario 1: Supplier一覧 -> Supplier選択 -> Contacts/Communication/Region/PO Code, Context維持', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.getByTestId('nav-master-maintenance').click()
    await page.getByTestId('nav-admin-suppliers').click()
    await expect(page).toHaveURL(/\/master\/suppliers$/)
    await expect(page.getByTestId('supplier-master-row-SUP_ALPHA')).toBeVisible()

    await page.getByTestId('supplier-master-link-SUP_ALPHA').click()
    await expect(page).toHaveURL(/\/master\/suppliers\/SUP_ALPHA\/overview/)
    await expect(page.getByTestId('supplier-settings-context-name')).toContainText('東和ライフサプライ株式会社')
    await expect(page.getByTestId('supplier-settings-context-code')).toContainText('SUP_ALPHA')
    // Overview: Brands derived from actual Order Candidate/Order data, never fabricated.
    await expect(page.getByTestId('supplier-overview-brand-BR_KITCHEN')).toBeVisible()
    await expect(page.getByTestId('supplier-overview-brand-BR_OUTDOOR')).toBeVisible()

    for (const tab of ['contacts', 'communication', 'region', 'po-code'] as const) {
      await page.getByTestId(`supplier-settings-tab-${tab}`).click()
      await expect(page).toHaveURL(new RegExp(`/master/suppliers/SUP_ALPHA/${tab}`))
      // Context header never disappears while moving between Tabs.
      await expect(page.getByTestId('supplier-settings-context-code')).toContainText('SUP_ALPHA')
    }

    // PO Code tab: reuses OfficialPoShortCodePage as-is, scoped to just this
    // Supplier's own row (codeType=SUPPLIER) - the Demo seed's ALP.
    await expect(page.getByTestId('official-po-short-code-table-container').getByText('ALP', { exact: true })).toBeVisible()
  })

  test('Scenario 2: 別Supplierを選択すると設定内容が正しく切り替わる', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto('/master/suppliers/SUP_ALPHA/overview')
    await expect(page.getByTestId('supplier-overview-brand-BR_KITCHEN')).toBeVisible()

    await page.getByTestId('supplier-settings-back-to-list').click()
    await expect(page).toHaveURL(/\/master\/suppliers$/)
    await page.getByTestId('supplier-master-link-SUP_BETA').click()
    await expect(page).toHaveURL(/\/master\/suppliers\/SUP_BETA\/overview/)

    await expect(page.getByTestId('supplier-settings-context-code')).toContainText('SUP_BETA')
    await expect(page.getByTestId('supplier-overview-brand-BR_HOME')).toBeVisible()
    await expect(page.getByTestId('supplier-overview-brand-BR_OUTDOOR')).toBeVisible()
    // SUP_ALPHA-only Brand must not leak into SUP_BETA's Overview.
    await expect(page.getByTestId('supplier-overview-brand-BR_KITCHEN')).toHaveCount(0)
    await expect(page.getByTestId('supplier-overview-tab').getByText('BET', { exact: true })).toBeVisible()
  })

  test('Scenario 3: メーカー担当者 - DefaultではActiveのみ、明示操作でInactiveも確認可能', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto('/master/suppliers/SUP_GAMMA/contacts')

    // Unique per run - this suite (and repeated manual verification runs
    // this Phase) already leaves several same-named "Hub E2E Contact" rows
    // behind for SUP_GAMMA, so every locator below must key off THIS run's
    // own unique name, never the shared literal string.
    const uniqueName = `Hub E2E Contact ${Date.now()}`
    await page.getByTestId('supplier-contact-create-button').click()
    await expect(page.getByTestId('supplier-contact-supplierCode').locator('input')).toHaveValue('SUP_GAMMA')
    await page.getByTestId('supplier-contact-contactName').locator('input').fill(uniqueName)
    await page.getByTestId('supplier-contact-email').locator('input').fill(`hub-e2e-${Date.now()}@example.com`)
    await page.getByTestId('supplier-contact-save').click()
    await expect(page.getByText(uniqueName)).toBeVisible()

    // Deactivate it, then confirm it disappears by default and reappears
    // only via the explicit "無効な項目も表示" toggle (IA Audit §8 principle).
    await page.getByRole('row', { name: new RegExp(uniqueName) }).getByText('編集').click()
    await page.getByTestId('supplier-contact-active').click()
    await page.getByTestId('supplier-contact-save').click()
    await expect(page.getByText(uniqueName)).toHaveCount(0)

    await page.getByTestId('supplier-contact-show-inactive').click()
    await expect(page.getByText(uniqueName)).toBeVisible()
  })

  test('Scenario 4: Supplier Settings - Browser Back/Forward でContext維持', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto('/master/suppliers/SUP_ALPHA/contacts')
    await page.getByTestId('supplier-settings-tab-po-code').click()
    await expect(page).toHaveURL(/\/master\/suppliers\/SUP_ALPHA\/po-code/)

    await page.goBack()
    await expect(page).toHaveURL(/\/master\/suppliers\/SUP_ALPHA\/contacts/)
    await expect(page.getByTestId('supplier-settings-context-code')).toContainText('SUP_ALPHA')

    await page.goForward()
    await expect(page).toHaveURL(/\/master\/suppliers\/SUP_ALPHA\/po-code/)
    await expect(page.getByTestId('supplier-settings-context-code')).toContainText('SUP_ALPHA')
  })

  test('Scenario 5: Global Settings（メールテンプレート）はSupplier Contextに依存しない', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto('/master/suppliers/SUP_ALPHA/overview')
    await page.getByTestId('nav-master-maintenance').click()
    await page.getByTestId('nav-admin-mail-templates').click()
    await expect(page).toHaveURL(/\/admin\/mail-templates$/)
    // No Supplier Context header/Chip anywhere on this Global screen.
    await expect(page.getByTestId('supplier-settings-context-code')).toHaveCount(0)
  })

  test('Scenario 6: Global Settings（Default CC）はSupplier Contextに依存しない', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto('/master/suppliers/SUP_ALPHA/overview')
    await page.getByTestId('nav-master-maintenance').click()
    await page.getByTestId('nav-admin-mail-settings').click()
    await expect(page).toHaveURL(/\/admin\/mail-settings$/)
    await expect(page.getByTestId('supplier-settings-context-code')).toHaveCount(0)
  })

  test('Scenario 12: 既存Master URLへ直接アクセスしても従来通り動作する（Backward Compatibility）', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    // Every pre-existing Route stays reachable, unfiltered, exactly as before -
    // no supplierCodeFilter prop is ever passed on these standalone Routes.
    for (const path of [
      '/admin/supplier-contacts',
      '/admin/manufacturer-channels',
      '/admin/supplier-region-classifications',
      '/admin/official-po-short-codes',
    ]) {
      await page.goto(path)
      await expect(page).toHaveURL(new RegExp(path.replace(/\//g, '\\/') + '$'))
      // None of these standalone screens ever render the new Hub's Context header.
      await expect(page.getByTestId('supplier-settings-context-code')).toHaveCount(0)
    }
  })

  test('OPERATOR cannot reach the Supplier Master Hub (ADMIN-only, mirrors every other Master Maintenance screen)', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await expect(page.getByTestId('nav-master-maintenance')).toHaveCount(0)

    const response = await page.request.get('/api/admin/suppliers')
    expect(response.status()).toBe(403)
    const body = await response.json()
    expect(body.errorCode).toBe('FORBIDDEN')
  })
})
