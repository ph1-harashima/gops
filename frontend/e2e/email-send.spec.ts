import { test, expect, type Page } from '@playwright/test'

/**
 * Phase 9-E: Real Email Send. Full chain: G-SYS連携準備 -> PO番号確定 ->
 * Excel生成 -> Manufacturer Channel=EMAIL -> Supplier Contact -> Mail
 * Template -> 送信. Uses LoggingEmailSenderAdapter (the only real Adapter
 * active in this environment - never opens a socket).
 */

const OPERATOR_USERNAME = 'purchase01'
const OPERATOR_PASSWORD = 'DemoPass123!'
const ADMIN_USERNAME = 'admin01'
const ADMIN_PASSWORD = 'DemoPass123!'
const SKU = 'OD-TENT-001' // SUP_ALPHA/BR_OUTDOOR

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

test.describe('Phase 9-E: Real Email Send', () => {
  test('ADMIN sends the Official PO Email to an EMAIL-channel manufacturer', async ({ page }) => {
    // --- Order setup: Candidate -> Draft -> 承認依頼 -> 承認 ---
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-candidates').click()
    await expect(page.getByTestId(`candidate-row-${SKU}`)).toBeVisible()
    await page.getByTestId(`candidate-checkbox-${SKU}`).locator('input').check()
    await page.getByTestId('create-draft-button').click()
    await expect(page).toHaveURL(/\/orders\/drafts\/(\d+)/)
    const draftId = page.url().match(/\/orders\/drafts\/(\d+)/)?.[1]
    expect(draftId).toBeTruthy()
    await page.getByTestId(`order-qty-input-${SKU}`).locator('input').fill('6')
    await page.getByTestId('save-draft-button').click()
    await expect(page.getByText('保存しました。')).toBeVisible()
    await page.getByTestId('submit-for-approval-button').click()
    await page.getByTestId('submit-for-approval-dialog-confirm').click()
    await logout(page)

    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto(`/orders/${draftId}`)
    await page.getByTestId('order-detail-approve-button').click()
    await page.getByTestId('approve-dialog-confirm').click()
    await expect(page.getByText('承認しました。')).toBeVisible()

    // --- Manufacturer Channel: EMAIL ---
    await page.getByTestId('nav-master-maintenance').click()
    await page.getByTestId('nav-admin-manufacturer-channels').click()
    await page.getByTestId('manufacturer-channel-create-button').click()
    await page.getByTestId('manufacturer-channel-supplierCode').locator('input').fill('SUP_ALPHA')
    await page.getByTestId('manufacturer-channel-brandCode').locator('input').fill('BR_OUTDOOR')
    await page.getByTestId('manufacturer-channel-save').click()
    await expect(page.getByTestId('manufacturer-channel-table-container')).toContainText('SUP_ALPHA')

    // --- Supplier Contact ---
    await page.goto('/admin/supplier-contacts')
    await page.getByTestId('supplier-contact-create-button').click()
    await page.getByTestId('supplier-contact-supplierCode').locator('input').fill('SUP_ALPHA')
    await page.getByTestId('supplier-contact-brandCode').locator('input').fill('BR_OUTDOOR')
    await page.getByTestId('supplier-contact-contactName').locator('input').fill('Taro Yamada')
    await page.getByTestId('supplier-contact-email').locator('input').fill('taro@example.com')
    await page.getByTestId('supplier-contact-save').click()
    await expect(page.getByTestId('supplier-contact-table-container')).toContainText('taro@example.com')

    // --- Mail Template ---
    await page.goto('/admin/mail-templates')
    await page.getByTestId('mail-template-create-button').click()
    await page.getByTestId('mail-template-templateName').locator('input').fill('PO Template')
    await page.getByTestId('mail-template-supplierCode').locator('input').fill('SUP_ALPHA')
    await page.getByTestId('mail-template-brandCode').locator('input').fill('BR_OUTDOOR')
    await page.getByTestId('mail-template-subjectTemplate').locator('input').fill('PO {{poNo}}')
    await page.getByTestId('mail-template-bodyTemplate').locator('textarea').first().fill('Dear {{contactName}}, PO No: {{poNo}}')
    await page.getByTestId('mail-template-save').click()
    await expect(page.getByTestId('mail-template-table-container')).toContainText('PO Template')

    // --- G-SYS連携準備 -> PO番号確定 -> Excel生成 ---
    await page.goto(`/orders/${draftId}`)
    // Phase 9-G: the "at a glance" panel's hint tracks each step in turn.
    await expect(page.getByTestId('next-action-hint')).toContainText('G-SYS連携準備')
    await page.getByTestId('official-po-request-button').click()
    await page.getByTestId('official-po-request-dialog-confirm').click()
    await expect(page.getByText('G-SYS連携の準備が完了しました。')).toBeVisible()
    await expect(page.getByTestId('next-action-hint')).toContainText('正式PO番号を確定')

    await page.getByTestId('official-po-number-input').locator('input').fill(`E2E-EMAIL-${draftId}`)
    await page.getByTestId('official-po-number-confirm-button').click()
    await expect(page.getByText('正式PO番号を確定しました。')).toBeVisible()
    await expect(page.getByTestId('at-a-glance-official-po-no')).toHaveText(`E2E-EMAIL-${draftId}`)
    await expect(page.getByTestId('next-action-hint')).toContainText('Excelを生成')

    await page.getByTestId('official-po-generate-button').click()
    await expect(page.getByText('正式PO Excelを生成しました。')).toBeVisible()
    // Email Send only ever requires the Excel (never a completed Import
    // Folder Handoff) and is prioritized ahead of that step in the hint.
    await expect(page.getByTestId('next-action-hint')).toContainText('メーカーへメールを送信')

    // --- 送信 ---
    await expect(page.getByTestId('resolved-manufacturer-channel-chip')).toHaveText('登録済み通信方法: Email')
    await page.getByTestId('mail-preview-button').click()
    await expect(page.getByTestId('mail-preview-result')).toBeVisible()

    await expect(page.getByTestId('email-send-section')).toBeVisible()
    await page.getByTestId('email-send-button').click()
    await expect(page.getByText('メールを送信しました。')).toBeVisible()
    await expect(page.getByTestId('email-sent-note')).toBeVisible()

    // Idempotent - the Button disappears once SENT (no re-send affordance
    // needed for a successful Send; a genuinely new Send only happens on a
    // future Revision). The hint now honestly reflects that G-SYS Import
    // Folder placement itself was never done in this flow (Email Send
    // never required it) - not a bug, Email and the G-SYS Handoff are
    // independent tracks.
    await expect(page.getByTestId('email-send-button')).toHaveCount(0)
    await expect(page.getByTestId('next-action-hint')).toContainText('G-SYS連携用ファイル')
  })

  /**
   * Gap Analysis C-5 (docs/gulliver-20260917-phase1-gap-analysis.md 10章,
   * Scenario 4 of the instruction's End-to-End Scenario list): Master
   * Contact selected -> Mail Preview -> To/CC Override -> Send Simulation
   * -> Master address retained unchanged -> actual recipient stored in
   * Audit. Reuses the SUP_ALPHA/BR_OUTDOOR EMAIL Channel/Contact/Template
   * the test above already registered (still active - the EDI-flip test
   * runs after this one, and cleanup runs last).
   */
  test('ADMIN overrides the To address at Send time - Master Contact is left unchanged', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-candidates').click()
    await expect(page.getByTestId(`candidate-row-${SKU}`)).toBeVisible()
    await page.getByTestId(`candidate-checkbox-${SKU}`).locator('input').check()
    await page.getByTestId('create-draft-button').click()
    await expect(page).toHaveURL(/\/orders\/drafts\/(\d+)/)
    const draftId = page.url().match(/\/orders\/drafts\/(\d+)/)?.[1]
    expect(draftId).toBeTruthy()
    await page.getByTestId(`order-qty-input-${SKU}`).locator('input').fill('4')
    await page.getByTestId('save-draft-button').click()
    await expect(page.getByText('保存しました。')).toBeVisible()
    await page.getByTestId('submit-for-approval-button').click()
    await page.getByTestId('submit-for-approval-dialog-confirm').click()
    await logout(page)

    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto(`/orders/${draftId}`)
    await page.getByTestId('order-detail-approve-button').click()
    await page.getByTestId('approve-dialog-confirm').click()
    await page.getByTestId('official-po-request-button').click()
    await page.getByTestId('official-po-request-dialog-confirm').click()
    await page.getByTestId('official-po-number-input').locator('input').fill(`E2E-OVERRIDE-${draftId}`)
    await page.getByTestId('official-po-number-confirm-button').click()
    await page.getByTestId('official-po-generate-button').click()
    await expect(page.getByText('正式PO Excelを生成しました。')).toBeVisible()

    await page.getByTestId('mail-preview-button').click()
    await expect(page.getByTestId('mail-preview-result')).toBeVisible()
    // The Master-selected Contact (taro@example.com, registered by the
    // first test in this file) is seeded into the editable field...
    await expect(page.getByTestId('mail-send-to-input').locator('input')).toHaveValue('taro@example.com')

    // ...but the user overrides it for this Send only.
    const overrideAddress = 'override-recipient@example.com'
    await page.getByTestId('mail-send-to-input').locator('input').fill(overrideAddress)
    await page.getByTestId('email-send-button').click()
    await expect(page.getByText('メールを送信しました。')).toBeVisible()

    // The Send actually used the Override, and the record makes clear it
    // was an Override (not silently indistinguishable from the normal path).
    await expect(page.getByTestId('email-recipient-override-note')).toBeVisible()
    await expect(page.getByTestId('email-recipient-override-note')).toContainText('taro@example.com')

    // Master Data itself (Supplier Contact) is completely untouched.
    await page.goto('/admin/supplier-contacts')
    await expect(page.getByTestId('supplier-contact-table-container')).toContainText('taro@example.com')
    await expect(page.getByTestId('supplier-contact-table-container')).not.toContainText(overrideAddress)
  })

  test('EDI-channel manufacturer never shows the Email Send section', async ({ page }) => {
    // Reuses SUP_ALPHA/BR_OUTDOOR (same Supplier/Brand the first test in
    // this file already registered as EMAIL) - flips that same Master row
    // to EDI instead of guessing at a second Supplier/Brand pair's
    // Legacy-side ownership, which historical Legacy Fixture data (multiple
    // Suppliers have shipped BR_KITCHEN over time) makes ambiguous.
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-candidates').click()
    await expect(page.getByTestId(`candidate-row-${SKU}`)).toBeVisible()
    await page.getByTestId(`candidate-checkbox-${SKU}`).locator('input').check()
    await page.getByTestId('create-draft-button').click()
    await expect(page).toHaveURL(/\/orders\/drafts\/(\d+)/)
    const draftId = page.url().match(/\/orders\/drafts\/(\d+)/)?.[1]
    expect(draftId).toBeTruthy()
    await page.getByTestId(`order-qty-input-${SKU}`).locator('input').fill('6')
    await page.getByTestId('save-draft-button').click()
    await expect(page.getByText('保存しました。')).toBeVisible()
    await page.getByTestId('submit-for-approval-button').click()
    await page.getByTestId('submit-for-approval-dialog-confirm').click()
    await logout(page)

    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto(`/orders/${draftId}`)
    await page.getByTestId('order-detail-approve-button').click()
    await page.getByTestId('approve-dialog-confirm').click()
    await expect(page.getByText('承認しました。')).toBeVisible()

    await page.getByTestId('nav-master-maintenance').click()
    await page.getByTestId('nav-admin-manufacturer-channels').click()
    await page.getByTestId('manufacturer-channel-table-container').getByText('SUP_ALPHA').first().waitFor()
    // The table lists every row regardless of Status (Active/Inactive) -
    // a deactivated leftover from an earlier full-suite run can share this
    // same Supplier/Brand text, so the filter must also require "有効"
    // (Active) to unambiguously target the one row this file's own
    // previous test just created (a plain hasText:'SUP_ALPHA' match alone
    // previously grabbed a stale Inactive row instead - real bug this
    // comment documents, not just defensive-programming for its own sake).
    const row = page.locator('[data-testid^="manufacturer-channel-row-"]').filter({ hasText: 'SUP_ALPHA' }).filter({ hasText: '有効' }).first()
    await row.getByRole('button', { name: '編集' }).click()
    await page.getByTestId('manufacturer-channel-channel').click()
    await page.getByRole('option', { name: 'EDI' }).click()
    await page.getByTestId('manufacturer-channel-save').click()
    await expect(page.getByTestId('manufacturer-channel-table-container')).toContainText('EDI')

    await page.goto(`/orders/${draftId}`)
    await expect(page.getByTestId('resolved-manufacturer-channel-chip')).toHaveText('登録済み通信方法: EDI')
    await expect(page.getByTestId('email-send-section')).toHaveCount(0)
  })

  // Runs last within this file (Playwright executes tests within one
  // describe block in declaration order by default, single worker in this
  // project's playwright.config.ts). Deactivates the SUP_ALPHA/BR_OUTDOOR
  // Supplier Contact / Mail Template / Manufacturer Channel rows the two
  // tests above created for real (E2E hits the actual running server, not
  // a rolled-back transaction) - other specs in a full-suite run
  // (supplier-contact-mail-template.spec.ts's own Scenario C/D, in
  // particular) assume SUP_ALPHA/BR_OUTDOOR starts with no active Contact/
  // Template configured yet, the same assumption this file's own tests
  // relied on when THEY ran. Deactivating (not deleting) matches every
  // Master's own Uniqueness policy (re-adding after deactivation is
  // explicitly allowed).
  test('cleanup: deactivate the Master data rows the tests above created', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)

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
      if (c.supplierCode === 'SUP_ALPHA' && c.brandCode === 'BR_OUTDOOR' && c.active) {
        await page.request.put(`/api/admin/supplier-contacts/${c.id}`, {
          data: { ...c, active: false },
        })
      }
    }

    const templates = await (await page.request.get('/api/admin/mail-templates')).json()
    for (const t of templates) {
      if (t.supplierCode === 'SUP_ALPHA' && t.brandCode === 'BR_OUTDOOR' && t.active) {
        await page.request.put(`/api/admin/mail-templates/${t.id}`, {
          data: { ...t, active: false },
        })
      }
    }
  })
})
