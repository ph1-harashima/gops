import { test, expect, type Page } from '@playwright/test'

/**
 * Phase 6-A: List State Preservation / returnTo Navigation Infrastructure.
 * Covers the Browser Acceptance Scenarios from the implementation
 * instructions (Phase 6-A 13章) plus the Open Redirect fallback required by
 * 11章. See docs/production-ux-workflow-redesign.md 6章 for the design.
 *
 * Uses dynamic Filter selection (whichever Brand/Supplier/Status option the
 * currently-seeded Demo Data offers) rather than hardcoded business codes,
 * since Demo Data has been anonymized more than once already in this
 * project and is expected to keep changing.
 */

const DEMO_USERNAME = 'purchase01'
const DEMO_PASSWORD = 'DemoPass123!'
const KNOWN_SKU = 'KT-BOWL-001' // fixture shared with core-demo-scenario.spec.ts

async function login(page: Page) {
  await page.goto('/')
  await page.getByLabel('ユーザー名').fill(DEMO_USERNAME)
  await page.getByLabel('パスワード').fill(DEMO_PASSWORD)
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page.getByTestId('nav-dashboard')).toBeVisible()
}

/** Opens the MUI Select for `labelText` and picks the first real option
 * (index 0 is always the "すべて" placeholder). Returns that option's
 * displayed text, since the URL stores the underlying Code, not the name. */
async function selectFirstRealOption(page: Page, labelText: string): Promise<string> {
  await page.getByLabel(labelText).click()
  const options = page.getByRole('option')
  await expect(options.nth(1)).toBeVisible()
  const text = (await options.nth(1).textContent())!.trim()
  await options.nth(1).click()
  return text
}

test.describe('Phase 6-A: List State Preservation', () => {
  test('Scenario A: Candidate List Filter survives a SKU Detail round trip', async ({ page }) => {
    await login(page)
    await page.getByTestId('nav-candidates').click()
    await expect(page).toHaveURL(/\/candidates$/)

    const brandName = await selectFirstRealOption(page, 'ブランド')
    await expect(page).toHaveURL(/brandCode=/)
    await selectFirstRealOption(page, 'メーカー')
    await expect(page).toHaveURL(/supplierCode=/)

    const listUrl = page.url()
    const brandCode = new URL(listUrl).searchParams.get('brandCode')
    const supplierCode = new URL(listUrl).searchParams.get('supplierCode')
    expect(brandCode).toBeTruthy()
    expect(supplierCode).toBeTruthy()

    // Open whichever row the Filter left visible.
    const firstSkuButton = page.locator('table tbody tr').first().getByRole('button').first()
    await expect(firstSkuButton).toBeVisible()
    await firstSkuButton.click()
    await expect(page).toHaveURL(/\/items\/.+returnTo=/)

    await page.getByRole('button', { name: '発注候補一覧へ戻る' }).click()
    await expect(page).toHaveURL(listUrl)
    await expect(page.getByLabel('ブランド')).toHaveText(brandName)
  })

  test('Scenario B: Candidate List Filter survives a Draft creation round trip', async ({ page }) => {
    await login(page)
    await page.getByTestId('nav-candidates').click()
    await selectFirstRealOption(page, 'ブランド')
    await expect(page).toHaveURL(/brandCode=/)
    const listUrl = page.url()

    const firstCheckbox = page.locator('table tbody tr').first().locator('input[type="checkbox"]')
    await firstCheckbox.check()
    await page.getByTestId('create-draft-button').click()
    await expect(page).toHaveURL(/\/orders\/drafts\/\d+\?returnTo=/)

    await page.getByRole('button', { name: '発注候補一覧へ戻る' }).click()
    await expect(page).toHaveURL(listUrl)
  })

  test('Scenario C: Order History List Filter survives a Detail round trip', async ({ page }) => {
    await login(page)
    await page.getByTestId('nav-history').click()
    await expect(page).toHaveURL(/\/orders\/history$/)

    // Status options render even before any data loads, so this is the
    // most reliably non-empty Filter to drive for this Scenario.
    await selectFirstRealOption(page, 'Status')
    await expect(page).toHaveURL(/status=/)
    const listUrl = page.url()

    // Wait for the refetch triggered by the Filter change to actually
    // resolve before counting rows - the URL updates synchronously but the
    // Table does not (this was a latent flake in this Scenario, found while
    // writing the equivalent wait for Phase 6-B's order-detail-hub.spec.ts).
    await expect(page.getByText(/件の発注|該当する発注が見つかりませんでした/)).toBeVisible()
    const rowCount = await page.locator('table tbody tr').count()
    test.skip(rowCount === 0, 'No orders match this Status in the current Demo Data')

    await page.locator('table tbody tr').first().click()
    await expect(page).toHaveURL(/\/orders\/\d+\?returnTo=/)

    await page.getByRole('button', { name: '発注一覧へ戻る' }).click()
    await expect(page).toHaveURL(listUrl)
  })

  test('Scenario D: Browser Back/Forward keeps the URL and the displayed Filter in sync', async ({ page }) => {
    // Filter changes use setSearchParams(..., { replace: true }) (design doc
    // 6.2章) so picking Brand/Supplier/typing a Keyword does not spam
    // history - the meaningful Back/Forward boundary is List <-> Detail.
    // This confirms the physical Back button (not just the app's own
    // "戻る" button) still lands on a List page whose displayed Filter
    // matches its URL, and Forward returns to Detail.
    await login(page)
    await page.getByTestId('nav-candidates').click()

    const brandName = await selectFirstRealOption(page, 'ブランド')
    await expect(page).toHaveURL(/brandCode=/)
    const filteredListUrl = page.url()

    const firstSkuButton = page.locator('table tbody tr').first().getByRole('button').first()
    await firstSkuButton.click()
    await expect(page).toHaveURL(/\/items\//)
    const detailUrl = page.url()

    await page.goBack()
    await expect(page).toHaveURL(filteredListUrl)
    await expect(page.getByLabel('ブランド')).toHaveText(brandName)

    await page.goForward()
    await expect(page).toHaveURL(detailUrl)
  })

  test('Direct URL load restores the Candidate List Filter without any click', async ({ page }) => {
    await login(page)
    await page.goto('/candidates?brandCode=BR_NONEXISTENT&keyword=abc')
    await expect(page.getByLabel('SKU / 商品名')).toHaveValue('abc')
  })

  test('returnTo rejects an external URL and falls back to the safe default', async ({ page }) => {
    await login(page)
    // Open Redirect attempt via a hand-crafted URL (implementation
    // instructions Phase 6-A 11章) - simulates a malicious/garbled link
    // rather than anything the app itself would ever generate.
    await page.goto(`/items/${KNOWN_SKU}?returnTo=https%3A%2F%2Fevil.example.com`)
    await page.getByRole('button', { name: '発注候補一覧へ戻る' }).click()
    await expect(page).toHaveURL(/^http:\/\/localhost:5173\/candidates$/)
  })

  test('returnTo rejects a protocol-relative URL and falls back to the safe default', async ({ page }) => {
    await login(page)
    await page.goto(`/items/${KNOWN_SKU}?returnTo=%2F%2Fevil.example.com`)
    await page.getByRole('button', { name: '発注候補一覧へ戻る' }).click()
    await expect(page).toHaveURL(/^http:\/\/localhost:5173\/candidates$/)
  })
})
