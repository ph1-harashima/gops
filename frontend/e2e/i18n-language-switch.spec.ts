import { test, expect, type Page } from '@playwright/test'

/**
 * G-OPS i18n完成 #14: Language Switch / Persistence / 日英主要画面 E2E.
 *
 * Covers exactly the three E2E-level items the task explicitly requires
 * (unit/component coverage for resource-parity and the Switcher's own
 * behavior already lives in i18nResources.test.ts / LanguageSwitcher.test.tsx /
 * i18nPersistence.test.ts - this spec is the real-browser layer on top):
 *   - 日本語主要画面: default language is ja with no prior localStorage state.
 *   - Language switch: clicking the Switcher actually re-renders the page.
 *   - Persistence: a chosen language survives a real page reload.
 *   - English主要画面: Dashboard / Order Candidates / Order Detail render
 *     correctly in English, including a Status/Enum label resolving from
 *     Resources rather than leaking a raw internal value (要件§5).
 */

const ADMIN_USERNAME = 'admin01'
const ADMIN_PASSWORD = 'DemoPass123!'

async function login(page: Page) {
  await page.goto('/')
  await page.getByLabel('ユーザー名').fill(ADMIN_USERNAME)
  await page.getByLabel('パスワード').fill(ADMIN_PASSWORD)
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page.getByTestId('nav-dashboard')).toBeVisible()
}

test.describe('G-OPS i18n完成: Language Switch / Persistence / JA・EN Main Screens', () => {
  test('A: default language is Japanese with no prior localStorage choice', async ({ page }) => {
    await login(page)
    await expect(page.getByRole('heading', { name: 'ダッシュボード', level: 1 })).toBeVisible()
    await expect(page.getByTestId('language-switcher-ja')).toHaveAttribute('aria-pressed', 'true')
  })

  test('B: switching to English via the Language Switcher re-renders the current page', async ({ page }) => {
    await login(page)
    await page.getByTestId('language-switcher-en').click()
    await expect(page.getByRole('heading', { name: 'Dashboard', level: 1 })).toBeVisible()
    await expect(page.getByTestId('language-switcher-en')).toHaveAttribute('aria-pressed', 'true')
  })

  test('C: the chosen language (English) survives a real page reload', async ({ page }) => {
    await login(page)
    await page.getByTestId('language-switcher-en').click()
    await expect(page.getByRole('heading', { name: 'Dashboard', level: 1 })).toBeVisible()

    await page.reload()
    await expect(page.getByRole('heading', { name: 'Dashboard', level: 1 })).toBeVisible()
    await expect(page.getByTestId('language-switcher-en')).toHaveAttribute('aria-pressed', 'true')
  })

  test('D: switching back to Japanese also survives a reload', async ({ page }) => {
    await login(page)
    await page.getByTestId('language-switcher-en').click()
    await expect(page.getByRole('heading', { name: 'Dashboard', level: 1 })).toBeVisible()
    await page.getByTestId('language-switcher-ja').click()
    await expect(page.getByRole('heading', { name: 'ダッシュボード', level: 1 })).toBeVisible()

    await page.reload()
    await expect(page.getByRole('heading', { name: 'ダッシュボード', level: 1 })).toBeVisible()
    await expect(page.getByTestId('language-switcher-ja')).toHaveAttribute('aria-pressed', 'true')
  })

  test('E: English main screens render correctly - Dashboard, Order Candidates, Order List', async ({ page }) => {
    await login(page)
    await page.getByTestId('language-switcher-en').click()

    await expect(page.getByRole('heading', { name: 'Dashboard', level: 1 })).toBeVisible()

    await page.getByTestId('nav-candidates').click()
    await page.getByTestId('candidates-view-all-button').click()
    await expect(page.getByRole('heading', { name: 'Order Candidates', level: 1 })).toBeVisible()

    await page.getByTestId('nav-history').click()
    await expect(page.getByRole('heading', { name: 'Orders', level: 1 })).toBeVisible()
  })

  test('F: Order Detail in English resolves a Status/Enum label from Resources, not a raw internal value (要件§5)', async ({ page }) => {
    await login(page)
    await page.getByTestId('nav-candidates').click()
    await page.getByTestId('order-candidate-brand-link-BR_KITCHEN').click()
    await expect(page.getByTestId('candidate-row-KT-KNIFE-001')).toBeVisible()
    await page.getByTestId('candidate-checkbox-KT-KNIFE-001').locator('input').check()
    await page.getByTestId('create-draft-button').click()
    await expect(page).toHaveURL(/\/orders\/drafts\/(\d+)/)

    const qtyInput = page.getByTestId('order-qty-input-KT-KNIFE-001').locator('input')
    await qtyInput.fill('3')
    await page.getByTestId('save-draft-button').click()
    await expect(page.getByText('保存しました。')).toBeVisible()

    // Switch to English only now, on the still-editable Draft screen -
    // proves mid-workflow switching doesn't lose the in-progress Draft.
    await page.getByTestId('language-switcher-en').click()
    await expect(page.getByRole('button', { name: 'SUBMIT FOR APPROVAL' })).toBeVisible()

    await page.getByTestId('submit-for-approval-button').click()
    await page.getByTestId('submit-for-approval-dialog-confirm').click()
    await expect(page.getByText('Pending Approval', { exact: false }).first()).toBeVisible()
  })
})
