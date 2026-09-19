import { test, expect, type Page } from '@playwright/test'
import { execSync } from 'node:child_process'

/**
 * Gap Analysis Implementation Instruction, End-to-End Scenario 1 (docs/
 * gulliver-20260917-phase1-gap-analysis.md基準の実装指示書 5章): the full
 * normal order flow threaded through in ONE continuous run, composing every
 * Phase 1-9 feature this engagement added rather than re-testing each in
 * isolation (already covered by the dedicated specs each Phase's own commit
 * added - this file is Phase 10's own "do these actually work TOGETHER"
 * check):
 *
 *   Candidate -> SKU Detail (B-2) -> Draft -> 承認依頼 -> 承認 -> 正式PO番号確定
 *   -> Excel生成 -> PDF生成 (C-1) -> G-SYS連携用ファイル配置 (Import Folder Sim)
 *   -> G-SYS登録状況確認 (Integration Status Sim) -> Mail Preview -> To/CC確認
 *   -> メール送信 (Email Send Sim) -> デモ送信 (Order Status進行) -> メーカー回答
 *   -> 差異 (Difference) -> 合意 (Agreement) -> 入荷 (Arrival) -> 完納 (Completed)
 *
 * Scenario 2 (Official PO Reissue), Scenario 3 (Cancel), Scenario 4 (Email
 * To/CC Override) are each already covered end-to-end by their own dedicated
 * specs (official-po-integration.spec.ts Scenarios K/L, email-send.spec.ts's
 * second test) - not duplicated here. Scenario 5 (full regression) is the
 * full Backend/Frontend/E2E suite run itself, not a single spec file.
 */

const OPERATOR_USERNAME = 'purchase01'
const OPERATOR_PASSWORD = 'DemoPass123!'
const ADMIN_USERNAME = 'admin01'
const ADMIN_PASSWORD = 'DemoPass123!'
const SKU = 'OD-TENT-001' // SUP_ALPHA/BR_OUTDOOR - also the Fulfillment Foundation's own 完納 Legacy Fixture SKU

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

/** Same disposable-Prototype-Postgres-only technique fulfillment-follow-up-
 * foundation.spec.ts's own linkOrderToOfficialPoNo helper uses (see that
 * file's Javadoc) - simulates the Legacy TR_PO/TR_INV "完納" match for
 * Arrival/Completed without writing to Legacy itself (writes only to the
 * local disposable Prototype Postgres, never reachable through any Portal
 * UI/API). Deliberately independent of this test's own G-SYS Integration
 * Request officialPoNo (a distinct E2E-* value) - FulfillmentService reads
 * only portal_order.official_po_no, never the Integration Request's own
 * field, so the two can differ without conflict. */
function linkOrderToFulfillmentFixture(orderId: string, officialPoNo: string) {
  execSync(
    `docker exec gsys-prototype-postgres psql -U gsys_portal -d gsys_portal -c ` +
    `"UPDATE portal_order SET official_po_no=NULL WHERE official_po_no='${officialPoNo}'; ` +
    `UPDATE portal_order SET official_po_no='${officialPoNo}' WHERE id=${orderId};"`,
    { stdio: 'pipe' },
  )
}

test.describe('Gap Analysis Implementation Instruction Scenario 1: full continuous order flow', () => {
  test('Candidate -> SKU Detail -> Draft -> 承認 -> PO No -> Excel -> PDF -> Import Folder Sim -> G-SYS Status Sim -> Mail Preview -> Email Send Sim -> Supplier Response -> Difference -> Agreement -> Arrival -> Completed', async ({ page }) => {
    // ---- Candidate -> SKU Detail (B-2 pre-existing entry point) -> back -> Draft ----
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-candidates').click()
    await page.getByTestId('order-candidate-brand-link-BR_OUTDOOR').click()
    await expect(page.getByTestId(`candidate-row-${SKU}`)).toBeVisible()
    await page.getByRole('button', { name: SKU }).click()
    await expect(page).toHaveURL(new RegExp(`/items/${SKU}`))
    await page.getByTestId('back-to-sku-detail-origin').click()
    await expect(page.getByTestId('nav-candidates')).toBeVisible()

    await page.getByTestId(`candidate-checkbox-${SKU}`).locator('input').check()
    await page.getByTestId('create-draft-button').click()
    await expect(page).toHaveURL(/\/orders\/drafts\/(\d+)/)
    const draftId = page.url().match(/\/orders\/drafts\/(\d+)/)?.[1]
    expect(draftId).toBeTruthy()
    const orderedQty = 6
    await page.getByTestId(`order-qty-input-${SKU}`).locator('input').fill(String(orderedQty))
    await page.getByTestId('save-draft-button').click()
    await expect(page.getByText('保存しました。')).toBeVisible()

    // ---- 承認依頼 -> 承認 ----
    await page.getByTestId('submit-for-approval-button').click()
    await page.getByTestId('submit-for-approval-dialog-confirm').click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))
    await logout(page)

    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto(`/orders/${draftId}`)
    await page.getByTestId('order-detail-approve-button').click()
    await page.getByTestId('approve-dialog-confirm').click()
    await expect(page.getByText('承認しました。')).toBeVisible()

    // ---- Manufacturer Channel (EMAIL) / Supplier Contact / Mail Template ----
    await page.getByTestId('nav-master-maintenance').click()
    await page.getByTestId('nav-admin-manufacturer-channels').click()
    await page.getByTestId('manufacturer-channel-create-button').click()
    await page.getByTestId('manufacturer-channel-supplierCode').locator('input').fill('SUP_ALPHA')
    await page.getByTestId('manufacturer-channel-brandCode').locator('input').fill('BR_OUTDOOR')
    await page.getByTestId('manufacturer-channel-save').click()
    await expect(page.getByTestId('manufacturer-channel-table-container')).toContainText('SUP_ALPHA')

    await page.goto('/admin/supplier-contacts')
    await page.getByTestId('supplier-contact-create-button').click()
    await page.getByTestId('supplier-contact-supplierCode').locator('input').fill('SUP_ALPHA')
    await page.getByTestId('supplier-contact-brandCode').locator('input').fill('BR_OUTDOOR')
    await page.getByTestId('supplier-contact-contactName').locator('input').fill('Integration Tester')
    await page.getByTestId('supplier-contact-email').locator('input').fill('integration-tester@example.com')
    await page.getByTestId('supplier-contact-save').click()
    await expect(page.getByTestId('supplier-contact-table-container')).toContainText('integration-tester@example.com')

    await page.goto('/admin/mail-templates')
    await page.getByTestId('mail-template-create-button').click()
    await page.getByTestId('mail-template-templateName').locator('input').fill('Integration PO Template')
    await page.getByTestId('mail-template-supplierCode').locator('input').fill('SUP_ALPHA')
    await page.getByTestId('mail-template-brandCode').locator('input').fill('BR_OUTDOOR')
    await page.getByTestId('mail-template-subjectTemplate').locator('input').fill('PO {{poNo}}')
    await page.getByTestId('mail-template-bodyTemplate').locator('textarea').first().fill('Dear {{contactName}}, PO No: {{poNo}}')
    await page.getByTestId('mail-template-save').click()
    await expect(page.getByTestId('mail-template-table-container')).toContainText('Integration PO Template')

    // ---- G-SYS連携準備 -> 正式PO番号確定 -> Excel生成 -> PDF生成 (C-1) ----
    await page.goto(`/orders/${draftId}`)
    await page.getByTestId('official-po-request-button').click()
    await page.getByTestId('official-po-request-dialog-confirm').click()
    await expect(page.getByText('G-SYS連携の準備が完了しました。')).toBeVisible()

    // BR-08 (docs/gulliver-20260917-confirmed-business-rules.md): Official
    // PO No. is auto-numbered immediately - no manual input/confirm step.
    await page.getByTestId('official-po-generate-button').click()
    await expect(page.getByText('正式PO Excelを生成しました。')).toBeVisible()

    await page.getByTestId('official-po-pdf-generate-button').click()
    await expect(page.getByText('正式PO PDFを生成しました。')).toBeVisible()
    await expect(page.getByTestId('official-po-pdf-download-button')).toBeVisible()

    // ---- G-SYS連携用ファイル配置 (Import Folder Sim) ----
    await page.getByTestId('official-po-place-button').click()
    await expect(page.getByText('G-SYS連携用ファイルを配置しました。')).toBeVisible()
    await expect(page.getByTestId('official-po-status-label')).toHaveText('G-SYSへ投入済み')

    // ---- G-SYS登録状況確認 (Integration Status Sim - a synthetic PO No.
    // that provably does not exist in the Legacy Demo MySQL Fixture set, so
    // this stays NOT_YET_IMPORTED rather than an error) ----
    await page.getByTestId('official-po-confirm-import-button').click()
    await expect(page.getByTestId('official-po-import-not-matched')).toBeVisible()
    await expect(page.getByTestId('official-po-import-not-matched')).toContainText('エラーではありません')

    // ---- Mail Preview -> To/CC確認 (Master値をそのまま使用 - Override自体は
    // 別Scenario 4として email-send.spec.ts が専用でカバー) -> メール送信 ----
    await expect(page.getByTestId('resolved-manufacturer-channel-chip')).toHaveText('登録済み通信方法: Email')
    await page.getByTestId('mail-preview-button').click()
    await expect(page.getByTestId('mail-preview-result')).toBeVisible()
    await expect(page.getByTestId('mail-send-to-input').locator('input')).toHaveValue('integration-tester@example.com')

    await expect(page.getByTestId('email-send-section')).toBeVisible()
    await page.getByTestId('email-send-button').click()
    // BR-04: Send now requires final confirmation via a Dialog.
    await page.getByTestId('email-send-confirm-dialog-confirm').click()
    await expect(page.getByText('メールを送信しました。')).toBeVisible()
    await expect(page.getByTestId('email-sent-note')).toBeVisible()

    // ---- デモ送信 (Order Status を AWAITING_SUPPLIER へ進める - 実メール送信
    // とは独立した別Track。demo-send-button は PO Preview 画面にある) ----
    const goToPreview = page.getByTestId('order-detail-primary-action')
    await expect(goToPreview).toHaveText('PO プレビューを見る')
    await goToPreview.click()
    await expect(page).toHaveURL(new RegExp(`/orders/drafts/${draftId}/preview(\\?.*)?$`))
    // Acceptance Fix C-3: this Button's label must never collide with the
    // real Manufacturer Email Send Action's own label/Section title
    // ("メーカーへ送信") shown later on Order Detail - they are different
    // Actions (this one only records/advances Status, never transmits
    // anything) and must be visually distinguishable at first glance.
    await expect(page.getByTestId('demo-send-button')).toHaveText('メール発注済みとして記録')
    await page.getByTestId('demo-send-button').click()
    await page.getByTestId('demo-send-dialog-confirm').click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))

    const enterSupplierResponse = page.getByTestId('order-detail-primary-action')
    await expect(enterSupplierResponse).toHaveText('メーカー回答を入力')
    await enterSupplierResponse.click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}/supplier-response(\\?.*)?$`))

    // ---- メーカー回答 (数量差異を作る - orderedQty=6, confirmedQty=4) ----
    const confirmedQty = orderedQty - 2
    await page.getByTestId(`confirmed-qty-input-${SKU}`).locator('input').fill(String(confirmedQty))
    await page.getByTestId('save-response-button').click()
    await expect(page.getByText('回答を保存しました。')).toBeVisible()
    await page.getByTestId('confirm-response-button').click()
    await page.getByTestId('confirm-response-dialog-confirm').click()
    await expect(page).toHaveURL(new RegExp(`/orders/${draftId}(\\?.*)?$`))
    await expect(page.getByText('メーカー回答を確定しました。')).toBeVisible()

    // ---- 差異 (Difference) ----
    await page.getByTestId('order-detail-primary-action').click()
    await expect(page.getByTestId('response-differences-section')).toBeVisible()
    await expect(page.getByTestId(`difference-${SKU}-QUANTITY_CHANGED`)).toBeVisible()

    // Acknowledge the Attention (差異確認), then Agree without force.
    const chip = page.locator('[data-testid^="attention-chip-"]').first()
    await chip.locator('span[aria-hidden]').click()
    await page.getByTestId('agree-button').click()
    await expect(page.getByTestId('force-agree-checkbox')).toHaveCount(0)
    await page.getByTestId('agree-dialog-confirm').click()

    // ---- 合意 (Agreement) ----
    await expect(page.getByTestId('agreed-section')).toBeVisible()

    // ---- 入荷 (Arrival) -> 完納 (Completed): match this Order against the
    // same Legacy TR_PO/TR_INV 完納 Fixture (PO-OUTDOOR-01) Fulfillment
    // Foundation's own Scenario B already exercises. ----
    linkOrderToFulfillmentFixture(draftId!, 'PO-OUTDOOR-01')
    await page.goto(`/orders/${draftId}`)
    await expect(page.getByTestId('fulfillment-status-chip')).toContainText('完納')
  })

  // Runs last - deactivates the SUP_ALPHA/BR_OUTDOOR Manufacturer Channel /
  // Supplier Contact / Mail Template rows the test above created for real,
  // matching manufacturer-channel.spec.ts / email-send.spec.ts's own
  // cleanup idiom so later specs in a full-suite run are unaffected.
  test('cleanup: deactivate the Master data rows created above', async ({ page }) => {
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
      if (c.email === 'integration-tester@example.com' && c.active) {
        await page.request.put(`/api/admin/supplier-contacts/${c.id}`, { data: { ...c, active: false } })
      }
    }

    const templates = await (await page.request.get('/api/admin/mail-templates')).json()
    for (const t of templates) {
      if (t.templateName === 'Integration PO Template' && t.active) {
        await page.request.put(`/api/admin/mail-templates/${t.id}`, { data: { ...t, active: false } })
      }
    }
  })
})
