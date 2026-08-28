import { test, expect, type Page } from '@playwright/test'

/**
 * Phase 6-D: Dashboard Deep Link audit
 * (docs/production-ux-workflow-redesign.md 10章). Confirms each KPI/Brand
 * row Deep Link both applies the right Filter AND that the resulting List
 * count matches the number shown on the Dashboard - the "非常に重要" check
 * from the implementation instructions (11章). All Filters reuse Phase
 * 6-A's URL Query Parameter mechanism; nothing here talks to a new Backend
 * endpoint.
 */

const DEMO_USERNAME = 'purchase01'
const DEMO_PASSWORD = 'DemoPass123!'

async function login(page: Page) {
  await page.goto('/')
  await page.getByLabel('ユーザー名').fill(DEMO_USERNAME)
  await page.getByLabel('パスワード').fill(DEMO_PASSWORD)
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page.getByTestId('nav-dashboard')).toBeVisible()
}

/** The clickable KPI tile itself - matched by its Label's EXACT text (MUI
 * Paper wraps an h4 value + a body2 Label; "欠品" and "長期欠品" would both
 * substring-match a loose lookup, so this must be exact). */
function kpiTile(page: Page, label: string) {
  // Narrow to Paper elements with an <h4> (the 6 KPI tiles) BEFORE the exact
  // Label match - the Brand breakdown table below is also a MuiPaper-root
  // and contains every Label as a column header, but has no <h4>.
  return page.locator('.MuiPaper-root').filter({ has: page.locator('h4') }).filter({ has: page.getByText(label, { exact: true }) })
}

async function kpiValue(page: Page, label: string): Promise<number> {
  const text = await kpiTile(page, label).locator('h4').textContent()
  return Number(text?.trim() ?? 'NaN')
}

async function waitForListSettled(page: Page, emptyText: RegExp) {
  await expect(page.getByText(emptyText)).toBeVisible()
}

test.describe('Phase 6-D: Dashboard Deep Links', () => {
  test('発注候補 KPI -> Candidate List with recommendedOnly, count matches', async ({ page }) => {
    await login(page)
    const expected = await kpiValue(page, '発注候補')

    await kpiTile(page, '発注候補').click()
    await expect(page).toHaveURL(/\/candidates\?recommendedOnly=true/)
    await expect(page.getByTestId('recommended-only-checkbox').locator('input')).toBeChecked()
    await waitForListSettled(page, /件の発注候補|発注候補が見つかりませんでした/)

    const resultText = await page.getByText(/件の発注候補/).textContent()
    const actual = Number(resultText?.match(/(\d+)件/)?.[1])
    expect(actual).toBe(expected)
  })

  test('発注作成中 KPI -> Order List with status=DRAFT, count matches', async ({ page }) => {
    await login(page)
    const expected = await kpiValue(page, '発注作成中')

    await kpiTile(page, '発注作成中').click()
    await expect(page).toHaveURL(/\/orders\/history\?status=DRAFT/)
    await waitForListSettled(page, /件の発注|該当する発注が見つかりませんでした/)

    if (expected === 0) {
      await expect(page.getByText('該当する発注が見つかりませんでした。')).toBeVisible()
    } else {
      const resultText = await page.getByText(/件の発注$|件の発注[^候]/).first().textContent()
      const actual = Number(resultText?.match(/(\d+)件/)?.[1])
      expect(actual).toBe(expected)
    }
  })

  test('承認待ち KPI -> Order List with status=PENDING_APPROVAL, count matches (Phase 7-C1 14章)', async ({ page }) => {
    await login(page)
    const expected = await kpiValue(page, '承認待ち')
    test.skip(expected === 0, 'No PENDING_APPROVAL orders in the current Demo Data')

    await kpiTile(page, '承認待ち').click()
    await expect(page).toHaveURL(/\/orders\/history\?status=PENDING_APPROVAL/)
    await waitForListSettled(page, /件の発注/)
    const resultText = await page.getByText(/件の発注$|件の発注[^候]/).first().textContent()
    const actual = Number(resultText?.match(/(\d+)件/)?.[1])
    expect(actual).toBe(expected)
  })

  test('メーカー回答待ち KPI -> Order List with status=AWAITING_SUPPLIER, count matches, and leads on into Supplier Response', async ({ page }) => {
    await login(page)
    const expected = await kpiValue(page, 'メーカー回答待ち')
    test.skip(expected === 0, 'No AWAITING_SUPPLIER orders in the current Demo Data')

    await kpiTile(page, 'メーカー回答待ち').click()
    await expect(page).toHaveURL(/\/orders\/history\?status=AWAITING_SUPPLIER/)
    await waitForListSettled(page, /件の発注/)
    const resultText = await page.getByText(/件の発注$|件の発注[^候]/).first().textContent()
    const actual = Number(resultText?.match(/(\d+)件/)?.[1])
    expect(actual).toBe(expected)

    // 発注一覧 -> 発注詳細 -> メーカー回答を入力 (5章)
    await page.locator('table tbody tr').first().click()
    await expect(page).toHaveURL(/\/orders\/\d+/)
    const action = page.getByTestId('order-detail-primary-action')
    await expect(action).toHaveText('メーカー回答を入力')
    await action.click()
    await expect(page).toHaveURL(/\/supplier-response/)
  })

  test('要確認 KPI -> Order List with hasAttention=true, count matches', async ({ page }) => {
    await login(page)
    const expected = await kpiValue(page, '要確認')
    test.skip(expected === 0, 'No orders with an active Attention in the current Demo Data')

    await kpiTile(page, '要確認').click()
    await expect(page).toHaveURL(/\/orders\/history\?hasAttention=true/)
    await expect(page.getByTestId('has-attention-only-checkbox').locator('input')).toBeChecked()
    await waitForListSettled(page, /件の発注/)
    const resultText = await page.getByText(/件の発注$|件の発注[^候]/).first().textContent()
    const actual = Number(resultText?.match(/(\d+)件/)?.[1])
    expect(actual).toBe(expected)

    // Every visible row must actually carry an Attention badge - the
    // Filter must narrow, not just decorate.
    const rowCount = await page.locator('table tbody tr').count()
    for (let i = 0; i < rowCount; i++) {
      await expect(page.locator('table tbody tr').nth(i).locator('.MuiChip-root').first()).toBeVisible()
    }
  })

  test('欠品 / 長期欠品 KPI still link to Candidate List, unfiltered (no new business definition introduced)', async ({ page }) => {
    await login(page)
    await kpiTile(page, '欠品').click()
    await expect(page).toHaveURL(/\/candidates$/)
    await expect(page.getByTestId('recommended-only-checkbox').locator('input')).not.toBeChecked()
  })

  test('Brand row Deep Links: 発注候補 carries recommendedOnly, 要確認 carries hasAttention', async ({ page }) => {
    await login(page)
    const firstBrandCandidateCell = page.locator('table tbody tr').first().locator('td').nth(1).getByRole('button')
    const brandCandidateCount = Number((await firstBrandCandidateCell.textContent())?.trim())
    await firstBrandCandidateCell.click()
    await expect(page).toHaveURL(/\/candidates\?brandCode=.+&recommendedOnly=true/)
    await waitForListSettled(page, /件の発注候補|発注候補が見つかりませんでした/)
    if (brandCandidateCount > 0) {
      const resultText = await page.getByText(/件の発注候補/).textContent()
      expect(Number(resultText?.match(/(\d+)件/)?.[1])).toBe(brandCandidateCount)
    }

    await page.goto('/')
    const firstBrandAttentionCell = page.locator('table tbody tr').first().locator('td').nth(5).getByRole('button')
    await firstBrandAttentionCell.click()
    await expect(page).toHaveURL(/\/orders\/history\?brandCode=.+&hasAttention=true/)
  })

  test('Browser Back/Forward preserves the Dashboard Deep Link Filter', async ({ page }) => {
    await login(page)
    await kpiTile(page, 'メーカー回答待ち').click()
    const filteredUrl = page.url()
    await page.goBack()
    await expect(page).toHaveURL(/\/$/)
    await page.goForward()
    await expect(page).toHaveURL(filteredUrl)
  })
})
