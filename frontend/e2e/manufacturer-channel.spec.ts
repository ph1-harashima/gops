import { test, expect, type Page } from '@playwright/test'

/**
 * Phase 9-D: Manufacturer Channel Master admin screen + the resolved
 * Channel Chip it feeds into Order Detail before any Send has happened.
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

/** Freeze Blocker-2 (Test Data Lifecycle, docs/gops-phase1-final-cleanup-report.md):
 * manufacturer_channel has no content-based Test marker, so the safe
 * "won't grow further" fix is reusing a prior-run row (from a repeated
 * full-suite run - Demo Reset never wipes Master data) instead of
 * unconditionally creating a new one every run. Business Rule
 * (soft-delete-only) is unchanged - this only decides Create-via-UI vs
 * reuse-via-the-SAME-PUT this file's own cleanup test already uses.
 * First-ever run (no Fixture exists yet) still exercises the real Create
 * UI once. */
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
  await page.getByTestId('nav-master-maintenance').click()
  await page.getByTestId('nav-admin-manufacturer-channels').click()
  await expect(page).toHaveURL(/\/admin\/manufacturer-channels/)
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

test.describe('Phase 9-D: Manufacturer Channel Master', () => {
  test('ADMIN creates a Manufacturer Channel entry and it appears on an APPROVED Order before any Send', async ({ page }) => {
    // Create the Draft first (as OPERATOR), before the Channel Master row
    // exists yet, to also prove the "no Chip when unresolved" case.
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-candidates').click()
    await page.getByTestId('order-candidate-brand-link-BR_OUTDOOR').click()
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

    // No ACTIVE Master row for SUP_ALPHA/BR_OUTDOOR at this point - either a
    // fresh Demo Reset (no row at all) or every prior run's row already
    // deactivated by this file's own cleanup test - unresolved, no Chip.
    await expect(page.getByTestId('resolved-manufacturer-channel-chip')).toHaveCount(0)

    // Register (or reuse a prior run's Fixture row for) the Channel.
    await ensureManufacturerChannel(page, 'SUP_ALPHA', 'BR_OUTDOOR', 'EMAIL')

    // Back on the Order Detail, the Chip now resolves.
    await page.goto(`/orders/${draftId}`)
    await expect(page.getByTestId('resolved-manufacturer-channel-chip')).toHaveText('登録済み通信方法: Email')
  })

  test('OPERATOR cannot reach the Manufacturer Channel admin API', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)

    const response = await page.request.get('/api/admin/manufacturer-channels')
    expect(response.status()).toBe(403)
    const body = await response.json()
    expect(body.errorCode).toBe('FORBIDDEN')

    // The Frontend Nav never even offers the Master Maintenance menu.
    await expect(page.getByTestId('nav-master-maintenance')).toHaveCount(0)
  })

  // Runs last in this file - deactivates the SUP_ALPHA/BR_OUTDOOR row the
  // first test above created for real (E2E hits the actual running server),
  // so later specs in a full-suite run that assume an unresolved Channel
  // for SUP_ALPHA/BR_OUTDOOR are not affected by this file having run
  // earlier. Same cleanup idiom as email-send.spec.ts's own last test.
  test('cleanup: deactivate the Manufacturer Channel row created above', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)

    const channels = await (await page.request.get('/api/admin/manufacturer-channels')).json()
    for (const c of channels) {
      if (c.supplierCode === 'SUP_ALPHA' && c.brandCode === 'BR_OUTDOOR' && c.active) {
        await page.request.put(`/api/admin/manufacturer-channels/${c.id}`, {
          data: { supplierCode: c.supplierCode, brandCode: c.brandCode, channel: c.channel, active: false },
        })
      }
    }
  })
})
