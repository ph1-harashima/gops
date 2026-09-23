import { test, expect, type Page } from '@playwright/test'

/**
 * G-OPS Operational Workflow Realignment Phase F §16 (Draft Lifecycle):
 * DRAFT-only, no-downstream-process-started deletion, soft delete on the
 * Backend. Complements role-approval-workflow.spec.ts (shares its
 * createOrderableDraft/submitForApproval helper shape) by covering the
 * one new action neither that file nor core-demo-scenario.spec.ts
 * exercises at all.
 */

const OPERATOR_USERNAME = 'purchase01'
const OPERATOR_PASSWORD = 'DemoPass123!'
const ADMIN_USERNAME = 'admin01'
const ADMIN_PASSWORD = 'DemoPass123!'
const SKU = 'HM-MUG-001'

async function login(page: Page, username: string, password: string) {
  await page.goto('/')
  await page.getByLabel('ユーザー名').fill(username)
  await page.getByLabel('パスワード').fill(password)
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page.getByTestId('nav-dashboard')).toBeVisible()
}

async function createDraft(page: Page): Promise<string> {
  await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
  await page.getByTestId('nav-candidates').click()
  await page.getByTestId('order-candidate-brand-link-BR_HOME').click()
  await expect(page.getByTestId(`candidate-row-${SKU}`)).toBeVisible()
  await page.getByTestId(`candidate-checkbox-${SKU}`).locator('input').check()
  await page.getByTestId('create-draft-button').click()
  await expect(page).toHaveURL(/\/orders\/drafts\/\d+(\?.*)?$/)
  const draftId = page.url().match(/\/orders\/drafts\/(\d+)/)?.[1]
  expect(draftId).toBeTruthy()
  return draftId!
}

test.describe('Phase F §16: Draft Deletion', () => {
  test('Creator deletes a DRAFT: confirm dialog, then gone', async ({ page }) => {
    const draftId = await createDraft(page)

    await expect(page.getByTestId('delete-draft-button')).toBeVisible()
    await page.getByTestId('delete-draft-button').click()
    await expect(page.getByTestId('delete-draft-dialog-confirm')).toBeVisible()
    await page.getByTestId('delete-draft-dialog-confirm').click()

    // Navigated away (back to the Brand-scoped Candidate List it was
    // created from) - the Draft screen itself is gone.
    await expect(page).not.toHaveURL(new RegExp(`/orders/drafts/${draftId}`))

    // The Backend soft-delete makes it genuinely NotFound - a direct
    // revisit must not show it as if still there. Generous timeout:
    // react-query's default retry (3 attempts, exponential backoff) adds
    // real seconds before a genuine 404 settles into isError - this is
    // not this feature's own slowness, just react-query's default retry
    // policy for what looks like a transient failure until the 3rd try.
    await page.goto(`/orders/drafts/${draftId}`)
    await expect(page.getByText('指定されたドラフトが見つかりませんでした。')).toBeVisible({ timeout: 15000 })
  })

  test('Cancel on the confirm dialog leaves the Draft untouched', async ({ page }) => {
    const draftId = await createDraft(page)

    await page.getByTestId('delete-draft-button').click()
    await expect(page.getByTestId('delete-draft-dialog-confirm')).toBeVisible()
    await page.getByTestId('delete-draft-dialog-cancel').click()

    await expect(page).toHaveURL(new RegExp(`/orders/drafts/${draftId}`))
    await page.reload()
    await expect(page).toHaveURL(new RegExp(`/orders/drafts/${draftId}`))
    await expect(page.getByTestId('delete-draft-button')).toBeVisible()
  })

  test('Once APPROVED, the delete action is no longer offered', async ({ page }) => {
    const draftId = await createDraft(page)
    const qtyInput = page.getByTestId(`order-qty-input-${SKU}`).locator('input')
    await qtyInput.fill('3')
    await page.getByTestId('save-draft-button').click()
    await expect(page.getByText('保存しました。')).toBeVisible()
    await page.getByTestId('submit-for-approval-button').click()
    await page.getByTestId('submit-for-approval-dialog-confirm').click()
    await page.getByTestId('nav-logout').click()
    await expect(page.getByLabel('ユーザー名')).toBeVisible()

    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto(`/orders/${draftId}`)
    await page.getByTestId('order-detail-approve-button').click()
    await page.getByTestId('approve-dialog-confirm').click()
    await expect(page.getByText('承認しました。')).toBeVisible()

    // Back on the (now APPROVED) Draft edit screen, ownership/editability
    // rules already hide Save/Submit for a non-DRAFT Order - the new
    // Delete action must be hidden the same way, not merely refused server-side.
    await page.goto(`/orders/drafts/${draftId}`)
    await expect(page.getByTestId('delete-draft-button')).toHaveCount(0)
  })
})
