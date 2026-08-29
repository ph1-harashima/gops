import { test, expect, type Page } from '@playwright/test'

/**
 * Phase 7-G: Dashboard 欠品/長期欠品 Drill-down UX clarity.
 *
 * Root problem this covers: the Candidate List Drill-down (Phase 7-F) made
 * the KPI count and the List's row COUNT agree, but no screen ever showed
 * WHICH SKUs were 欠品/長期欠品 - e.g. HM-MUG-002 showed 商品状態=通常 while
 * being included in a 長期欠品-filtered list, which reads as a contradiction
 * to a user even though 商品状態 and the stock judgement are two independent
 * axes (confirmed via Source: OrderCandidateResponse/SkuDetailResponse carry
 * itemStatus and currentStock/openPo as separate fields; neither predicate
 * reads the other's field).
 *
 * NOT a Business Rule change: shared/domain/stockJudgement.ts's
 * computeStockJudgement() is a byte-for-byte mirror of
 * DashboardService.isOutOfStock/isLongTermOutOfStock (currentStock==0 /
 * +openPo==0, still [PROTOTYPE DECISION] / CUSTOMER REVIEW pending) - no new
 * judgement condition anywhere in this file.
 */

const DEMO_USERNAME = 'purchase01'
const DEMO_PASSWORD = 'DemoPass123!'
const ADMIN_USERNAME = 'admin01'
const ADMIN_PASSWORD = 'DemoPass123!'

async function login(page: Page, username: string, password: string) {
  await page.goto('/')
  await page.getByLabel('ユーザー名').fill(username)
  await page.getByLabel('パスワード').fill(password)
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page.getByTestId('nav-dashboard')).toBeVisible()
}

function stockJudgementChipsInRow(page: Page, sku: string) {
  return page.getByTestId(`candidate-row-${sku}`).getByTestId('stock-judgement-chip')
}

test.describe('Phase 7-G: 在庫判定（暫定）Badge visibility and cross-screen consistency', () => {
  test('A: Candidate List unfiltered shows a 在庫判定 Badge on every row, separate from 商品状態', async ({ page }) => {
    await login(page, DEMO_USERNAME, DEMO_PASSWORD)
    await page.goto('/candidates')
    await expect(page.getByText(/件の発注候補/)).toBeVisible()

    // Column header exists and is distinct from 商品状態's own header.
    const table = page.getByTestId('candidate-list-table-container')
    await expect(table.getByRole('columnheader', { name: '商品状態' })).toBeVisible()
    await expect(table.getByRole('columnheader', { name: /在庫判定/ })).toBeVisible()

    // Every row has exactly one 在庫判定 Chip (never blank - even 通常 rows
    // render a Chip, mirroring ItemStatusChip's own always-render behavior).
    const rowCount = await table.locator('tbody tr').count()
    expect(rowCount).toBeGreaterThan(0)
    for (let i = 0; i < rowCount; i++) {
      await expect(table.locator('tbody tr').nth(i).getByTestId('stock-judgement-chip')).toHaveCount(1)
    }
  })

  test('B: HM-MUG-002 shows 商品状態=通常 and 在庫判定=長期欠品 together, non-contradictorily (Section 7 case)', async ({ page }) => {
    await login(page, DEMO_USERNAME, DEMO_PASSWORD)
    await page.goto('/candidates')
    await expect(page.getByText(/件の発注候補/)).toBeVisible()

    const row = page.getByTestId('candidate-row-HM-MUG-002')
    await expect(row).toBeVisible()
    await expect(row.getByText('通常', { exact: true })).toBeVisible()
    await expect(row.getByTestId('stock-judgement-chip')).toHaveText('長期欠品')
  })

  test('C: 欠品のみ表示 - every visible row is 欠品 or 長期欠品, never 通常 (subset semantics preserved)', async ({ page }) => {
    await login(page, DEMO_USERNAME, DEMO_PASSWORD)
    await page.goto('/candidates?outOfStockOnly=true')
    await expect(page.getByText(/件の発注候補/)).toBeVisible()

    const table = page.getByTestId('candidate-list-table-container')
    const rowCount = await table.locator('tbody tr').count()
    expect(rowCount).toBeGreaterThan(0)
    for (let i = 0; i < rowCount; i++) {
      const label = await table.locator('tbody tr').nth(i).getByTestId('stock-judgement-chip').textContent()
      expect(['欠品', '長期欠品']).toContain(label)
    }
  })

  test('D: 長期欠品のみ表示 - every visible row is 長期欠品 only', async ({ page }) => {
    await login(page, DEMO_USERNAME, DEMO_PASSWORD)
    await page.goto('/candidates?longTermOutOfStockOnly=true')
    await expect(page.getByText(/件の発注候補/)).toBeVisible()

    const table = page.getByTestId('candidate-list-table-container')
    const rowCount = await table.locator('tbody tr').count()
    expect(rowCount).toBeGreaterThan(0)
    for (let i = 0; i < rowCount; i++) {
      await expect(table.locator('tbody tr').nth(i).getByTestId('stock-judgement-chip')).toHaveText('長期欠品')
    }
  })

  test('E: Brand + 欠品 combination keeps the Badge alignment', async ({ page }) => {
    await login(page, DEMO_USERNAME, DEMO_PASSWORD)
    await page.goto('/')
    // FIELDNEST's 欠品 cell (3rd data column) - reuses the same Brand-row
    // Deep Link Dashboard already has (Phase 7-F).
    const cell = page.locator('table tbody tr').filter({ hasText: 'FIELDNEST' }).locator('td').nth(2).getByRole('button')
    const expectedCount = Number((await cell.textContent())?.trim())
    await cell.click()
    await expect(page).toHaveURL(/\/candidates\?brandCode=.+&outOfStockOnly=true/)
    await expect(page.getByText(/件の発注候補/)).toBeVisible()

    const table = page.getByTestId('candidate-list-table-container')
    const rowCount = await table.locator('tbody tr').count()
    expect(rowCount).toBe(expectedCount)
    for (let i = 0; i < rowCount; i++) {
      const label = await table.locator('tbody tr').nth(i).getByTestId('stock-judgement-chip').textContent()
      expect(['欠品', '長期欠品']).toContain(label)
    }
  })

  test('F: SKU Detail 在庫判定 Badge matches the Candidate List Badge for the same SKU (HM-MUG-002)', async ({ page }) => {
    await login(page, DEMO_USERNAME, DEMO_PASSWORD)
    await page.goto('/candidates')
    await expect(page.getByText(/件の発注候補/)).toBeVisible()
    const listBadge = await stockJudgementChipsInRow(page, 'HM-MUG-002').textContent()

    await page.getByTestId('candidate-row-HM-MUG-002').getByRole('button', { name: 'HM-MUG-002' }).click()
    await expect(page).toHaveURL(/\/items\/HM-MUG-002/)

    // 商品状態 Chip in the page header, 在庫判定 Chip in the 在庫情報 Paper -
    // two separate Chips, not one overwriting the other.
    await expect(page.getByRole('heading', { name: /HM-MUG-002/ })).toBeVisible()
    await expect(page.getByText('通常', { exact: true }).first()).toBeVisible()
    await expect(page.getByTestId('stock-judgement-chip')).toHaveText(listBadge ?? '長期欠品')
  })

  test('G: 在庫判定 column header / Chip Tooltip discloses this is a provisional Prototype judgement', async ({ page }) => {
    await login(page, DEMO_USERNAME, DEMO_PASSWORD)
    await page.goto('/candidates')
    await expect(page.getByText(/件の発注候補/)).toBeVisible()

    // Column header itself carries the （暫定）caveat - visible without any
    // hover, satisfying "重すぎる説明なしで暫定であることが分かる".
    const table = page.getByTestId('candidate-list-table-container')
    await expect(table.getByRole('columnheader', { name: /在庫判定.*暫定/ })).toBeVisible()

    // The Chip's own Tooltip additionally spells out the axis-independence
    // and the pending-confirmation caveat on hover.
    const chip = table.locator('tbody tr').first().getByTestId('stock-judgement-chip')
    await chip.hover()
    await expect(page.getByRole('tooltip')).toContainText('Gulliver社確認後に確定')
  })

  test('H: Dashboard KPI Tooltip on 欠品/長期欠品 tiles carries the same provisional-judgement wording', async ({ page }) => {
    await login(page, DEMO_USERNAME, DEMO_PASSWORD)
    await page.goto('/')
    const tile = page.getByTestId('kpi-tile-outOfStockCount')
    await tile.hover()
    await expect(page.getByRole('tooltip')).toContainText('Gulliver社確認後に確定')
  })

  test('I: ADMIN role sees the same 在庫判定 column/Badge as OPERATOR', async ({ page }) => {
    await login(page, ADMIN_USERNAME, ADMIN_PASSWORD)
    await page.goto('/candidates')
    await expect(page.getByText(/件の発注候補/)).toBeVisible()
    await expect(page.getByTestId('candidate-list-table-container').getByRole('columnheader', { name: /在庫判定/ })).toBeVisible()
    await expect(stockJudgementChipsInRow(page, 'HM-MUG-002')).toHaveText('長期欠品')
  })
})
