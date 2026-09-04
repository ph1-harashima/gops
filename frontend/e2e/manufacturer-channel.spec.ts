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

test.describe('Phase 9-D: Manufacturer Channel Master', () => {
  test('ADMIN creates a Manufacturer Channel entry and it appears on an APPROVED Order before any Send', async ({ page }) => {
    // Create the Draft first (as OPERATOR), before the Channel Master row
    // exists yet, to also prove the "no Chip when unresolved" case.
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

    // No Master row for SUP_ALPHA/BR_OUTDOOR yet at this point in a fresh
    // Demo Reset - unresolved, no Chip.
    await expect(page.getByTestId('resolved-manufacturer-channel-chip')).toHaveCount(0)

    // Register the Channel via the admin screen.
    await page.getByTestId('nav-master-maintenance').click()
    await page.getByTestId('nav-admin-manufacturer-channels').click()
    await expect(page).toHaveURL(/\/admin\/manufacturer-channels/)
    await page.getByTestId('manufacturer-channel-create-button').click()
    await page.getByTestId('manufacturer-channel-supplierCode').locator('input').fill('SUP_ALPHA')
    await page.getByTestId('manufacturer-channel-brandCode').locator('input').fill('BR_OUTDOOR')
    // Default channel is already EMAIL - explicit select for clarity/robustness.
    await page.getByTestId('manufacturer-channel-channel').click()
    await page.getByRole('option', { name: 'Email' }).click()
    await page.getByTestId('manufacturer-channel-save').click()
    await expect(page.getByTestId('manufacturer-channel-table-container')).toContainText('SUP_ALPHA')

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
})
