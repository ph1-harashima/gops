import { test, expect, type Page } from '@playwright/test'

/**
 * Phase 7-C3 21章: the six Browser Acceptance Scenarios (A-F) for Supplier
 * Contact / Mail Template Foundation.
 *
 *   A. ADMIN -> Supplier Contact作成 -> Template作成 -> Order Detail ->
 *      Mail Preview -> To/CC/Subject/Body確認
 *   B. OPERATOR -> Master変更API -> 403
 *   C. Contact未設定Order -> Preview BLOCKED
 *   D. Template未設定 -> Preview BLOCKED
 *   E. officialPoNo未設定 -> Previewで正式PO番号未設定を確認
 *   F. 実メールが一切送信されていないことを確認
 *
 * Declared BELOW in C, D, A, B, E, F order (not A-F) - Supplier Contact/
 * Mail Template rows are real, persistent Master data (deliberately not
 * wiped by Demo Reset, docs/official-po-integration-foundation.md's sibling
 * design 20章), so C and D ("nothing configured yet" for SUP_ALPHA/
 * BR_OUTDOOR) must run BEFORE Scenario A creates real Contact/Template rows
 * for that same Supplier/Brand - otherwise A's leftover data would make C/D
 * observe "already resolved" instead of the BLOCKED state they exist to
 * prove. The Scenario labels/titles below still read A-F per the
 * instructions; only the file's physical declaration order differs.
 *
 * Note on Scenario A: officialPoNo is NEVER assigned by any real Portal
 * Flow this Phase (7-C2A's Gate carries forward unchanged) - so Subject/
 * Body can never actually render through the real browser UI even with
 * Contact+Template fully configured (MailPreviewService blocks rendering
 * whenever ANY blocker is present, and OFFICIAL_PO_NO_NOT_ASSIGNED is
 * always present). Scenario A therefore verifies To/CC resolve correctly
 * (independent of that Gate) and that Subject/Body correctly show as
 * blocked rather than fabricated content - the honest, correct behavior,
 * not a shortcut around it.
 *
 * Prerequisite: same as the other 7-C* specs - Demo Reset run first.
 */

const OPERATOR_USERNAME = 'purchase01'
const OPERATOR_PASSWORD = 'DemoPass123!'
const ADMIN_USERNAME = 'admin01'
const ADMIN_PASSWORD = 'DemoPass123!'

const SKU = 'OD-TENT-001' // SUP_ALPHA / BR_OUTDOOR

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

async function createApprovedOrder(page: Page): Promise<string> {
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

  await page.getByTestId('submit-for-approval-button').click()
  await page.getByTestId('submit-for-approval-dialog-confirm').click()
  await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))

  await logout(page)
  await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
  await page.goto(`/orders/${draftId}`)
  await page.getByTestId('order-detail-approve-button').click()
  await page.getByTestId('approve-dialog-confirm').click()
  await expect(page.getByText('承認しました。')).toBeVisible()

  return draftId!
}

// Random-enough SKU-independent unique email per test run so repeated CI
// runs never collide with a previously-registered active Contact.
function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 100000)}@example.com`
}

/** Supplier Contact / Mail Template are real, persistent Master data (not
 * wiped by Demo Reset) - every test in this file that creates one must
 * deactivate it afterward via the API (as ADMIN, already authenticated in
 * `page`'s context) so a repeat run of this SAME spec file (2x-stable
 * regression) doesn't see stale rows from the previous run and break
 * Scenario C/D's "nothing configured" premise. Deactivating (not deleting)
 * mirrors 7-C3 12章's own "Inactiveを推奨" policy - Resolution ignores
 * inactive rows either way. */
async function deactivateContactByEmail(page: Page, email: string) {
  const list = await (await page.request.get('/api/admin/supplier-contacts')).json()
  // email is already run-unique (uniqueEmail()), but match on active===true
  // too for the same defense-in-depth reason as deactivateTemplateByName.
  const match = list.find((c: { email: string; active: boolean }) => c.email === email && c.active)
  if (!match) return
  await page.request.put(`/api/admin/supplier-contacts/${match.id}`, { data: { ...match, active: false } })
}

async function deactivateTemplateByName(page: Page, templateName: string) {
  const list = await (await page.request.get('/api/admin/mail-templates')).json()
  // Match on active===true, not just the name - templateName is NOT unique
  // across repeated runs (unlike Contact's uniqueEmail()), so a previous
  // run's already-deactivated same-named row could otherwise be matched
  // instead of the one THIS run just created. The V10 migration's partial
  // UNIQUE index guarantees at most one ACTIVE row can ever exist for a
  // given (type, supplier, brand, language) identity, so this is
  // unambiguous.
  const match = list.find((t: { templateName: string; active: boolean }) => t.templateName === templateName && t.active)
  if (!match) return
  await page.request.put(`/api/admin/mail-templates/${match.id}`, { data: { ...match, active: false } })
}

test.describe('Phase 7-C3: Supplier Contact / Mail Template Foundation', () => {
  test('Scenario C: no Supplier Contact configured -> Mail Preview is BLOCKED', async ({ page }) => {
    const draftId = await createApprovedOrder(page)

    await page.goto(`/orders/${draftId}`)
    await page.getByTestId('mail-preview-button').click()

    await expect(page.getByText('有効なTo担当者（メーカー担当者Master）が見つかりません。')).toBeVisible()
    await expect(page.getByText('未確定の項目があるため、件名・本文は表示できません。')).toBeVisible()
  })

  test('Scenario D: Contact exists but no Mail Template configured -> Mail Preview is BLOCKED', async ({ page }) => {
    const draftId = await createApprovedOrder(page)
    const email = uniqueEmail('scenario-d')

    await page.goto('/admin/supplier-contacts')
    await page.getByTestId('supplier-contact-create-button').click()
    await page.getByTestId('supplier-contact-supplierCode').locator('input').fill('SUP_ALPHA')
    await page.getByTestId('supplier-contact-brandCode').locator('input').fill('BR_OUTDOOR')
    await page.getByTestId('supplier-contact-contactName').locator('input').fill('Scenario D Contact')
    await page.getByTestId('supplier-contact-email').locator('input').fill(email)
    await page.getByTestId('supplier-contact-save').click()
    await expect(page.getByTestId('supplier-contact-save')).toHaveCount(0)

    await page.goto(`/orders/${draftId}`)
    await page.getByTestId('mail-preview-button').click()

    await expect(page.getByText('有効なメールテンプレートが見つかりません。')).toBeVisible()
    await expect(page.getByText('未確定の項目があるため、件名・本文は表示できません。')).toBeVisible()

    await deactivateContactByEmail(page, email) // see helper's Javadoc-style comment above
  })

  test('Scenario A: ADMIN creates a Supplier Contact + Mail Template, then Mail Preview resolves To/CC (Subject/Body stay blocked - no Official PO No. yet)', async ({ page }) => {
    const draftId = await createApprovedOrder(page)
    // Still logged in as ADMIN.

    const toEmail = uniqueEmail('scenario-a-to')

    await page.goto('/admin/supplier-contacts')
    await page.getByTestId('supplier-contact-create-button').click()
    await page.getByTestId('supplier-contact-supplierCode').locator('input').fill('SUP_ALPHA')
    await page.getByTestId('supplier-contact-brandCode').locator('input').fill('BR_OUTDOOR')
    await page.getByTestId('supplier-contact-contactName').locator('input').fill('Scenario A Contact')
    await page.getByTestId('supplier-contact-email').locator('input').fill(toEmail)
    await page.getByTestId('supplier-contact-save').click()
    await expect(page.getByTestId('supplier-contact-save')).toHaveCount(0) // dialog closed

    await page.goto('/admin/mail-templates')
    await page.getByTestId('mail-template-create-button').click()
    await page.getByTestId('mail-template-templateName').locator('input').fill('Scenario A Template')
    await page.getByTestId('mail-template-supplierCode').locator('input').fill('SUP_ALPHA')
    await page.getByTestId('mail-template-brandCode').locator('input').fill('BR_OUTDOOR')
    await page.getByTestId('mail-template-subjectTemplate').locator('input').fill('PO {{poNo}}')
    await page.getByTestId('mail-template-bodyTemplate').locator('textarea').first().fill('{{contactName}} 様、PO No: {{poNo}}')
    await page.getByTestId('mail-template-save').click()
    await expect(page.getByTestId('mail-template-save')).toHaveCount(0)

    await page.goto(`/orders/${draftId}`)
    await page.getByTestId('mail-preview-button').click()
    await expect(page.getByTestId('mail-preview-result')).toBeVisible()
    await expect(page.getByTestId('mail-preview-result')).toContainText(toEmail)
    await expect(page.getByTestId('mail-preview-result')).toContainText('@portal-demo.invalid') // Admin CC
    // Subject/Body are never shown - officialPoNo is never assigned this Phase.
    await expect(page.getByText('未確定の項目があるため、件名・本文は表示できません。')).toBeVisible()

    await deactivateContactByEmail(page, toEmail)
    await deactivateTemplateByName(page, 'Scenario A Template')
  })

  test('Scenario B: OPERATOR calling the Master write APIs directly is rejected (403)', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)

    const contactResponse = await page.request.post('/api/admin/supplier-contacts', {
      data: { supplierCode: 'SUP_ALPHA', contactName: 'X', email: 'x@example.com', contactType: 'TO', language: 'ja', primary: false, active: true },
    })
    expect(contactResponse.status()).toBe(403)
    expect((await contactResponse.json()).errorCode).toBe('FORBIDDEN')

    const listResponse = await page.request.get('/api/admin/supplier-contacts')
    expect(listResponse.status()).toBe(403)

    const templateResponse = await page.request.post('/api/admin/mail-templates', {
      data: { templateName: 'X', templateType: 'PURCHASE_ORDER', language: 'ja', subjectTemplate: 'S', bodyTemplate: 'B', active: true },
    })
    expect(templateResponse.status()).toBe(403)
    expect((await templateResponse.json()).errorCode).toBe('FORBIDDEN')

    // The Nav entries themselves are also hidden for OPERATOR - including
    // the マスタメンテナンス submenu trigger itself (Phase 7-E Section 6/7).
    await page.goto('/')
    await expect(page.getByTestId('nav-master-maintenance')).toHaveCount(0)
    await expect(page.getByTestId('nav-admin-supplier-contacts')).toHaveCount(0)
    await expect(page.getByTestId('nav-admin-mail-templates')).toHaveCount(0)
  })

  test('Phase 7-E Section 6: ADMIN - マスタメンテナンス submenu exposes both Master screens', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)

    await page.getByTestId('nav-master-maintenance').click()
    await page.getByTestId('nav-admin-supplier-contacts').click()
    await expect(page).toHaveURL(/\/admin\/supplier-contacts$/)
    await expect(page.getByTestId('supplier-contact-create-button')).toBeVisible()

    await page.getByTestId('nav-master-maintenance').click()
    await page.getByTestId('nav-admin-mail-templates').click()
    await expect(page).toHaveURL(/\/admin\/mail-templates$/)
    await expect(page.getByTestId('mail-template-create-button')).toBeVisible()
  })

  test('Scenario E: officialPoNo not yet assigned -> Preview explicitly shows "正式PO番号未設定" as a Blocker', async ({ page }) => {
    const draftId = await createApprovedOrder(page)

    await page.goto(`/orders/${draftId}`)
    await page.getByTestId('mail-preview-button').click()

    await expect(page.getByText('正式PO番号が未設定です。')).toBeVisible()
  })

  test('Scenario F: after all of the above, no real mail was ever sent', async ({ page }) => {
    // Same Browser-observable-proxy approach as official-po-integration.spec.ts's
    // Scenario E: no Send API exists this Phase at all (7-C3 9章/19章), so
    // there is no endpoint capable of dispatching mail to begin with. This
    // Scenario confirms the Preview screen never claims otherwise - its own
    // subtitle explicitly states no real mail is sent.
    const draftId = await createApprovedOrder(page)

    await page.goto(`/orders/${draftId}`)
    await expect(page.getByText('実送信は行いません（Send APIは未実装です）。デモ送信とは別機能です。')).toBeVisible()
    await page.getByTestId('mail-preview-button').click()
    await expect(page.getByTestId('mail-preview-result')).toBeVisible()

    // No Send/送信 button exists anywhere on this Section.
    await expect(page.getByTestId('mail-preview-section').getByRole('button', { name: /送信/ })).toHaveCount(0)
  })
})
