import { test, expect, type Page } from '@playwright/test'

/**
 * Gap Analysis §12 (Domestic/Overseas Foundation): Supplier Region
 * Classification Master admin screen + the resolved Chip it feeds into
 * Order Detail. Display only - never consulted by Recommended Qty.
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

test.describe('Gap Analysis §12: Supplier Region Classification Master', () => {
  test('ADMIN creates a Region Classification entry and it appears as a Chip on Order Detail', async ({ page }) => {
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
    await expect(page.getByTestId('resolved-region-classification-chip')).toHaveCount(0)

    await page.getByTestId('nav-master-maintenance').click()
    await page.getByTestId('nav-admin-supplier-region-classifications').click()
    await expect(page).toHaveURL(/\/admin\/supplier-region-classifications/)
    await page.getByTestId('supplier-region-classification-create-button').click()
    await page.getByTestId('supplier-region-classification-supplierCode').locator('input').fill('SUP_ALPHA')
    await page.getByTestId('supplier-region-classification-brandCode').locator('input').fill('BR_OUTDOOR')
    await page.getByTestId('supplier-region-classification-regionClassification').click()
    await page.getByRole('option', { name: '海外' }).click()
    await page.getByTestId('supplier-region-classification-save').click()
    await expect(page.getByTestId('supplier-region-classification-table-container')).toContainText('SUP_ALPHA')

    // Back on the Order Detail, the Chip now resolves.
    await page.goto(`/orders/${draftId}`)
    await expect(page.getByTestId('resolved-region-classification-chip')).toHaveText('海外')
  })

  test('OPERATOR cannot reach the Region Classification admin API', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)

    const response = await page.request.get('/api/admin/supplier-region-classifications')
    expect(response.status()).toBe(403)
    const body = await response.json()
    expect(body.errorCode).toBe('FORBIDDEN')

    await expect(page.getByTestId('nav-master-maintenance')).toHaveCount(0)
  })

  // Runs last in this file - deactivates the SUP_ALPHA/BR_OUTDOOR row the
  // first test above created for real (E2E hits the actual running server),
  // matching manufacturer-channel.spec.ts's own cleanup idiom.
  test('cleanup: deactivate the Region Classification row created above', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)

    const rows = await (await page.request.get('/api/admin/supplier-region-classifications')).json()
    for (const r of rows) {
      if (r.supplierCode === 'SUP_ALPHA' && r.brandCode === 'BR_OUTDOOR' && r.active) {
        await page.request.put(`/api/admin/supplier-region-classifications/${r.id}`, {
          data: { supplierCode: r.supplierCode, brandCode: r.brandCode, regionClassification: r.regionClassification, active: false },
        })
      }
    }
  })
})
