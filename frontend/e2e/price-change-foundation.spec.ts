import { test, expect, type Page } from '@playwright/test'

/**
 * Phase 8-B: Price Change Foundation. Covers the 3-screen flow (List /
 * Create-Edit / Detail) end to end against real Legacy Demo MySQL Seed Data
 * (backend/demo-data/02-seed.sql) - Current Price/Cost read, Baseline
 * Snapshot, Margin Preview, Concurrency display, Audit Trail, Save/reopen.
 * No Approval/Scheduled/Threshold Business Rule exists to exercise (Phase
 * 8-B forbids implementing them) - this suite only proves the Foundation
 * itself, and that it never touches the existing Ordering Workflow.
 */

const OPERATOR_USERNAME = 'purchase01'
const OPERATOR_PASSWORD = 'DemoPass123!'

async function login(page: Page, username: string, password: string) {
  await page.goto('/')
  await page.getByLabel('ユーザー名').fill(username)
  await page.getByLabel('パスワード').fill(password)
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page.getByTestId('nav-dashboard')).toBeVisible()
}

test.describe('Phase 8-B: Price Change Foundation', () => {
  test('A: Navigation - 価格変更 Nav entry reaches the List screen', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)

    await page.getByTestId('nav-price-changes').click()
    await expect(page).toHaveURL(/\/price-changes$/)
    await expect(page.getByRole('heading', { name: '価格変更一覧' })).toBeVisible()
  })

  test('B: Create Draft -> Product Selection -> Add SKU -> Margin Preview shows Current/Cost/Margin', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-price-changes').click()

    await page.getByTestId('create-price-change-button').click()
    await expect(page).toHaveURL(/\/price-changes\/\d+\/edit(\?.*)?$/)
    await expect(page.getByText(/価格変更（下書き）/)).toBeVisible()

    await page.getByTestId('candidate-keyword-input').locator('input').fill('HM-MUG-001')
    await page.getByTestId('search-candidates-button').click()
    await expect(page.getByTestId('candidate-search-row-HM-MUG-001')).toBeVisible()

    await page.getByTestId('add-candidate-HM-MUG-001').click()
    await expect(page.getByTestId('price-change-line-HM-MUG-001')).toBeVisible()

    // Seed data (backend/demo-data/02-seed.sql): HM-MUG-001 prc_sell_w_tax=3560.00,
    // cost_this_month_avg=2096.00 -> margin=1464, marginRate=0.3522 (35.22%).
    const row = page.getByTestId('price-change-line-HM-MUG-001')
    await expect(row).toContainText('¥3,560')
    await expect(row).toContainText('¥2,096')
    await expect(row).toContainText('¥1,464')
    await expect(row).toContainText('35.22%')
    await expect(page.getByTestId('concurrency-chip-HM-MUG-001')).toHaveText('変更なし')
  })

  test('C: Proposed Price edit updates Difference/%Change/Proposed Margin without page reload', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-price-changes').click()
    await page.getByTestId('create-price-change-button').click()

    await page.getByTestId('candidate-keyword-input').locator('input').fill('HM-MUG-001')
    await page.getByTestId('search-candidates-button').click()
    await page.getByTestId('add-candidate-HM-MUG-001').click()
    await expect(page.getByTestId('price-change-line-HM-MUG-001')).toBeVisible()

    const priceInput = page.getByTestId('proposed-price-input-HM-MUG-001')
    await priceInput.fill('3800')
    await priceInput.blur()
    await expect(page.getByText('保存しました。')).toBeVisible()

    const row = page.getByTestId('price-change-line-HM-MUG-001')
    await expect(row).toContainText('¥240')  // 3800 - 3560
    await expect(row).toContainText('6.74%') // 240 / 3560 * 100
  })

  test('D: Item Group bulk add adds every SKU in the group in one action', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-price-changes').click()
    await page.getByTestId('create-price-change-button').click()

    await page.getByTestId('item-group-select').click()
    await page.getByRole('option', { name: 'IG-OD-TENT' }).click()
    await page.getByTestId('add-item-group-button').click()

    await expect(page.getByTestId('price-change-line-OD-TENT-001')).toBeVisible()
    await expect(page.getByTestId('price-change-line-OD-TENT-002')).toBeVisible()
  })

  test('E: Negative margin fixture (KT-BOWL-002) displays without warning/blocking', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-price-changes').click()
    await page.getByTestId('create-price-change-button').click()

    await page.getByTestId('candidate-keyword-input').locator('input').fill('KT-BOWL-002')
    await page.getByTestId('search-candidates-button').click()
    await page.getByTestId('add-candidate-KT-BOWL-002').click()

    const row = page.getByTestId('price-change-line-KT-BOWL-002')
    await expect(row).toBeVisible()
    // -103 margin, -13.23% rate - just displayed, no error Toast/Dialog,
    // no disabled Save (Phase 8-B forbids Threshold/Warning Rules).
    await expect(row).toContainText('-¥103')
    await expect(row).toContainText('-13.23%')
  })

  test('F: Remove Detail via confirm Dialog', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-price-changes').click()
    await page.getByTestId('create-price-change-button').click()

    await page.getByTestId('candidate-keyword-input').locator('input').fill('HM-MUG-001')
    await page.getByTestId('search-candidates-button').click()
    await page.getByTestId('add-candidate-HM-MUG-001').click()
    await expect(page.getByTestId('price-change-line-HM-MUG-001')).toBeVisible()

    await page.getByTestId('remove-detail-HM-MUG-001').click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await page.getByTestId('confirm-remove-detail-dialog').click()

    await expect(page.getByText('削除しました。')).toBeVisible()
    await expect(page.getByTestId('price-change-line-HM-MUG-001')).not.toBeVisible()
  })

  test('G: Save note, reopen from List, and Detail screen shows Audit Trail', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-price-changes').click()
    await page.getByTestId('create-price-change-button').click()

    await page.getByTestId('price-change-note-input').locator('input').fill('Q4 kitchen adjustment')
    await page.getByTestId('save-note-button').click()
    await expect(page.getByText('保存しました。')).toBeVisible()

    await page.getByTestId('candidate-keyword-input').locator('input').fill('KT-KNIFE-001')
    await page.getByTestId('search-candidates-button').click()
    await page.getByTestId('add-candidate-KT-KNIFE-001').click()
    await expect(page.getByTestId('price-change-line-KT-KNIFE-001')).toBeVisible()

    const url = page.url()
    const id = url.match(/\/price-changes\/(\d+)\/edit/)?.[1]
    expect(id).toBeTruthy()

    // Reopen from the List (Save/reopen - Section 16).
    await page.getByTestId('back-to-price-change-origin').click()
    await expect(page).toHaveURL(/\/price-changes$/)
    await expect(page.getByTestId(`price-change-row-${id}`)).toContainText('Q4 kitchen adjustment')
    await page.getByTestId(`price-change-row-${id}`).click()
    await expect(page).toHaveURL(new RegExp(`/price-changes/${id}/edit(\\?.*)?$`))
    await expect(page.getByTestId('price-change-note-input').locator('input')).toHaveValue('Q4 kitchen adjustment')
    await expect(page.getByTestId('price-change-line-KT-KNIFE-001')).toBeVisible()

    // Detail screen (read-only) - navigate directly by URL and check Audit Trail.
    await page.goto(`/price-changes/${id}`)
    await expect(page.getByRole('heading', { name: /価格変更詳細/ })).toBeVisible()
    await expect(page.getByText('価格変更を作成')).toBeVisible()
    await expect(page.getByText('商品を追加')).toBeVisible()
    await expect(page.getByText('メモを変更')).toBeVisible()
    await expect(page.getByTestId('edit-price-change-button')).toBeVisible()
  })

  test('H: Existing Ordering Workflow (Candidate List -> Create Draft) is unaffected', async ({ page }) => {
    await login(page, OPERATOR_USERNAME, OPERATOR_PASSWORD)
    await page.getByTestId('nav-candidates').click()
    await page.getByTestId('order-candidate-brand-link-BR_KITCHEN').click()
    await expect(page.getByTestId('candidate-row-KT-KNIFE-002')).toBeVisible()
    await page.getByTestId('candidate-checkbox-KT-KNIFE-002').locator('input').check()
    await page.getByTestId('create-draft-button').click()
    await expect(page).toHaveURL(/\/orders\/drafts\/\d+/)
  })
})
